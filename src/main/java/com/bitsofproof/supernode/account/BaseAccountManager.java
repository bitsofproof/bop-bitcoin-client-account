/*
 * Copyright 2013 bits of proof zrt.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bitsofproof.supernode.account;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionInput;
import com.bitsofproof.supernode.api.TransactionOutput;

public abstract class BaseAccountManager implements AccountManager
{
	private static final Logger log = LoggerFactory.getLogger (BaseAccountManager.class);

	private UTXO confirmed = createConfirmedUTXO ();
	private UTXO change = createChangeUTXO ();
	private UTXO receiving = createReceivingUTXO ();
	private UTXO sending = createSendingUTXO ();

	private final Set<AccountListener> accountListener = Collections.synchronizedSet (new HashSet<AccountListener> ());
	private final Map<String, Transaction> transactions = new HashMap<> ();

	private long created;

	@Override
	public long getCreated ()
	{
		return created;
	}

	public void setCreated (long created)
	{
		this.created = created;
	}

	protected UTXO createConfirmedUTXO ()
	{
		return new InMemoryUTXO ();
	}

	protected UTXO createChangeUTXO ()
	{
		return new InMemoryUTXO ();
	}

	protected UTXO createSendingUTXO ()
	{
		return new InMemoryUTXO ();
	}

	protected UTXO createReceivingUTXO ()
	{
		return new InMemoryUTXO ();
	}

	protected synchronized void reset ()
	{
		confirmed = createConfirmedUTXO ();
		change = createChangeUTXO ();
		receiving = createReceivingUTXO ();
		sending = createSendingUTXO ();
	}

	public synchronized boolean updateWithTransaction (Transaction t)
	{
		boolean modified = false;
		if ( t.getOffendingTx () != null )
		{
			modified = updateWithDoubleSpent (t);
		}
		else if ( t.isExpired () )
		{
			modified = updateWithExpiredTransaction (t);
		}
		else
		{
			modified = updateWithRegularTransaction (t);
		}
		return modified;
	}

	private boolean updateWithDoubleSpent (Transaction t)
	{
		removeOutput (t);
		return transactions.remove (t.getHash ()) != null;
	}

	private boolean updateWithExpiredTransaction (Transaction t)
	{
		log.trace ("Remove expired " + t.getHash ());
		removeOutput (t);
		return transactions.remove (t.getHash ()) != null;
	}

	private boolean updateWithRegularTransaction (Transaction t)
	{
		boolean spending = processInputs (t);
		boolean modified = processOutputs (t, spending);
		if ( modified )
		{
			transactions.put (t.getHash (), t);
		}
		return modified;
	}

	private boolean processOutputs (Transaction t, boolean spending)
	{
		boolean modified;
		modified = spending;
		for ( TransactionOutput o : t.getOutputs () )
		{
			removeOutput (o.getTxHash (), o.getIx ());

			if ( isOwnAddress (o.getOutputAddress ()) )
			{
				if ( t.getBlockHash () != null )
				{
					modified = confirmed.add (o);
					log.trace ("Confirmed " + t.getHash () + " [" + o.getIx () + "] (" + o.getOutputAddress () + ") " + o.getValue ());
				}
				else
				{
					if ( spending )
					{
						modified = change.add (o);
						log.trace ("Change " + t.getHash () + " [" + o.getIx () + "] (" + o.getOutputAddress () + ") "
								+ o.getValue ());
					}
					else
					{
						modified = receiving.add (o);
						log.trace ("Receiving " + t.getHash () + " [" + o.getIx () + "] (" + o.getOutputAddress () + ") "
								+ o.getValue ());
					}
				}
			}
			else
			{
				if ( t.getBlockHash () == null && spending )
				{
					modified = sending.add (o);
					log.trace ("Sending " + t.getHash () + " [" + o.getIx () + "] (" + o.getOutputAddress () + ") " + o.getValue ());
				}
			}
		}
		return modified;
	}

	private boolean processInputs (Transaction t)
	{
		boolean spends = false;
		for ( TransactionInput input : t.getInputs () )
		{
			TransactionOutput spend;
			spend = confirmed.remove (input.getSourceHash (), input.getIx ());
			if ( spend != null )
			{
				log.trace ("Spend settled output " + input.getSourceHash () + " [" + input.getIx () + "] " + spend.getValue ());
			}
			else
			{
				spend = change.remove (input.getSourceHash (), input.getIx ());
				if ( spend != null )
				{
					log.trace ("Spend change output " + input.getSourceHash () + " [" + input.getIx () + "] " + spend.getValue ());
				}
				else
				{
					spend = receiving.remove (input.getSourceHash (), input.getIx ());
					if ( spend != null )
					{
						log.trace ("Spend receiving output " + input.getSourceHash () + " [" + input.getIx () + "] " + spend.getValue ());
					}
				}
			}
			spends |= spend != null;
		}
		return spends;
	}

	private void removeOutput (Transaction t)
	{
		for ( TransactionOutput o : t.getOutputs () )
		{
			removeOutput (o.getTxHash (), o.getIx ());
		}
	}

	private TransactionOutput removeOutput (String hash, long ix)
	{
		TransactionOutput out;
		out = confirmed.remove (hash, ix);
		if ( out == null )
		{
			out = change.remove (hash, ix);
		}
		if ( out == null )
		{
			out = receiving.remove (hash, ix);
		}
		if ( out == null )
		{
			out = sending.remove (hash, ix);
		}
		if ( out != null )
		{
			log.trace ("Remove " + out.getTxHash () + " [" + out.getIx () + "] (" + out.getOutputAddress () + ") " + out.getValue ());
		}
		return out;
	}

	@Override
	public synchronized long getBalance ()
	{
		return confirmed.getTotal () + change.getTotal () + receiving.getTotal ();
	}

	@Override
	public synchronized long getConfirmed ()
	{
		return confirmed.getTotal ();
	}

	@Override
	public synchronized long getSending ()
	{
		return sending.getTotal ();
	}

	@Override
	public synchronized long getReceiving ()
	{
		return receiving.getTotal ();
	}

	@Override
	public synchronized long getChange ()
	{
		return change.getTotal ();
	}

	@Override
	public synchronized Collection<TransactionOutput> getConfirmedOutputs ()
	{
		return confirmed.getUTXO ();
	}

	@Override
	public synchronized Collection<TransactionOutput> getSendingOutputs ()
	{
		return sending.getUTXO ();
	}

	@Override
	public synchronized Collection<TransactionOutput> getReceivingOutputs ()
	{
		return receiving.getUTXO ();
	}

	@Override
	public synchronized Collection<TransactionOutput> getChangeOutputs ()
	{
		return change.getUTXO ();
	}

	@Override
	public void addAccountListener (AccountListener listener)
	{
		accountListener.add (listener);
	}

	@Override
	public void removeAccountListener (AccountListener listener)
	{
		accountListener.remove (listener);
	}

	protected void notifyListener (Transaction t)
	{
		ArrayList<AccountListener> al = new ArrayList<> ();
		synchronized ( accountListener )
		{
			al.addAll (accountListener);
		}
		for ( AccountListener l : al )
		{
			try
			{
				l.accountChanged (this, t);
			}
			catch ( Exception e )
			{
				log.error ("Uncaught exception in account listener", e);
			}
		}
	}

	@Override
	public boolean process (Transaction t)
	{
		if ( updateWithTransaction (t) )
		{
			notifyListener (t);
			return true;
		}
		return false;
	}

	@Override
	public void rejected (String command, String hash, String reason, int rejectionCode)
	{
		Transaction revert = null;
		synchronized ( this )
		{
			if ( command.equals ("tx") && transactions.containsKey (hash) )
			{
				revert = transactions.get (hash);

				removeOutput (revert);

				for ( TransactionInput input : revert.getInputs () )
				{
					Transaction prev = transactions.get (input.getSourceHash ());
					if ( prev.getBlockHash () != null )
					{
						confirmed.add (prev.getOutputs ().get ((int) input.getIx ()));
					}
					else
					{
						boolean spend = false;
						for ( TransactionInput pin : prev.getInputs () )
						{
							if ( transactions.containsKey (pin.getSourceHash ()) )
							{
								spend = true;
								break;
							}
						}
						if ( spend )
						{
							change.add (prev.getOutputs ().get ((int) input.getIx ()));
						}
						else
						{
							receiving.add (prev.getOutputs ().get ((int) input.getIx ()));
						}
					}
				}
				transactions.remove (hash);
			}
		}
		if ( revert != null )
		{
			notifyListener (revert);
		}
	}

	@Override
	public synchronized boolean isKnownTransaction (Transaction t)
	{
		return transactions.containsKey (t.getHash ());
	}

	@Override
	public synchronized Set<Transaction> getTransactions ()
	{
		Set<Transaction> ts = new HashSet<Transaction> ();
		ts.addAll (transactions.values ());
		return ts;
	}
}
