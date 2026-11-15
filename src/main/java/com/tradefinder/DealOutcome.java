package com.tradefinder;

import com.tradefinder.api.Side;

/** How a closed deal reads in the last-deal card and the history list: the pill and the headline verb. */
enum DealOutcome
{
	COMPLETED("Completed", true),
	CANCELLED("Cancelled", false),
	EXPIRED("Expired", false),
	DISPUTED("Disputed", false),
	OTHER("", false);

	private final String pill;
	private final boolean good;

	DealOutcome(String pill, boolean good)
	{
		this.pill = pill;
		this.good = good;
	}

	static DealOutcome of(String status)
	{
		if (status != null)
		{
			switch (status)
			{
				case "completed":
					return COMPLETED;
				case "cancelled":
					return CANCELLED;
				case "expired":
					return EXPIRED;
				case "disputed":
					return DISPUTED;
				default:
					break;
			}
		}
		return OTHER;
	}

	/** Pill text; empty for an unknown status. */
	String pill()
	{
		return pill;
	}

	/** Completed is green, everything else that is shown is red. */
	boolean isGood()
	{
		return good;
	}

	/** "Sold" / "Bought" only when the trade happened; otherwise just the amount and item, so a cancelled deal never claims a sale. */
	String headline(Side side, long quantity, String itemName)
	{
		String amount = CoinAmounts.format(quantity) + " " + itemName;
		if (this != COMPLETED || side == null)
		{
			return amount;
		}
		return (side == Side.SELL ? "Sold " : "Bought ") + amount;
	}
}
