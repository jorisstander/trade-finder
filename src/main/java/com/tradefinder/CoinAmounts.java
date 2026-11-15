package com.tradefinder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import net.runelite.client.util.QuantityFormatter;

/** Parse and format coin amounts and quantities with the usual k/m/b shorthand. Amounts are long. */
final class CoinAmounts
{
	/** Largest stack the game allows, platinum tokens included. */
	static final long MAX_STACK = Integer.MAX_VALUE;

	private CoinAmounts()
	{
	}

	/**
	 * Parse "1500", "1,500", "1.5k", "2m", "1.4355b" (case-insensitive) into a whole number of coins.
	 *
	 * @throws NumberFormatException when the text is empty, not a number, fractional after scaling,
	 *                               zero, negative or beyond a long
	 */
	static long parse(String text)
	{
		String s = text.trim().toLowerCase(Locale.ROOT).replace(",", "").replace(" ", "");
		if (s.isEmpty())
		{
			throw new NumberFormatException("empty");
		}
		long multiplier = 1;
		char last = s.charAt(s.length() - 1);
		if (last == 'k')
		{
			multiplier = 1_000L;
		}
		else if (last == 'm')
		{
			multiplier = 1_000_000L;
		}
		else if (last == 'b')
		{
			multiplier = 1_000_000_000L;
		}
		if (multiplier != 1)
		{
			s = s.substring(0, s.length() - 1);
		}
		BigDecimal value;
		try
		{
			value = new BigDecimal(s).multiply(BigDecimal.valueOf(multiplier));
		}
		catch (ArithmeticException e)
		{
			throw new NumberFormatException("not a number: " + text);
		}
		if (value.stripTrailingZeros().scale() > 0)
		{
			throw new NumberFormatException("not a whole number of coins: " + text);
		}
		if (value.signum() <= 0)
		{
			throw new NumberFormatException("must be positive: " + text);
		}
		try
		{
			return value.longValueExact();
		}
		catch (ArithmeticException e)
		{
			throw new NumberFormatException("too large: " + text);
		}
	}

	/** Exact amount with thousands separators, e.g. "1,435,500,000". */
	static String format(long amount)
	{
		return QuantityFormatter.formatNumber(amount);
	}

	/** Short form with up to three decimals, e.g. "1.436B", "12.5M", "850K", "42". */
	static String shortFormat(long amount)
	{
		long abs = Math.abs(amount);
		if (abs >= 1_000_000_000L)
		{
			return scaled(amount, 1_000_000_000L) + "B";
		}
		if (abs >= 1_000_000L)
		{
			return scaled(amount, 1_000_000L) + "M";
		}
		if (abs >= 10_000L)
		{
			return scaled(amount, 1_000L) + "K";
		}
		return format(amount);
	}

	private static String scaled(long amount, long divisor)
	{
		return BigDecimal.valueOf(amount)
			.divide(BigDecimal.valueOf(divisor), 3, RoundingMode.HALF_UP)
			.stripTrailingZeros()
			.toPlainString();
	}
}
