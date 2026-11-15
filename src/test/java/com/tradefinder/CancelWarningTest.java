package com.tradefinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.time.Instant;
import org.junit.Test;

public class CancelWarningTest
{
	private static final Instant FREE = Instant.parse("2026-11-15T12:10:00Z");

	@Test
	public void beforeTheFreeTimeItPausesPosting()
	{
		String text = CancelWarning.text(Instant.parse("2026-11-15T12:09:59Z"), FREE, true);
		assertTrue(text.contains("pauses your posting for 10 minutes"));
	}

	@Test
	public void noFreeTimeMeansTheWarning()
	{
		assertEquals(CancelWarning.text(FREE, null, true), CancelWarning.text(Instant.parse("2026-11-15T12:00:00Z"), FREE, true));
	}

	@Test
	public void atAndAfterTheFreeTimeItIsFree()
	{
		assertTrue(CancelWarning.text(FREE, FREE, true).contains("free"));
		assertTrue(CancelWarning.text(FREE.plusSeconds(600), FREE, true).contains("free"));
	}

	@Test
	public void notFreeUnlessYouWereAtTheMeetingSpotYourself()
	{
		assertTrue(CancelWarning.text(FREE.plusSeconds(600), FREE, false).contains("pauses your posting"));
	}
}
