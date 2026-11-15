package com.tradefinder;

/** What trading face to face saves compared with the Grand Exchange, shown as a green line on the forms. */
final class GeSavings
{
	private static final long TAX_PERCENT = 2;
	private static final long TAX_CAP = 5_000_000;
	/** The GE charges no tax on items sold for less than this. */
	private static final long TAX_FREE_BELOW = 50;

	private GeSavings()
	{
	}

	/** The GE tax on one item sold at {@code price}: 2% rounded down, at most 5m, none under 50 gp. */
	static long tax(long price)
	{
		if (price < TAX_FREE_BELOW)
		{
			return 0;
		}
		return Math.min(TAX_CAP, price * TAX_PERCENT / 100);
	}

	/** Seller: coins at {@code price} minus what the GE would pay after tax at the deal zone's low end. Never negative. */
	static long sellGain(long price, long quantity, long zoneLow)
	{
		if (zoneLow <= 0)
		{
			return 0;
		}
		long geNet = (zoneLow - tax(zoneLow)) * quantity;
		return Math.max(0, price * quantity - geNet);
	}

	/** Buyer: the GE insta-buy price minus the price paid, per item, times the quantity. Never negative. */
	static long buyGain(long instaBuy, long price, long quantity)
	{
		return Math.max(0, (instaBuy - price) * quantity);
	}
}
