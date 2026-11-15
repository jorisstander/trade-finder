package com.tradefinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Test;

public class CoinAmountsTest
{
	@Test
	public void parsesPlainAndShorthand()
	{
		assertEquals(1500L, CoinAmounts.parse("1500"));
		assertEquals(1500L, CoinAmounts.parse("1,500"));
		assertEquals(1500L, CoinAmounts.parse("1.5k"));
		assertEquals(2_000_000L, CoinAmounts.parse(" 2M "));
		assertEquals(1_435_500_000L, CoinAmounts.parse("1.4355b"));
		assertEquals(3_000_000_000L, CoinAmounts.parse("3b"));
	}

	@Test
	public void rejectsBadAmounts()
	{
		for (String bad : new String[]{"", "abc", "0", "-5", "1.5", "1.2345k", "99999999999b"})
		{
			try
			{
				CoinAmounts.parse(bad);
				fail("expected rejection of " + bad);
			}
			catch (NumberFormatException expected)
			{
				// ok
			}
		}
	}

	@Test
	public void formatsShortForm()
	{
		assertEquals("42", CoinAmounts.shortFormat(42));
		assertEquals("9,999", CoinAmounts.shortFormat(9_999));
		assertEquals("850K", CoinAmounts.shortFormat(850_000));
		assertEquals("12.5M", CoinAmounts.shortFormat(12_500_000));
		assertEquals("1.436B", CoinAmounts.shortFormat(1_435_500_000L));
	}
}
