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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.Block;
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
	private final Set<Transaction> transactions = new HashSet<> ();
	private final Map<String, Set<Transaction>> confirmations = new HashMap<> ();
	private final Map<String, Set<Transaction>> inputs = new HashMap<> ();
	private final LinkedList<String> trunk = new LinkedList<> ();

	private long created;
	private int height = 0;

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

	private void cacheTransaction (Transaction t)
	{
		transactions.add (t);
		for ( TransactionInput i : t.getInputs () )
		{
			Set<Transaction> twithi = inputs.get (i.getSourceHash ());
			if ( twithi == null )
			{
				twithi = new HashSet<Transaction> ();
				inputs.put (i.getSourceHash (), twithi);
			}
			twithi.add (t);
		}
	}

	private void forgetTransaction (Transaction t)
	{
		transactions.remove (t);
		for ( TransactionInput i : t.getInputs () )
		{
			inputs.remove (i.getSourceHash ());
		}
	}

	public synchronized List<Transaction> updateWithTransaction (Transaction t)
	{
		boolean modified = false;
		List<Transaction> notifyList = new ArrayList<> ();
		if ( !t.isExpired () )
		{
			modified = updateWithRegularTransaction (t, notifyList);
		}
		else
		{
			modified = updateWithExpiredTransaction (t, modified);
		}
		if ( modified )
		{
			notifyList.add (t);
		}
		return notifyList;
	}

	private boolean updateWithExpiredTransaction (Transaction t, boolean modified)
	{
		log.trace ("Remove expired " + t.getHash ());
		for ( long ix = 0; ix < t.getOutputs ().size (); ++ix )
		{
			TransactionOutput out;
			out = removeOutput (t.getHash (), ix);
			modified |= out != null;
		}
		forgetTransaction (t);
		return modified;
	}

	private boolean updateWithRegularTransaction (Transaction t, List<Transaction> notifyList)
	{
		boolean spending = processInputs (t, notifyList);
		boolean modified = processOutputs (t, spending);
		if ( modified )
		{
			cacheTransaction (t);
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
				modified = true;
				if ( t.getBlockHash () != null )
				{
					confirmed.add (o);

					Set<Transaction> confirmed;
					if ( (confirmed = confirmations.get (t.getBlockHash ())) == null )
					{
						confirmed = new HashSet<Transaction> ();
						confirmations.put (t.getBlockHash (), confirmed);
					}
					confirmed.add (t);

					log.trace ("Confirmed " + t.getHash () + " [" + o.getIx () + "] (" + o.getOutputAddress () + ") " + o.getValue ());
				}
				else
				{
					if ( spending )
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
				if ( t.getBlockHash () == null && spending )
				{
					modified = true;
					sending.add (o);
					log.trace ("Sending " + t.getHash () + " [" + o.getIx () + "] (" + o.getOutputAddress () + ") " + o.getValue ());
				}
			}
		}
		return modified;
	}

	private boolean processInputs (Transaction t, List<Transaction> notifyList)
	{
		TransactionOutput spend = null;
		for ( TransactionInput input : t.getInputs () )
		{
			checkDoubleSpend (t, notifyList, input);
			spend = confirmed.get (input.getSourceHash (), input.getIx ());
			if ( spend != null )
			{
				confirmed.remove (input.getSourceHash (), input.getIx ());
				log.trace ("Spend settled output " + input.getSourceHash () + " [" + input.getIx () + "] " + spend.getValue ());
			}
			else
			{
				spend = change.get (input.getSourceHash (), input.getIx ());
				if ( spend != null )
				{
					change.remove (input.getSourceHash (), input.getIx ());
					log.trace ("Spend change output " + input.getSourceHash () + " [" + input.getIx () + "] " + spend.getValue ());
				}
				else
				{
					spend = receiving.get (input.getSourceHash (), input.getIx ());
					if ( spend != null )
					{
						receiving.remove (input.getSourceHash (), input.getIx ());
						log.trace ("Spend receiving output " + input.getSourceHash () + " [" + input.getIx () + "] " + spend.getValue ());
					}
				}
			}
		}
		return spend != null;
	}

	private void checkDoubleSpend (Transaction t, List<Transaction> notifyList, TransactionInput input)
	{
		if ( inputs.containsKey (input.getSourceHash ()) )
		{
			List<Transaction> forgetList = new ArrayList<> ();
			for ( Transaction prev : inputs.get (input.getSourceHash ()) )
			{
				for ( TransactionInput pi : prev.getInputs () )
				{
					if ( pi.getSourceHash ().equals (input.getSourceHash ()) && pi.getIx () == input.getIx () )
					{
						log.trace ("Double spend " + t.getHash () + " replaces " + prev.getHash ());
						prev.setHeight (0);
						prev.setBlockHash (null);
						prev.setOffendingTx (t.getHash ());
						notifyList.add (prev);
						for ( TransactionOutput o : prev.getOutputs () )
						{
							removeOutput (prev.getHash (), o.getIx ());
						}
						forgetList.add (prev);
						break;
					}
				}
			}
			for ( Transaction f : forgetList )
			{
				forgetTransaction (f);
			}
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
	public void process (Transaction t)
	{
		for ( Transaction n : updateWithTransaction (t) )
		{
			notifyListener (n);
		}
	}

	@Override
	public void trunkUpdate (List<Block> added)
	{
		Set<Transaction> reorgedTransactions = new HashSet<> ();
		List<Transaction> addedOrReorged = new ArrayList<> ();

		synchronized ( this )
		{
			Block first = added.get (0);

			if ( !trunk.isEmpty () && !trunk.getLast ().equals (first.getPreviousHash ()) && trunk.contains (first.getPreviousHash ()) )
			{
				do
				{
					String removed = trunk.removeLast ();
					if ( confirmations.containsKey (removed) )
					{
						for ( Transaction t : confirmations.get (removed) )
						{
							t.setBlockHash (null);
							t.setBlocktime (new Date ().getTime () / 1000);
							t.setHeight (0);
						}
						reorgedTransactions.addAll (confirmations.remove (removed));
					}
				} while ( !first.getPreviousHash ().equals (trunk.getLast ()) );
			}
			for ( Block b : added )
			{
				trunk.addLast (b.getHash ());
				for ( Transaction t : b.getTransactions () )
				{
					t.setBlockHash (b.getHash ());
					t.setHeight (b.getHeight ());
					t.setBlocktime (b.getCreateTime ());
					List<Transaction> newOrReplaced = updateWithTransaction (t);
					addedOrReorged.addAll (newOrReplaced);
					reorgedTransactions.removeAll (newOrReplaced);
				}
				height = b.getHeight ();
			}
		}
		for ( Transaction n : reorgedTransactions )
		{
			notifyListener (n);
		}
		for ( Transaction n : addedOrReorged )
		{
			notifyListener (n);
		}
	}

	public int getHeight ()
	{
		return height;
	}

	@Override
	public synchronized Set<Transaction> getTransactions ()
	{
		return Collections.unmodifiableSet (transactions);
	}
}
