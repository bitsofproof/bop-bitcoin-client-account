package com.bitsofproof.supernode.account;

import com.bitsofproof.supernode.api.Transaction;

public interface ConfirmationsListener
{
	public void confirmed (Transaction t);
}
