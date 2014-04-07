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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.bitsofproof.supernode.api.Address;
import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionOutput;
import com.bitsofproof.supernode.common.ExtendedKey;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ValidationException;

public class ReceiverChangeAccountManager extends BaseTransactionFactory
{
	private final ExtendedKeyAccountManager receiver = new ExtendedKeyAccountManager ();
	private final ExtendedKeyAccountManager change = new ExtendedKeyAccountManager ();
	private ExtendedKey master;

	public ExtendedKey getMaster ()
	{
		return master;
	}

	public void setMaster (ExtendedKey master) throws ValidationException
	{
		this.master = master;

		AccountListener listener = new AccountListener ()
		{
			@Override
			public void accountChanged (AccountManager account, Transaction t)
			{
				notifyListener (t);
			}
		};

		receiver.setFirstIndex (getFirstIndex ());
		receiver.setLookAhead (getLookAhead ());
		receiver.setCreated (getCreated ());
		receiver.setMaster (master.getChild (0));
		receiver.addAccountListener (listener);

		change.setFirstIndex (getFirstIndex ());
		change.setLookAhead (getLookAhead ());
		change.setCreated (getCreated ());
		change.setMaster (master.getChild (1));
		change.addAccountListener (listener);
	}

	public int getFirstIndex ()
	{
		return receiver.getFirstIndex ();
	}

	public void setLookAhead (int lookAhead)
	{
		receiver.setLookAhead (lookAhead);
		change.setLookAhead (lookAhead);
	}

	public void setFirstIndex (int firstIndex)
	{
		receiver.setFirstIndex (firstIndex);
		change.setFirstIndex (firstIndex);
	}

	public int getLookAhead ()
	{
		return receiver.getLookAhead ();
	}

	public boolean isReceiverAddress (Address address)
	{
		Key key = receiver.getKeyForAddress (address);
		if ( key != null )
		{
			return true;
		}
		return false;
	}

	public boolean isChangeAddress (Address address)
	{
		Key key = change.getKeyForAddress (address);
		if ( key != null )
		{
			return true;
		}
		return false;
	}

	public int[] getKeyPathForAddresss (Address address)
	{
		Integer rk = receiver.getKeyIDForAddress (address);
		if ( rk != null )
		{
			return new int[] { 0, rk };
		}
		Integer ck = change.getKeyIDForAddress (address);
		if ( ck != null )
		{
			return new int[] { 1, ck };
		}
		return null;
	}

	@Override
	public Key getKeyForAddress (Address address)
	{
		Key key = receiver.getKeyForAddress (address);
		if ( key == null )
		{
			return change.getKeyForAddress (address);
		}
		return key;
	}

	@Override
	public Set<Address> getAddresses ()
	{
		Set<Address> all = new HashSet<> ();
		all.addAll (receiver.getAddresses ());
		all.addAll (change.getAddresses ());
		return Collections.unmodifiableSet (all);
	}

	@Override
	public void process (Transaction t)
	{
		change.process (t);
		receiver.process (t);
	}

	@Override
	public synchronized Collection<TransactionOutput> getConfirmedOutputs ()
	{
		Set<TransactionOutput> outs = new HashSet<TransactionOutput> ();
		outs.addAll (receiver.getConfirmedOutputs ());
		outs.addAll (change.getConfirmedOutputs ());
		return outs;
	}

	@Override
	public synchronized Collection<TransactionOutput> getSendingOutputs ()
	{
		Set<TransactionOutput> outs = new HashSet<TransactionOutput> ();
		outs.addAll (receiver.getSendingOutputs ());
		outs.addAll (change.getSendingOutputs ());
		return outs;
	}

	@Override
	public synchronized Collection<TransactionOutput> getReceivingOutputs ()
	{
		Set<TransactionOutput> outs = new HashSet<TransactionOutput> ();
		outs.addAll (receiver.getReceivingOutputs ());
		outs.addAll (change.getReceivingOutputs ());
		return outs;
	}

	@Override
	public synchronized Collection<TransactionOutput> getChangeOutputs ()
	{
		Set<TransactionOutput> outs = new HashSet<TransactionOutput> ();
		outs.addAll (receiver.getChangeOutputs ());
		outs.addAll (change.getChangeOutputs ());
		return outs;
	}

	@Override
	public synchronized long getBalance ()
	{
		return receiver.getBalance () + change.getBalance ();
	}

	@Override
	public synchronized long getConfirmed ()
	{
		return receiver.getConfirmed () + change.getConfirmed ();
	}

	@Override
	public synchronized long getSending ()
	{
		return receiver.getSending () + change.getSending ();
	}

	@Override
	public synchronized long getReceiving ()
	{
		return receiver.getReceiving () + change.getReceiving ();
	}

	@Override
	public synchronized long getChange ()
	{
		return receiver.getChange () + change.getChange ();
	}

	@Override
	public synchronized List<Transaction> getTransactions ()
	{
		List<Transaction> txs = new ArrayList<> ();
		txs.addAll (receiver.getTransactions ());
		txs.addAll (change.getTransactions ());

		return txs;
	}

	@Override
	public void syncHistory (BCSAPI api) throws BCSAPIException
	{
		receiver.syncHistory (api);
		change.syncHistory (api);
	}

	@Override
	public void sync (BCSAPI api) throws BCSAPIException
	{
		throw new BCSAPIException ("not implemented");
	}

	@Override
	public boolean isOwnAddress (Address address)
	{
		return change.isOwnAddress (address) || receiver.isOwnAddress (address);
	}

	@Override
	public Address getNextChangeAddress () throws ValidationException
	{
		return change.getNextChangeAddress ();
	}

	@Override
	public Address getNextReceiverAddress () throws ValidationException
	{
		return receiver.getNextReceiverAddress ();
	}
}
