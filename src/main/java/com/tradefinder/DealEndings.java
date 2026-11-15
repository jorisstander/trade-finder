package com.tradefinder;

import com.tradefinder.api.RealtimeEvent;
import javax.annotation.Nullable;

/** What the player is told, in plain words, when a deal ends: the line on the Deal tab and the notification. Pure. */
final class DealEndings
{
	private DealEndings()
	{
	}

	/** The Deal tab line for the push {@code eventType}, or null when that event does not end a deal. */
	@Nullable
	static String status(String eventType)
	{
		switch (eventType)
		{
			case RealtimeEvent.CANCELLED:
				return "The deal was cancelled.";
			case RealtimeEvent.EXPIRED:
				return "The time to meet ran out. The player who did not show up lost their order. "
					+ "If you were at the meeting spot, your order is open again and can be matched with someone else.";
			case RealtimeEvent.COMPLETED:
				return "Deal completed. Thanks for trading tax-free!";
			case RealtimeEvent.DISPUTED:
				return "The trade could not be confirmed: you and your partner reported different items.";
			default:
				return null;
		}
	}

	/** The notification for the push {@code eventType}, or null when that event does not end a deal. */
	@Nullable
	static String notification(String eventType)
	{
		switch (eventType)
		{
			case RealtimeEvent.CANCELLED:
				return "Trade Finder: the deal was cancelled.";
			case RealtimeEvent.EXPIRED:
				return "Trade Finder: the deal ran out of time. Nobody completed the trade.";
			case RealtimeEvent.COMPLETED:
				return "Trade Finder: the deal is completed.";
			case RealtimeEvent.DISPUTED:
				return "Trade Finder: the trade could not be confirmed because you and your partner reported different items.";
			default:
				return null;
		}
	}
}
