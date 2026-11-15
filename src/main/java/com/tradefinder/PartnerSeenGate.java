package com.tradefinder;

/**
 * Decides when to tell the server "I saw my partner at the meeting spot": once per deal and world,
 * again after a failed attempt (after a short wait) and again after a world change. Pure; tick counts
 * come from the caller. Client thread.
 */
final class PartnerSeenGate
{
	/** Ticks to wait after a failed attempt (about 6 seconds). */
	static final int RETRY_TICKS = 10;

	private boolean sent;
	private int notBefore = -1;

	/** True when the signal should be sent now; the gate then counts it as sent until {@link #failed} or {@link #reset}. */
	boolean due(boolean partnerAtMeetingSpot, int tick)
	{
		if (sent || !partnerAtMeetingSpot || tick < notBefore)
		{
			return false;
		}
		sent = true;
		return true;
	}

	/** The server could not be told: try again after a short wait. */
	void failed(int tick)
	{
		sent = false;
		notBefore = tick + RETRY_TICKS;
	}

	/** A new deal, or the world changed: the partner has to be seen again. */
	void reset()
	{
		sent = false;
		notBefore = -1;
	}
}
