package com.tradefinder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TradeTitleTest
{
	@Test
	public void exactNameMatches()
	{
		assertTrue(TradeTitle.isWith("Trading with: Bob", "Bob"));
		assertTrue(TradeTitle.isWith("Trading With: bob", "BOB"));
		assertTrue(TradeTitle.isWith("<col=ffffff>Trading with:</col> Bob", "Bob"));
	}

	@Test
	public void substringDoesNotMatch()
	{
		assertFalse(TradeTitle.isWith("Trading with: xBobx", "Bob"));
		assertFalse(TradeTitle.isWith("Trading with: Bobby", "Bob"));
		assertFalse(TradeTitle.isWith("Trading with: Bob", "Bobby"));
		assertFalse(TradeTitle.isWith("Trading with: Big Bob", "Bob"));
	}

	@Test
	public void nonBreakingSpacesAreSpaces()
	{
		assertTrue(TradeTitle.isWith("Trading with: Big\u00A0Bob", "Big Bob"));
		assertTrue(TradeTitle.isWith("Trading with: Big Bob", "Big\u00A0Bob"));
	}

	@Test
	public void noMarkerNeedsTheWholeText()
	{
		assertTrue(TradeTitle.isWith("Bob", "Bob"));
		assertFalse(TradeTitle.isWith("Hello Bob", "Bob"));
		assertFalse(TradeTitle.isWith("", "Bob"));
		assertFalse(TradeTitle.isWith("Trading with:", "Bob"));
		assertFalse(TradeTitle.isWith(null, "Bob"));
	}
}
