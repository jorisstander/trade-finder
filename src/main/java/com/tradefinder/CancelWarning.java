package com.tradefinder;

import java.time.Instant;
import javax.annotation.Nullable;

/** What "Cancel deal" tells the player before it does anything. Pure. */
final class CancelWarning
{
	static final String BUTTON_YES = "Yes, cancel the deal";
	static final String BUTTON_NO = "Keep the deal";

	private CancelWarning()
	{
	}

	/**
	 * Cancelling is free only when the server's {@code free_cancel_at} has passed (the partner did
	 * not show up) and this player waited at the meeting spot themselves; otherwise it pauses their
	 * posting for 10 minutes.
	 *
	 * @param atMeetingSpot this client is on the deal's world and inside the Grand Exchange area
	 */
	static String text(Instant now, @Nullable Instant freeCancelAt, boolean atMeetingSpot)
	{
		if (freeCancelAt != null && !now.isBefore(freeCancelAt) && atMeetingSpot)
		{
			return "Your partner did not show up in time, so cancelling is free: you can post again right away.";
		}
		return "Cancelling pauses your posting for 10 minutes. Your partner's order stays open for other players.";
	}
}
