package com.tradefinder;

import net.runelite.client.util.Text;

/** Reads the trade window's title ("Trading with: Name") and decides whether it names the deal partner. Pure. */
final class TradeTitle
{
	static final String MARKER = "trading with:";

	private TradeTitle()
	{
	}

	/**
	 * True when the name after "Trading with:" is exactly the partner's name (after
	 * {@link Text#standardize}, so non-breaking spaces and case do not matter). "Bob" is not "xBobx".
	 * A title without the marker counts only when the whole text is the partner's name.
	 */
	static boolean isWith(String title, String partnerRsn)
	{
		if (title == null || partnerRsn == null)
		{
			return false;
		}
		String shown = Text.standardize(Text.removeTags(title));
		String name = shown;
		int at = shown.indexOf(MARKER);
		if (at >= 0)
		{
			name = shown.substring(at + MARKER.length()).trim();
		}
		return !name.isEmpty() && name.equals(Text.standardize(partnerRsn));
	}
}
