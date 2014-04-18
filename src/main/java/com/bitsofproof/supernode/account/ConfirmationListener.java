package com.bitsofproof.supernode.account;

import com.bitsofproof.supernode.api.Transaction;

public interface ConfirmationListener
{
	public void confirmed (Transaction t);

	public void newHeight (int height);
}
