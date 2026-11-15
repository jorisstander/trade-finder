package com.tradefinder.api;

import lombok.Value;

/** One stack in a trade report: {@code {item_id, quantity}}. Quantities are long (tokens are reported as tokens). */
@Value
public class TradedItem
{
	int itemId;
	long quantity;
}
