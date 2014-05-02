package com.bitsofproof.supernode.account;

public class PaymentOptions
{
	public enum Priority
	{
		LOW, NORMAL, HIGH
	};

	public enum FeeSource
	{
		SENDER, RECEIVER
	};

	public enum FeeCalculation
	{
		FIXED, CALCULATED
	};

	public enum OutputOrder
	{
		FIXED, SHUFFLED
	};

	private long fee;
	private Priority priority;
	private FeeSource source;
	private FeeCalculation calculation;
	private OutputOrder outputOrder = OutputOrder.SHUFFLED;
	private int change = 1;

	public static final PaymentOptions common = new PaymentOptions (FeeSource.SENDER);
	public static final PaymentOptions lowPriority = new PaymentOptions (Priority.LOW);
	public static final PaymentOptions highPriority = new PaymentOptions (Priority.HIGH);

	public static final PaymentOptions receiverPaysFee = new PaymentOptions (FeeSource.RECEIVER);

	public PaymentOptions (long fee)
	{
		this.fee = fee;
		this.calculation = FeeCalculation.FIXED;
		this.source = FeeSource.SENDER;
		this.priority = Priority.NORMAL;
	}

	public PaymentOptions (FeeSource source)
	{
		this.calculation = FeeCalculation.CALCULATED;
		this.source = source;
		this.priority = Priority.NORMAL;
	}

	public PaymentOptions (Priority priority)
	{
		this.calculation = FeeCalculation.CALCULATED;
		this.source = FeeSource.SENDER;
		this.priority = priority;
	}

	public PaymentOptions (FeeSource source, Priority priority)
	{
		this.calculation = FeeCalculation.CALCULATED;
		this.source = source;
		this.priority = priority;
	}

	public PaymentOptions (long fee, FeeCalculation calculation, FeeSource source, Priority priority)
	{
		this.fee = fee;
		this.calculation = calculation;
		this.source = source;
		this.priority = priority;
	}

	public PaymentOptions (long fee, FeeCalculation calculation, FeeSource source, Priority priority, OutputOrder outputOrder, int change)
	{
		this.fee = fee;
		this.calculation = calculation;
		this.source = source;
		this.priority = priority;
		this.outputOrder = outputOrder;
		this.change = change;
	}

	public boolean isCalculated ()
	{
		return calculation == FeeCalculation.CALCULATED;
	}

	public boolean isPaidBySender ()
	{
		return source == FeeSource.SENDER;
	}

	public boolean isLowPriority ()
	{
		return priority == Priority.LOW;
	}

	public boolean isNormalPriority ()
	{
		return priority == Priority.NORMAL;
	}

	public boolean isHighPriority ()
	{
		return priority == Priority.HIGH;
	}

	public boolean isShuffled ()
	{
		return outputOrder == OutputOrder.SHUFFLED;
	}

	public int getChange ()
	{
		return change;
	}

	public long getFee ()
	{
		return fee;
	}

	public Priority getPriority ()
	{
		return priority;
	}

	public FeeSource getSource ()
	{
		return source;
	}

	public FeeCalculation getCalculation ()
	{
		return calculation;
	}

	public OutputOrder getOutputOrder ()
	{
		return outputOrder;
	}

	public void setFee (long fee)
	{
		this.fee = fee;
	}

	public void setPriority (Priority priority)
	{
		this.priority = priority;
	}

	public void setSource (FeeSource source)
	{
		this.source = source;
	}

	public void setCalculation (FeeCalculation calculation)
	{
		this.calculation = calculation;
	}

	public void setOutputOrder (OutputOrder outputOrder)
	{
		this.outputOrder = outputOrder;
	}

	public void setChange (int change)
	{
		this.change = change;
	}

	@Override
	public String toString ()
	{
		return "PaymentOptions [fee=" + fee + ", calculation=" + calculation + ", source=" + source + ", priority=" + priority + ", outputOrder=" + outputOrder
				+ ", change=" + change + "]";
	}

}
