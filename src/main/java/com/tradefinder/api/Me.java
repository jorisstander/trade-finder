package com.tradefinder.api;

import javax.annotation.Nullable;
import lombok.Data;

/**
 * {@code GET /me}: this player's counters, order slots and posting pause. {@code blockedUntil}
 * and {@code blockedReason} are null when posting is allowed.
 */
@Data
public class Me
{
	private String rsn;
	private int dealsDone;
	private int noShows;
	private int cancels;
	private int maxOpenOrders;
	private int slotsUsed;
	@Nullable
	private String blockedUntil;
	/** Short phrase: "you cancelled a deal", "you missed a meeting", "3 unconfirmed meetings in a row". */
	@Nullable
	private String blockedReason;
}
