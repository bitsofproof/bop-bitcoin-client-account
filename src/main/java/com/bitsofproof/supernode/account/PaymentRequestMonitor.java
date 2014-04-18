package com.bitsofproof.supernode.account;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.Address;
import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionOutput;

public class PaymentRequestMonitor extends AddressListAccountManager implements ConfirmationListener, AccountListener
{
	private static final Logger log = LoggerFactory.getLogger (PaymentRequestMonitor.class);

	private final ConfirmationManager confirmationManager;

	private final Map<PaymentRequest, Set<PaymentRequestListener>> paymentRequestListener = Collections
			.synchronizedMap (new HashMap<PaymentRequest, Set<PaymentRequestListener>> ());
	private Map<Address, PaymentRequest> addressToRequest = Collections
			.synchronizedMap (new HashMap<Address, PaymentRequest> ());

	public PaymentRequestMonitor (BCSAPI api, ConfirmationManager confirmationManager, Collection<PaymentRequest> pastRequests) throws BCSAPIException
	{
		log.trace ("Initialize payment request monitor");
		this.confirmationManager = confirmationManager;

		if ( pastRequests != null && !pastRequests.isEmpty () )
		{
			log.trace ("catch up " + pastRequests.size () + " old requests");
			catchUp (api, pastRequests);
		}
		api.registerTransactionListener (this);
		api.registerRejectListener (this);
		confirmationManager.addAccount (this);
		confirmationManager.addConfirmationListener (this);
	}

	private void catchUp (BCSAPI api, Collection<PaymentRequest> pastRequests) throws BCSAPIException
	{
		for ( PaymentRequest r : pastRequests )
		{
			addAddress (r.getAddress ());
			addressToRequest.put (r.getAddress (), r);
		}
		sync (api);
		for ( PaymentRequest request : pastRequests )
		{
			request.setReceivedAmount (0);
			request.setConfirmationHeight (0);
		}
		for ( Transaction t : getTransactions () )
		{
			for ( TransactionOutput out : t.getOutputs () )
			{
				PaymentRequest request = addressToRequest.get (out.getOutputAddress ());
				if ( request != null )
				{
					log.trace ("payment " + t.getHash () + " to " + request.getAddress ());
					request.setConfirmationHeight (Math.max (request.getConfirmationHeight (), t.getHeight ()));
					request.setReceivedAmount (request.getReceivedAmount () + out.getValue ());
				}
			}
		}
		int height = confirmationManager.getHeight ();
		for ( PaymentRequest request : pastRequests )
		{
			if ( !request.getOutputs ().isEmpty () )
			{
				notifyPaid (request);
				if ( (height - request.getConfirmationHeight () + 1) >= request.getExpectedConfirmations () )
				{
					notifyConfirmed (request);
				}
			}
		}
	}

	public void monitor (Address address, long amount, int confirmations)
	{
		addAddress (address);
		confirmationManager.addAccount (this);
		confirmationManager.addConfirmationListener (this);
	}

	public void addPaymentRequestListener (PaymentRequest request, PaymentRequestListener listener)
	{
		synchronized ( paymentRequestListener )
		{
			Set<PaymentRequestListener> listenerSet = paymentRequestListener.get (request);
			if ( listenerSet == null )
			{
				listenerSet = new HashSet<PaymentRequestListener> ();
				paymentRequestListener.put (request, listenerSet);
				addressToRequest.put (request.getAddress (), request);
			}
			listenerSet.add (listener);
		}
	}

	public void removePaymentRequestListener (PaymentRequest request, PaymentRequestListener listener)
	{
		synchronized ( paymentRequestListener )
		{
			Set<PaymentRequestListener> listenerSet = paymentRequestListener.get (request);
			if ( listenerSet != null )
			{
				listenerSet.remove (listener);
				if ( listenerSet.isEmpty () )
				{
					paymentRequestListener.remove (request);
					addressToRequest.remove (request.getAddress ());
				}
			}
		}
	}

	@Override
	public void confirmed (Transaction t)
	{
		for ( TransactionOutput out : t.getOutputs () )
		{
			PaymentRequest request = addressToRequest.get (out.getOutputAddress ());
			if ( request != null )
			{
				if ( t.getBlockHash () != null )
				{
					log.trace ("Confirmed " + t.getHash ());
					request.setConfirmationHeight (Math.max (request.getConfirmationHeight (), t.getHeight ()));
				}
				else
				{
					if ( t.getOffendingTx () != null )
					{
						log.trace ("Double spend " + t.getHash ());
						request.setConfirmationHeight (-1);
					}
					else
					{
						log.trace ("Unconfirmed " + t.getHash ());
						int prev = request.getConfirmationHeight ();
						request.setConfirmationHeight (0);
						if ( prev != 0 || request.getExpectedConfirmations () == 0 )
						{
							notifyUnconfirmed (request);
						}
					}
				}
			}
		}
	}

	@Override
	public void accountChanged (AccountManager account, Transaction t)
	{
		Set<PaymentRequest> notify = new HashSet<PaymentRequest> ();
		for ( TransactionOutput out : t.getOutputs () )
		{
			PaymentRequest request = addressToRequest.get (out.getOutputAddress ());
			if ( request != null )
			{
				if ( t.getOffendingTx () == null )
				{
					if ( request.getOutputs ().add (out) )
					{
						request.setReceivedAmount (request.getReceivedAmount () + out.getValue ());
						notify.add (request);
					}
				}
				else
				{
					if ( request.getOutputs ().remove (out) )
					{
						request.setReceivedAmount (request.getReceivedAmount () - out.getValue ());
						notify.add (request);
					}
				}
			}
		}
		for ( PaymentRequest p : notify )
		{
			if ( t.getOffendingTx () == null )
			{
				notifyPaid (p);
				if ( p.getExpectedConfirmations () == 0 )
				{
					notifyConfirmed (p);
				}
			}
			else
			{
				notifyDoubleSpent (p);
			}
		}
	}

	@Override
	public void newHeight (int height)
	{
		for ( PaymentRequest request : paymentRequestListener.keySet () )
		{
			if ( request.getConfirmationHeight () > 0
					&& (height - request.getConfirmationHeight () + 1) == request.getExpectedConfirmations () )
			{
				notifyConfirmed (request);
			}
		}
	}

	private void notifyDoubleSpent (PaymentRequest request)
	{
		Set<PaymentRequestListener> listenerSet = paymentRequestListener.get (request);
		if ( listenerSet != null )
		{
			for ( PaymentRequestListener l : listenerSet )
			{
				l.doubleSpent (request);
			}
		}
	}

	private void notifyConfirmed (PaymentRequest request)
	{
		Set<PaymentRequestListener> listenerSet = paymentRequestListener.get (request);
		if ( listenerSet != null )
		{
			for ( PaymentRequestListener l : listenerSet )
			{
				l.confirmed (request);
			}
		}
	}

	private void notifyPaid (PaymentRequest request)
	{
		Set<PaymentRequestListener> listenerSet = paymentRequestListener.get (request);
		if ( listenerSet != null )
		{
			for ( PaymentRequestListener l : listenerSet )
			{
				l.paid (request);
			}
		}
	}

	private void notifyUnconfirmed (PaymentRequest request)
	{
		Set<PaymentRequestListener> listenerSet = paymentRequestListener.get (request);
		if ( listenerSet != null )
		{
			for ( PaymentRequestListener l : listenerSet )
			{
				l.unconfirmed (request);
			}
		}
	}
}
