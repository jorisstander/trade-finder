package com.tradefinder;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class GeSavingsTest
{
	@Test
	public void taxIsTwoPercentCappedAndFreeUnderFifty()
	{
		assertEquals(0, GeSavings.tax(49));
		assertEquals(0, GeSavings.tax(0));
		assertEquals(1, GeSavings.tax(50));
		assertEquals(30, GeSavings.tax(1_500));
		assertEquals(5_000_000, GeSavings.tax(1_000_000_000L));
	}

	@Test
	public void sellerKeepsTheTaxPlusAnyPriceDifference()
	{
		// GE would pay (1500 - 30) * 6 = 8820; at 1500 each face to face = 9000.
		assertEquals(180, GeSavings.sellGain(1_500, 6, 1_500));
		assertEquals(240, GeSavings.sellGain(1_510, 6, 1_500));
	}

	@Test
	public void noGainWithoutAZoneLow()
	{
		assertEquals(0, GeSavings.sellGain(1_500, 6, 0));
	}

	@Test
	public void sellGainNeverNegative()
	{
		assertEquals(0, GeSavings.sellGain(1_000, 6, 1_500));
	}

	@Test
	public void buyerSavesTheDifferenceToInstaBuy()
	{
		assertEquals(600, GeSavings.buyGain(1_600, 1_500, 6));
		assertEquals(0, GeSavings.buyGain(1_400, 1_500, 6));
	}
}
