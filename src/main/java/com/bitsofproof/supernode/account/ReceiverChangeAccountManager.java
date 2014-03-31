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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.bitsofproof.supernode.api.Address;
import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionListener;
import com.bitsofproof.supernode.common.ExtendedKey;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ValidationException;

public class ReceiverChangeAccountManager extends BaseAccountManager implements TransactionListener
{
	private ExtendedKeyAccountManager receiver;
	private ExtendedKeyAccountManager change;
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

		receiver = new ExtendedKeyAccountManager ();
		receiver.setFirstIndex (getFirstIndex ());
		receiver.setLookAhead (getLookAhead ());
		receiver.setCreated (getCreated ());
		receiver.setMaster (master.getChild (0));
		receiver.addAccountListener (listener);

		change = new ExtendedKeyAccountManager ();
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

	@Override
	public Key getNextKey () throws ValidationException
	{
		return change.getNextKey ();
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
}