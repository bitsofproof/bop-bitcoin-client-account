package com.bitsofproof.supernode.account;

import java.util.List;

import com.bitsofproof.supernode.api.Address;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ValidationException;

public interface TransactionFactory extends AccountManager
{

	public Address getNextChangeAddress () throws ValidationException;

	public Address getNextReceiverAddress () throws ValidationException;

	public void reserveInputs (Transaction t);

	public boolean isReserved (String hash, long ix);

	public void releaseInputs (Transaction t);

	public Key getKeyForAddress (Address address);

	public Transaction pay (List<Address> receiver, List<Long> amounts, long fee, boolean senderPaysFee) throws ValidationException;

	public Transaction pay (Address receiver, long amount, long fee, boolean senderPaysFee) throws ValidationException;

	public Transaction pay (Address receiver, long amount, boolean senderPaysFee) throws ValidationException;

	public Transaction pay (List<Address> receiver, List<Long> amounts, boolean senderPaysFee) throws ValidationException;

}