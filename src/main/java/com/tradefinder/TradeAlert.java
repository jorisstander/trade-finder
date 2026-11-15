package com.tradefinder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure decisions for the loud "this trade does not match the deal" state: when it is on, what the
 * warning says, and the once-per-trade-window notification latch. The overlay and the tracker only
 * apply these answers.
 */
final class TradeAlert
{
	static final String HEADLINE = "TRADE DOES NOT MATCH THE DEAL";

	private TradeAlert()
	{
	}

	/**
	 * True when the warning must be shown: a side offers something that differs from the deal, or a
	 * side is still empty at a point where both players have committed: on the confirm screen, or on
	 * the first screen once the partner has accepted. An empty side while the partner has not accepted
	 * is how every honest trade starts and is not an alert.
	 */
	static boolean isAlert(TradeCheck.Result result)
	{
		if (result == null)
		{
			return false;
		}
		if (result.mismatch())
		{
			return true;
		}
		boolean anyEmpty = result.getMine().isNothingOffered() || result.getTheirs().isNothingOffered();
		return anyEmpty && (result.isConfirmScreen() || result.isPartnerAccepted());
	}

	/** Notify the first time a trade window turns red, and again after it matched in between. */
	static boolean shouldNotify(boolean alreadyNotified, TradeCheck.Result result)
	{
		return !alreadyNotified && isAlert(result);
	}

	/** The latch after this check: set by an alert, re-armed once the check matches again. */
	static boolean nextLatch(boolean alreadyNotified, TradeCheck.Result result)
	{
		if (isAlert(result))
		{
			return true;
		}
		if (result != null && result.matches())
		{
			return false;
		}
		return alreadyNotified;
	}

	/** Headline first, then what differs (only the sides that offer something wrong). Empty when no alert. */
	static List<String> warningLines(TradeCheck.Result result)
	{
		if (!isAlert(result))
		{
			return Collections.emptyList();
		}
		List<String> lines = new ArrayList<>();
		lines.add(HEADLINE);
		lines.addAll(result.getMine().getProblems());
		lines.addAll(result.getTheirs().getProblems());
		return lines;
	}

	/** One line for the notification: the first thing that differs. */
	static String notificationText(TradeCheck.Result result)
	{
		List<String> lines = warningLines(result);
		if (lines.size() < 2)
		{
			return "Trade Finder: the trade does not match the deal";
		}
		return "Trade Finder: the trade does not match the deal. " + lines.get(1);
	}


	/** Pulse phase 0..1 for the border, from the clock; a pure function so it can be tested. */
	static float pulse(long millis)
	{
		double phase = (millis % 1000L) / 1000.0;
		return (float) (0.5 - 0.5 * Math.cos(phase * 2 * Math.PI));
	}
}
