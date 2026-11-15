package com.tradefinder.api;

import java.util.List;
import javax.annotation.Nullable;
import lombok.Data;

/** {@code GET /prices/{item_id}} response. */
@Data
public class PriceInfo
{
	private int itemId;
	private long instaBuy;
	private long instaSell;
	private String fetchedAt;
	private DealZone dealZone;
	/** {@code below}, {@code inside} or {@code above} when the request carried {@code ?price=}, else null. */
	@Nullable
	private String warning;
	private List<RecentDeal> recentDeals;

	@Data
	public static class DealZone
	{
		private long low;
		private long high;
		private long suggested;
	}

	@Data
	public static class RecentDeal
	{
		private long price;
		private long quantity;
		private String at;
	}
}
