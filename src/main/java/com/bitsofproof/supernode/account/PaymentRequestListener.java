package com.bitsofproof.supernode.account;

public interface PaymentRequestListener
{
	public void paid (PaymentRequest p);

	public void confirmed (PaymentRequest p);

	public void doubleSpent (PaymentRequest p);

	public void unconfirmed (PaymentRequest p);
}
