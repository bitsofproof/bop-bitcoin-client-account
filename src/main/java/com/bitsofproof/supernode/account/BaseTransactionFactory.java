package com.bitsofproof.supernode.account;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.account.PaymentOptions.Priority;
import com.bitsofproof.supernode.api.Address;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionInput;
import com.bitsofproof.supernode.api.TransactionOutput;
import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.common.WireFormat;

public abstract class BaseTransactionFactory extends BaseAccountManager implements TransactionFactory
{
	private static final Logger log = LoggerFactory.getLogger (BaseTransactionFactory.class);

	private static final long DUST_LIMIT = 5430;
	private static final long KB_FEE = 1000;
	private static final long MINIMUM_FEE = 10000;
	private static final long MAXIMUM_FEE = 1000000;

	private static final SecureRandom random = new SecureRandom ();

	private Set<String> reserved = Collections.synchronizedSet (new HashSet<String> ());

	@Override
	public abstract Address getNextChangeAddress () throws ValidationException;

	@Override
	public abstract Address getNextReceiverAddress () throws ValidationException;

	public static long estimateFee (Transaction t, Priority priority)
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		t.toWire (writer);
		int tsd = (writer.toByteArray ().length + 1000) / 1000;
		return Math.min (MAXIMUM_FEE, priority == Priority.LOW ? 0 : Math.max (tsd * (priority == Priority.NORMAL ? KB_FEE : MINIMUM_FEE), MINIMUM_FEE));
	}

	public static long estimateFee (Transaction t)
	{
		return estimateFee (t, Priority.NORMAL);
	}

	protected Transaction createTransaction (List<TransactionSource> sources, List<TransactionSink> sinks, long fee) throws ValidationException
	{
		if ( fee < 0 || fee > MAXIMUM_FEE )
		{
			throw new ValidationException ("You unlikely want to do that");
		}
		Transaction transaction = new Transaction ();
		transaction.setInputs (new ArrayList<TransactionInput> ());
		transaction.setOutputs (new ArrayList<TransactionOutput> ());

		long sumOut = 0;
		for ( TransactionSink s : sinks )
		{
			TransactionOutput o = new TransactionOutput ();
			o.setValue (s.getValue ());
			sumOut += s.getValue ();
			o.setScript (s.getScript ());

			transaction.getOutputs ().add (o);
		}

		long sumInput = 0;
		for ( TransactionSource o : sources )
		{
			TransactionInput i = new TransactionInput ();
			i.setSourceHash (o.getSource ().getTxHash ());
			i.setIx (o.getSource ().getIx ());
			sumInput += o.getSource ().getValue ();

			transaction.getInputs ().add (i);
		}
		if ( sumInput != (sumOut + fee) )
		{
			throw new ValidationException ("Sum of sinks (+fee) does not match sum of sources");
		}

		int j = 0;
		for ( TransactionSource s : sources )
		{
			TransactionInput i = transaction.getInputs ().get (j);
			i.setScript (s.spend (j, transaction));
			++j;
		}

		transaction.computeHash ();
		return transaction;
	}

	protected TransactionSource createTransactionSource (TransactionOutput output)
	{
		return new TransactionSource (output, this);
	}

	protected List<TransactionSource> getSufficientSources (long amount, long fee)
	{
		List<TransactionSource> candidates = new ArrayList<> ();
		for ( TransactionOutput o : getConfirmedOutputs () )
		{
			if ( !isReserved (o.getTxHash (), o.getIx ()) )
			{
				candidates.add (createTransactionSource (o));
			}
		}
		// prefer confirmed
		Collections.sort (candidates, new Comparator<TransactionSource> ()
		{
			// prefer aggregation of UTXO
			@Override
			public int compare (TransactionSource o1, TransactionSource o2)
			{
				return Long.compare (o1.getSource ().getValue (), o2.getSource ().getValue ());
			}
		});
		List<TransactionSource> changelist = new ArrayList<> ();
		for ( TransactionOutput o : getChangeOutputs () )
		{
			if ( !isReserved (o.getTxHash (), o.getIx ()) )
			{
				candidates.add (createTransactionSource (o));
			}
		}
		// ... then change
		Collections.sort (changelist, new Comparator<TransactionSource> ()
		{
			// prefer aggregation of UTXO
			@Override
			public int compare (TransactionSource o1, TransactionSource o2)
			{
				return Long.compare (o1.getSource ().getValue (), o2.getSource ().getValue ());
			}
		});
		candidates.addAll (changelist);

		List<TransactionSource> result = new ArrayList<> ();
		long sum = 0;
		for ( TransactionSource o : candidates )
		{
			sum += o.getSource ().getValue ();
			result.add (o);
			if ( sum >= (amount + fee) )
			{
				return result;
			}
		}
		return null;
	}

	@Override
	public void reserveInputs (Transaction t)
	{
		for ( TransactionInput in : t.getInputs () )
		{
			reserved.add (in.getSourceHash () + ":" + in.getIx ());
		}
	}

	@Override
	public boolean isReserved (String hash, long ix)
	{
		return reserved.contains (hash + ":" + ix);
	}

	@Override
	public void releaseInputs (Transaction t)
	{
		for ( TransactionInput in : t.getInputs () )
		{
			reserved.remove (in.getSourceHash () + ":" + in.getIx ());
		}
	}

	private Transaction payFixed (List<Address> receiver, List<Long> amounts, PaymentOptions options) throws ValidationException
	{
		long amount = 0;
		for ( Long a : amounts )
		{
			amount += a;
		}
		log.trace ("pay " + amount + (options.isPaidBySender () ? " + " + options : ""));
		List<TransactionSource> sources = getSufficientSources (amount, options.isPaidBySender () ? options.getFee () : 0);
		if ( sources == null )
		{
			throw new ValidationException ("Insufficient funds to pay " + amount + " " + options);
		}
		long in = 0;
		for ( TransactionSource o : sources )
		{
			log.trace ("using input " + o.getSource ().getTxHash () + "[" + o.getSource ().getIx () + "] " + o.getSource ().getValue ());
			in += o.getSource ().getValue ();
		}
		List<TransactionSink> sinks = new ArrayList<> ();
		Iterator<Long> ai = amounts.iterator ();
		for ( Address r : receiver )
		{
			sinks.add (new TransactionSink (r.getAddressScript (), ai.next ()));
		}
		long txfee = options.getFee ();
		if ( !options.isPaidBySender () )
		{
			long feeCollected = 0;
			while ( !sinks.isEmpty () && feeCollected < txfee )
			{
				TransactionSink last = sinks.get (sinks.size () - 1);
				long feeAvaialable = Math.min (last.getValue (), options.getFee () - feeCollected);
				if ( feeAvaialable == last.getValue () )
				{
					sinks.remove (sinks.size () - 1);
				}
				else
				{
					sinks.set (sinks.size () - 1, new TransactionSink (last.getScript (), last.getValue () - feeAvaialable));
				}
				feeCollected += feeAvaialable;
			}
			if ( feeCollected < txfee )
			{
				throw new ValidationException ("Can not cover fees by reducing outputs");
			}
			if ( sinks.isEmpty () )
			{
				throw new ValidationException ("No output left after paying fees");
			}
		}
		if ( ((in - amount) - (options.isPaidBySender () ? options.getFee () : 0)) > DUST_LIMIT )
		{
			for ( long change : splitChange (in - amount - (options.isPaidBySender () ? options.getFee () : 0), Math.max (1, options.getChange ())) )
			{
				Address changeAddress = getNextChangeAddress ();
				TransactionSink changeOutput = new TransactionSink (changeAddress.getAddressScript (), change);
				log.trace ("change to " + changeAddress + " " + changeOutput.getValue ());
				sinks.add (changeOutput);
			}
		}
		else
		{
			if ( options.isPaidBySender () )
			{
				txfee = in - amount;
			}
		}
		if ( options.isShuffled () )
		{
			Collections.shuffle (sinks);
		}
		return createTransaction (sources, sinks, txfee);
	}

	private long[] splitChange (long change, int n)
	{
		if ( n == 1 || change <= (n * MINIMUM_FEE) )
		{
			return new long[] { change };
		}
		else
		{
			long[] changes = new long[n];
			boolean dust = false;

			do
			{
				double[] proportions = new double[n];
				double s = 0;
				for ( int i = 0; i < n; ++i )
				{
					s += proportions[i] = Math.exp (1 - random.nextDouble ());
				}
				long cs = 0;
				for ( int i = 0; i < n; ++i )
				{
					cs += changes[i] = ((long) Math.floor (proportions[i] / s * change / MINIMUM_FEE)) * MINIMUM_FEE;
				}
				changes[0] += change - cs;
				for ( long c : changes )
				{
					if ( c <= DUST_LIMIT )
					{
						dust = true;
					}
				}
			} while ( dust );
			return changes;
		}
	}

	@Override
	public Transaction pay (Address receiver, long amount) throws ValidationException
	{
		List<Address> a = new ArrayList<> ();
		a.add (receiver);
		List<Long> v = new ArrayList<> ();
		v.add (amount);
		return pay (a, v, PaymentOptions.common);
	}

	@Override
	public Transaction pay (Address receiver, long amount, PaymentOptions options) throws ValidationException
	{
		List<Address> a = new ArrayList<> ();
		a.add (receiver);
		List<Long> v = new ArrayList<> ();
		v.add (amount);
		return pay (a, v, options);
	}

	@Override
	public Transaction pay (List<Address> receiver, List<Long> amounts, PaymentOptions options) throws ValidationException
	{
		Transaction t;

		if ( options.isCalculated () )
		{
			long estimate = 0;
			long txfee = options.isLowPriority () ? 0 : MINIMUM_FEE;
			do
			{
				txfee = Math.max (txfee, estimate);
				t = payFixed (receiver, amounts,
						new PaymentOptions (txfee, PaymentOptions.FeeCalculation.FIXED, options.getSource (), options.getPriority (),
								options.getOutputOrder (), options.getChange ()));
				estimate = estimateFee (t, options.getPriority ());
				if ( txfee < estimate )
				{
					log.trace ("The transaction requires more network fees. Reassembling.");
				}
			} while ( options.getCalculation () == PaymentOptions.FeeCalculation.CALCULATED && txfee < estimate );
		}
		else
		{
			t = payFixed (receiver, amounts, options);
		}
		return t;
	}

	@Override
	public Transaction pay (List<Address> receiver, List<Long> amounts) throws ValidationException
	{
		return pay (receiver, amounts, PaymentOptions.common);
	}
}
