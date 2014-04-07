package com.bitsofproof.supernode.account;

import org.bouncycastle.util.Arrays;

public class TransactionSink
{
	private final byte[] script;
	private final long value;

	public TransactionSink (byte[] script, long value)
	{
		this.script = Arrays.clone (script);
		this.value = value;
	}

	public byte[] getScript ()
	{
		return Arrays.clone (script);
	}

	public long getValue ()
	{
		return value;
	}
}
