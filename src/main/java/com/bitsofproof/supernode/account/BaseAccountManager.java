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
import java.util.List;
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

	private final Map<String, Integer> confirmations = new HashMap<> ();

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

	private final Set<AccountListener> accountListener = Collections.synchronizedSet (new HashSet<AccountListener> ());
	private final Map<String, Transaction> transactions = new HashMap<> ();

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

	@Override
	public synchronized void trunkUpdate (List<String> added)
	{
	}

	public synchronized boolean updateWithTransaction (Transaction t)
	{
		boolean modified = false;
		if ( t.getOffendingTx () == null && !t.isExpired () )
		{
			TransactionOutput spend = null;
			for ( TransactionInput i : t.getInputs () )
			{
				spend = confirmed.get (i.getSourceHash (), i.getIx ());
				if ( spend != null )
				{
					confirmed.remove (i.getSourceHash (), i.getIx ());
					log.trace ("Spend settled output " + i.getSourceHash () + " [" + i.getIx () + "] " + spend.getValue ());
				}
				else
				{
					spend = change.get (i.getSourceHash (), i.getIx ());
					if ( spend != null )
					{
						change.remove (i.getSourceHash (), i.getIx ());
						log.trace ("Spend change output " + i.getSourceHash () + " [" + i.getIx () + "] " + spend.getValue ());
					}
					else
					{
						spend = receiving.get (i.getSourceHash (), i.getIx ());
						if ( spend != null )
						{
							receiving.remove (i.getSourceHash (), i.getIx ());
							log.trace ("Spend receiving output " + i.getSourceHash () + " [" + i.getIx () + "] " + spend.getValue ());
						}
					}
				}
			}
			modified = spend != null;
			for ( TransactionOutput o : t.getOutputs () )
			{
				confirmed.remove (o.getTxHash (), o.getIx ());
				change.remove (o.getTxHash (), o.getIx ());
				receiving.remove (o.getTxHash (), o.getIx ());
				sending.remove (o.getTxHash (), o.getIx ());

				if ( isOwnAddress (o.getOutputAddress ()) )
				{
					modified = true;
					if ( t.getBlockHash () != null )
					{
						confirmed.add (o);
						log.trace ("Settled " + t.getHash () + " [" + o.getIx () + "] (" + o.getOutputAddress () + ") " + o.getValue ());
					}
					else
					{
						if ( spend != null )
						{
							change.add (o);
							log.trace ("Change " + t.getHash () + " [" + o.getIx () + "] (" + o.getOutputAddress () + ") "
									+ o.getValue ());
						}
						else
						{
							receiving.add (o);
							log.trace ("Receiving " + t.getHash () + " [" + o.getIx () + "] (" + o.getOutputAddress () + ") "
									+ o.getValue ());
						}
					}
				}
				else
				{
					if ( t.getBlockHash () == null && spend != null )
					{
						modified = true;
						sending.add (o);
						log.trace ("Sending " + t.getHash () + " [" + o.getIx () + "] (" + o.getOutputAddress () + ") " + o.getValue ());
					}
				}
			}
			if ( modified )
			{
				transactions.put (t.getHash (), t);
			}
		}
		else
		{
			if ( t.isExpired () )
			{
				log.trace ("Remove expired " + t.getHash ());
			}
			else
			{
				log.trace ("Remove " + t.getHash () + " because of " + t.getOffendingTx ());
			}
			for ( long ix = 0; ix < t.getOutputs ().size (); ++ix )
			{
				TransactionOutput out;
				out = confirmed.remove (t.getHash (), ix);
				if ( out == null )
				{
					out = change.remove (t.getHash (), ix);
				}
				if ( out == null )
				{
					out = receiving.remove (t.getHash (), ix);
				}
				if ( out == null )
				{
					out = sending.remove (t.getHash (), ix);
				}
				if ( out != null )
				{
					log.trace ("Remove " + out.getTxHash () + " [" + out.getIx () + "] (" + out.getOutputAddress () + ")"
							+ out.getValue ());
				}
				modified |= out != null;
			}
			transactions.remove (t.getHash ());
		}
		return modified;
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
	public void process (Transaction t)
	{
		if ( updateWithTransaction (t) )
		{
			notifyListener (t);
		}
	}

	@Override
	public synchronized List<Transaction> getTransactions ()
	{
		List<Transaction> tl = new ArrayList<> ();
		for ( Transaction t : transactions.values () )
		{
			boolean inserted = false;
			int ix = 0;
			for ( Transaction other : tl )
			{
				for ( TransactionInput in : other.getInputs () )
				{
					if ( in.getSourceHash ().equals (t.getHash ()) )
					{
						tl.add (ix, t);
						inserted = true;
						break;
					}
				}
				++ix;
				if ( inserted )
				{
					break;
				}
			}
			if ( !inserted )
			{
				tl.add (t);
			}
		}
		return tl;
	}
}
