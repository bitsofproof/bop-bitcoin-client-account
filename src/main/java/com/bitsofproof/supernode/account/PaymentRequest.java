/*
 * Copyright 2014 bits of proof zrt.
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

import java.util.HashSet;
import java.util.Set;

import com.bitsofproof.supernode.api.Address;
import com.bitsofproof.supernode.api.TransactionOutput;

public class PaymentRequest
{
	private Address address;
	private long expectedAmount;
	private int expectedConfirmations;
	private int confirmationHeight;
	private long receivedAmount;
	private Set<TransactionOutput> outputs = new HashSet<> ();

	public PaymentRequest (Address address, long expectedAmount, int expectedConfirmations)
	{
		this.address = address;
		this.expectedAmount = expectedAmount;
		this.expectedConfirmations = expectedConfirmations;
	}

	public Address getAddress ()
	{
		return address;
	}

	public long getAmount ()
	{
		return expectedAmount;
	}

	public int getConfirmationHeight ()
	{
		return confirmationHeight;
	}

	public void setConfirmationHeight (int confirmationHeight)
	{
		this.confirmationHeight = confirmationHeight;
	}

	public Set<TransactionOutput> getOutputs ()
	{
		return outputs;
	}

	public int getExpectedConfirmations ()
	{
		return expectedConfirmations;
	}

	public long getReceivedAmount ()
	{
		return receivedAmount;
	}

	public void setReceivedAmount (long receivedAmount)
	{
		this.receivedAmount = receivedAmount;
	}

	@Override
	public int hashCode ()
	{
		return address.hashCode ();
	}

	@Override
	public boolean equals (Object obj)
	{
		if ( obj instanceof PaymentRequest )
		{
			return address.equals (((PaymentRequest) obj).address);
		}
		return false;
	}
}
