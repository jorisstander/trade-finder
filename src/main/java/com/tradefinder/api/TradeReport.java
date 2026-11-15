package com.tradefinder.api;

import java.util.List;
import lombok.Value;

/**
 * Body of {@code POST /deals/{id}/report}: what this player's inventory lost ({@code gave}) and
 * gained ({@code received}) in the accepted trade. {@code outcome} is always {@code accepted}: a
 * trade that closes without "Accepted trade." produces no report at all.
 */
@Value
public class TradeReport
{
	public static final String ACCEPTED = "accepted";

	String outcome;
	List<TradedItem> gave;
	List<TradedItem> received;
}
