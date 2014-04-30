package com.bitsofproof.supernode.account;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	private Set<String> reserved = Collections.synchronizedSet (new HashSet<String> ());

	@Override
	public abstract Address getNextChangeAddress () throws ValidationException;

	@Override
	public abstract Address getNextReceiverAddress () throws ValidationException;

	public static long estimateFee (Transaction t)
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		t.toWire (writer);
		return Math.max (Math.min (MAXIMUM_FEE, (writer.toByteArray ().length + 1000) / 1000 * KB_FEE), MINIMUM_FEE);
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

	@Override
	public Transaction pay (List<Address> receiver, List<Long> amounts, long fee, boolean senderPaysFee) throws ValidationException
	{
		long amount = 0;
		for ( Long a : amounts )
		{
			amount += a;
		}
		log.trace ("pay " + amount + (senderPaysFee ? " + " + fee : ""));
		List<TransactionSource> sources = getSufficientSources (amount, senderPaysFee ? fee : 0);
		if ( sources == null )
		{
			throw new ValidationException ("Insufficient funds to pay " + amount + (senderPaysFee ? " + " + fee : ""));
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
		if ( !senderPaysFee )
		{
			TransactionSink last = sinks.get (sinks.size () - 1);
			sinks.set (sinks.size () - 1, new TransactionSink (last.getScript (), Math.max (last.getValue () - fee, 0)));
		}
		if ( ((in - amount) - (senderPaysFee ? fee : 0)) > DUST_LIMIT )
		{
			Address changeAddress = getNextChangeAddress ();
			TransactionSink change = new TransactionSink (changeAddress.getAddressScript (), in - amount - (senderPaysFee ? fee : 0));
			log.trace ("change to " + changeAddress + " " + change.getValue ());
			sinks.add (change);
		}
		else
		{
			fee = in - amount;
		}
		Collections.shuffle (sinks);
		return createTransaction (sources, sinks, fee);
	}

	@Override
	public Transaction pay (Address receiver, long amount, long fee, boolean senderPaysFee) throws ValidationException
	{
		List<Address> a = new ArrayList<> ();
		a.add (receiver);
		List<Long> v = new ArrayList<> ();
		v.add (amount);
		return pay (a, v, fee, senderPaysFee);
	}

	@Override
	public Transaction pay (Address receiver, long amount, boolean senderPaysFee) throws ValidationException
	{
		List<Address> a = new ArrayList<> ();
		a.add (receiver);
		List<Long> v = new ArrayList<> ();
		v.add (amount);
		return pay (a, v, senderPaysFee);
	}

	@Override
	public Transaction pay (List<Address> receiver, List<Long> amounts, boolean senderPaysFee) throws ValidationException
	{
		long fee = MINIMUM_FEE;
		long estimate = 0;
		Transaction t;

		do
		{
			fee = Math.max (fee, estimate);
			t = pay (receiver, amounts, fee, senderPaysFee);
			estimate = estimateFee (t);
			if ( fee < estimate )
			{
				log.trace ("The transaction requires more network fees. Reassembling.");
			}
		} while ( fee < estimate );

		return t;
	}

}
