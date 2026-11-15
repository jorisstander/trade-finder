package com.tradefinder.api;

import lombok.AllArgsConstructor;
import lombok.Data;

/** {@code POST /orders} body. */
@Data
@AllArgsConstructor
public class OrderRequest
{
	private int itemId;
	private Side side;
	private long quantity;
	private long limitPrice;
	/** {@code ItemComposition.isMembers()} of the canonical item; the server refuses it for a non-member. */
	private boolean membersItem;
}
