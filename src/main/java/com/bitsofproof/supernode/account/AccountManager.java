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

import java.util.Set;

import com.bitsofproof.supernode.api.Address;
import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.RejectListener;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionListener;
import com.bitsofproof.supernode.api.TransactionOutput;

public interface AccountManager extends TransactionListener, RejectListener
{
	public interface UTXO
	{

		public boolean add (TransactionOutput out);

		public Set<TransactionOutput> getUTXO ();

		public TransactionOutput get (String tx, long ix);

		public TransactionOutput remove (String tx, long ix);

		public long getTotal ();

	}

	public void sync (BCSAPI api) throws BCSAPIException;

	public void syncHistory (BCSAPI api) throws BCSAPIException;

	public long getCreated ();

	public boolean isOwnAddress (Address address);

	public Set<Address> getAddresses ();

	public long getBalance ();

	public long getConfirmed ();

	public long getSending ();

	public long getReceiving ();

	public long getChange ();

	public Set<TransactionOutput> getConfirmedOutputs ();

	public Set<TransactionOutput> getSendingOutputs ();

	public Set<TransactionOutput> getReceivingOutputs ();

	public Set<TransactionOutput> getChangeOutputs ();

	public Set<Transaction> getTransactions ();

	public boolean isKnownTransaction (Transaction t);

	public void addAccountListener (AccountListener listener);

	public void removeAccountListener (AccountListener listener);
}