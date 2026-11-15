package com.tradefinder;

import java.time.Duration;

/** The meeting countdown: text, the bar that empties and when it turns red. */
final class MeetingClock
{
	static final Duration FALLBACK_TOTAL = Duration.ofMinutes(15);
	static final Duration WARN = Duration.ofMinutes(2);

	private MeetingClock()
	{
	}

	/** m:ss, or "time up" at or below zero. */
	static String text(Duration left)
	{
		if (left.isNegative() || left.isZero())
		{
			return "time up";
		}
		long minutes = left.toMinutes();
		long seconds = left.minusMinutes(minutes).getSeconds();
		return String.format("%d:%02d", minutes, seconds);
	}

	/** Share of the meeting time that is left, 0..1; a non-positive total counts as the 15 minute default. */
	static double fraction(Duration left, Duration total)
	{
		long totalMillis = total == null || total.isZero() || total.isNegative() ? FALLBACK_TOTAL.toMillis() : total.toMillis();
		double f = (double) left.toMillis() / totalMillis;
		return Math.max(0, Math.min(1, f));
	}

	/** Red in the last two minutes (and at zero), yellow before. */
	static boolean isUrgent(Duration left)
	{
		return left.compareTo(WARN) < 0;
	}
}
