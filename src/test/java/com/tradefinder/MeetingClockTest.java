package com.tradefinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.time.Duration;
import org.junit.Test;

public class MeetingClockTest
{
	@Test
	public void text()
	{
		assertEquals("14:05", MeetingClock.text(Duration.ofSeconds(14 * 60 + 5)));
		assertEquals("time up", MeetingClock.text(Duration.ZERO));
		assertEquals("time up", MeetingClock.text(Duration.ofSeconds(-3)));
	}

	@Test
	public void barEmptiesAndStaysInRange()
	{
		Duration total = Duration.ofMinutes(15);
		assertEquals(1, MeetingClock.fraction(Duration.ofMinutes(15), total), 1e-9);
		assertEquals(0.5, MeetingClock.fraction(Duration.ofSeconds(450), total), 1e-9);
		assertEquals(0, MeetingClock.fraction(Duration.ofSeconds(-5), total), 1e-9);
		assertEquals(1, MeetingClock.fraction(Duration.ofMinutes(20), total), 1e-9);
		assertEquals(0.5, MeetingClock.fraction(Duration.ofSeconds(450), Duration.ZERO), 1e-9);
	}

	@Test
	public void redInTheLastTwoMinutes()
	{
		assertTrue(MeetingClock.isUrgent(Duration.ofSeconds(119)));
		assertFalse(MeetingClock.isUrgent(Duration.ofMinutes(2)));
	}
}
