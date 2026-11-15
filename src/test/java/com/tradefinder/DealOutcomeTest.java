package com.tradefinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.tradefinder.api.Side;
import org.junit.Test;

public class DealOutcomeTest
{
	@Test
	public void onlyACompletedDealSaysSoldOrBought()
	{
		assertEquals("Sold 6 Anglerfish", DealOutcome.of("completed").headline(Side.SELL, 6, "Anglerfish"));
		assertEquals("Bought 6 Anglerfish", DealOutcome.of("completed").headline(Side.BUY, 6, "Anglerfish"));
		assertEquals("6 Anglerfish", DealOutcome.of("cancelled").headline(Side.SELL, 6, "Anglerfish"));
		assertEquals("6 Anglerfish", DealOutcome.of("expired").headline(Side.BUY, 6, "Anglerfish"));
		assertEquals("6 Anglerfish", DealOutcome.of("disputed").headline(Side.SELL, 6, "Anglerfish"));
	}

	@Test
	public void pillsAndColours()
	{
		assertEquals("Completed", DealOutcome.of("completed").pill());
		assertTrue(DealOutcome.of("completed").isGood());
		for (String bad : new String[]{"cancelled", "expired", "disputed"})
		{
			assertFalse(DealOutcome.of(bad).isGood());
		}
		assertEquals("Cancelled", DealOutcome.of("cancelled").pill());
		assertEquals("", DealOutcome.of(null).pill());
		assertEquals("", DealOutcome.of("deal_on").pill());
	}
}
