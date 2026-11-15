package com.tradefinder.api;

import java.util.List;
import lombok.Data;

/** {@code GET /deals/history}: this player's counters and their closed deals, newest first (at most 20). */
@Data
public class DealHistory
{
	private int dealsDone;
	private int noShows;
	private int cancels;
	/** Same card shape as {@code GET /deals/{id}}; status is completed, disputed, expired or cancelled. */
	private List<Deal> deals;
}
