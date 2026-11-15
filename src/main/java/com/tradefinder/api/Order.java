package com.tradefinder.api;

import lombok.Data;

/** One of this player's orders, as the backend returns it. Amounts are long (platinum-token trades). */
@Data
public class Order
{
	private long id;
	private int itemId;
	private Side side;
	/** The original amount; it never shrinks. */
	private long quantity;
	/** Traded so far (partial deals); {@code quantity - filled} is what is still wanted. */
	private long filled;
	private long limitPrice;
	/** {@code open} (also after a partial deal), {@code matched}, {@code filled} (nothing remains) or {@code cancelled}. */
	private String status;
	private String createdAt;
}
