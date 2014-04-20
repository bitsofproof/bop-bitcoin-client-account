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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.Address;
import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionListener;

public class AddressListAccountManager extends BaseAccountManager
{
	private static final Logger log = LoggerFactory.getLogger (AddressListAccountManager.class);

	private final Set<Address> addresses = new HashSet<> ();

	@Override
	public Set<Address> getAddresses ()
	{
		return Collections.unmodifiableSet (addresses);
	}

	@Override
	public boolean isOwnAddress (Address address)
	{
		return addresses.contains (address);
	}

	public void addAddress (Address address)
	{
		addresses.add (address);
	}

	@Override
	public void syncHistory (BCSAPI api) throws BCSAPIException
	{
		reset ();
		log.trace ("Sync naddr: " + addresses.size ());
		api.scanTransactionsForAddresses (getAddresses (), getCreated (), new TransactionListener ()
		{
			@Override
			public boolean process (Transaction t)
			{
				return updateWithTransaction (t);
			}
		});
		log.trace ("Sync finished naddr: " + addresses.size ());
	}

	@Override
	public void sync (BCSAPI api) throws BCSAPIException
	{
		reset ();
		log.trace ("Sync naddr: " + addresses.size ());
		api.scanUTXOForAddresses (getAddresses (), new TransactionListener ()
		{
			@Override
			public boolean process (Transaction t)
			{
				return updateWithTransaction (t);
			}
		});
		log.trace ("Sync finished naddr: " + addresses.size ());
	}
}
