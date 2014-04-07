package com.bitsofproof.supernode.account;

import com.bitsofproof.supernode.api.Address;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionOutput;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ScriptFormat;
import com.bitsofproof.supernode.common.ValidationException;

public class TransactionSource
{
	private final TransactionOutput source;
	private final TransactionFactory account;

	public TransactionSource (TransactionOutput source, TransactionFactory account)
	{
		this.source = source;
		this.account = account;
	}

	public TransactionOutput getSource ()
	{
		return source;
	}

	public TransactionFactory getAccount ()
	{
		return account;
	}

	protected byte[] spend (int ix, Transaction transaction) throws ValidationException
	{
		if ( ScriptFormat.isPayToAddress (getSource ().getScript ()) )
		{
			ScriptFormat.Writer sw = new ScriptFormat.Writer ();
			Address address = getSource ().getOutputAddress ();
			Key key = getAccount ().getKeyForAddress (address);
			if ( key == null )
			{
				throw new ValidationException ("Have no key to spend this output");
			}
			byte[] sig = key.sign (transaction.hashTransaction (ix, ScriptFormat.SIGHASH_ALL, getSource ().getScript ()));
			byte[] sigPlusType = new byte[sig.length + 1];
			System.arraycopy (sig, 0, sigPlusType, 0, sig.length);
			sigPlusType[sigPlusType.length - 1] = (byte) (ScriptFormat.SIGHASH_ALL & 0xff);
			sw.writeData (sigPlusType);
			sw.writeData (key.getPublic ());
			return sw.toByteArray ();
		}
		else
		{
			throw new ValidationException ("Can not spend this output type");
		}
	}
}
