package com.bitsofproof.supernode.account;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionInput;
import com.bitsofproof.supernode.api.TrunkListener;
import com.bitsofproof.supernode.common.Hash;

public class ConfirmationManager implements TrunkListener
{
	private static final Logger log = LoggerFactory.getLogger (ConfirmationManager.class);

	private final Set<AccountManager> accounts = Collections.synchronizedSet (new HashSet<AccountManager> ());
	private final LinkedList<String> trunk = new LinkedList<> ();

	private final Map<String, Set<Transaction>> inputs = new HashMap<> ();
	private final Map<String, Set<Transaction>> confirmations = new HashMap<> ();
	private int height;

	private final Set<ConfirmationListener> confirmationListener = Collections.synchronizedSet (new HashSet<ConfirmationListener> ());

	public synchronized void addAccount (AccountManager account)
	{
		accounts.add (account);
	}

	public synchronized void removeAccount (AccountManager account)
	{
		accounts.remove (account);
	}

	public synchronized void init (BCSAPI api, int trunkLength, List<String> inventory) throws BCSAPIException
	{
		trunk.clear ();
		if ( inventory != null )
		{
			Collections.copy (trunk, inventory);
		}
		api.catchUp (trunk, trunkLength, true, this);
		Block highest = api.getBlockHeader (trunk.getFirst ());
		height = highest.getHeight ();
	}

	public synchronized void init (BCSAPI api, int trunkLength) throws BCSAPIException
	{
		init (api, trunkLength, null);
	}

	public synchronized int getHeight ()
	{
		return height;
	}

	@Override
	public synchronized void trunkUpdate (List<Block> added)
	{
		Set<Transaction> reorgedTransactions = new HashSet<> ();
		Block first = added.get (0);

		if ( !trunk.isEmpty () && !trunk.getFirst ().equals (first.getPreviousHash ()) )
		{
			log.trace ("Chain reorg through " + first.getHash ());
			if ( trunk.contains (first.getPreviousHash ()) )
			{
				do
				{
					String removed = trunk.removeFirst ();
					log.trace ("Removing block " + removed);
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
				} while ( !first.getPreviousHash ().equals (trunk.getFirst ()) );
			}
			else
			{
				log.trace ("Removing all blocks");
				trunk.clear ();
				Iterator<String> ri = confirmations.keySet ().iterator ();
				while ( ri.hasNext () )
				{
					String removed = ri.next ();
					for ( Transaction t : confirmations.get (removed) )
					{
						t.setBlockHash (null);
						t.setBlocktime (new Date ().getTime () / 1000);
						t.setHeight (0);
						reorgedTransactions.add (t);
					}
					ri.remove ();
				}
			}
		}
		for ( Block b : added )
		{
			trunk.addFirst (b.getHash ());
			log.trace ("New highest block " + trunk.getFirst ());
			if ( b.getTransactions () != null )
			{
				for ( Transaction t : b.getTransactions () )
				{
					t.setBlockHash (b.getHash ());
					t.setHeight (b.getHeight ());
					t.setBlocktime (b.getCreateTime ());
					reorgedTransactions.remove (t);
					checkDoubleSpend (t);
					boolean cache = false;
					for ( AccountManager account : accounts )
					{
						if ( account.process (t) || account.isKnownTransaction (t) )
						{
							log.trace ("confirmation for " + t.getHash ());
							cache = true;
						}
					}
					if ( cache )
					{
						cacheTransaction (t);
						notifyListener (t);
					}
				}
			}
			height = b.getHeight ();
		}

		for ( Transaction n : reorgedTransactions )
		{
			log.trace ("un-confirmed " + n.getHash ());
			notifyListener (n);
		}
		notifyListener (null);
	}

	private void checkDoubleSpend (Transaction t)
	{
		Set<Transaction> doubleSpent = new HashSet<> ();
		for ( TransactionInput input : t.getInputs () )
		{
			if ( inputs.containsKey (input.getSourceHash ()) )
			{
				for ( Transaction prev : inputs.get (input.getSourceHash ()) )
				{
					if ( !prev.equals (t) )
					{
						for ( TransactionInput pi : prev.getInputs () )
						{
							if ( pi.getSourceHash ().equals (input.getSourceHash ()) && pi.getIx () == input.getIx () )
							{
								prev.setHeight (0);
								prev.setBlockHash (null);
								prev.setOffendingTx (t.getHash ());
								doubleSpent.add (prev);
								break;
							}
						}
					}
				}
			}
		}
		for ( Transaction f : doubleSpent )
		{
			log.trace ("Double spend " + t.getHash () + " replaces " + f.getHash ());
			for ( AccountManager account : accounts )
			{
				account.process (f);
			}
			forgetTransaction (f);
			notifyListener (f);
		}
	}

	private void cacheTransaction (Transaction t)
	{
		Set<Transaction> ts = confirmations.get (t.getBlockHash ());
		if ( ts == null )
		{
			confirmations.put (t.getBlockHash (), ts = new HashSet<Transaction> ());
		}
		ts.add (t);
		for ( TransactionInput i : t.getInputs () )
		{
			if ( !i.getSourceHash ().equals (Hash.ZERO_HASH_STRING) )
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
	}

	private void forgetTransaction (Transaction t)
	{
		for ( TransactionInput i : t.getInputs () )
		{
			inputs.remove (i.getSourceHash ());
		}
	}

	public void addConfirmationListener (ConfirmationListener listener)
	{
		confirmationListener.add (listener);
	}

	public void removeConfirmationListener (ConfirmationListener listener)
	{
		confirmationListener.remove (listener);
	}

	private void notifyListener (Transaction t)
	{
		ArrayList<ConfirmationListener> al = new ArrayList<> ();
		synchronized ( confirmationListener )
		{
			al.addAll (confirmationListener);
		}
		for ( ConfirmationListener l : al )
		{
			try
			{
				if ( t != null )
				{
					l.confirmed (t);
				}
				else
				{
					l.newHeight (height);
				}
			}
			catch ( Exception e )
			{
				log.error ("Uncaught exception in account listener", e);
			}
		}
	}
}
