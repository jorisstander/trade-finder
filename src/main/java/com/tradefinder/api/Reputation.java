package com.tradefinder.api;

import lombok.Data;

/**
 * Lifetime counters of a player as the backend keeps them. Deals done can be farmed between two
 * accounts; no-shows and cancels cannot, so those are the ones to weigh.
 */
@Data
public class Reputation
{
	private int dealsDone;
	private int noShows;
	private int cancels;
}
