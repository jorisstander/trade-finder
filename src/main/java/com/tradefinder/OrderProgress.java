package com.tradefinder;

import com.tradefinder.api.Side;

/** The progress line and bar fraction of an order: "3 of 6 sold" / "200 of 1,000 bought". */
final class OrderProgress
{
	private OrderProgress()
	{
	}

	static String text(Side side, long filled, long quantity)
	{
		return CoinAmounts.format(clamp(filled, quantity)) + " of " + CoinAmounts.format(quantity)
			+ (side == Side.SELL ? " sold" : " bought");
	}

	/** 0..1 */
	static double fraction(long filled, long quantity)
	{
		if (quantity <= 0)
		{
			return 0;
		}
		return (double) clamp(filled, quantity) / quantity;
	}

	private static long clamp(long filled, long quantity)
	{
		return Math.max(0, Math.min(filled, quantity));
	}
}
