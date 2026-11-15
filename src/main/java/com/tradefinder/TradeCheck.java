package com.tradefinder;

import com.tradefinder.api.Deal;
import com.tradefinder.api.Side;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntFunction;
import lombok.Value;

/**
 * Pure comparison of the two trade offers with the deal: the seller offers exactly the deal's
 * quantity of the item, the buyer offers coins (and Platinum tokens, counted as coins) worth exactly
 * price x quantity. Offers arrive normalised by {@link TradeItems#normalise}.
 */
final class TradeCheck
{
	private TradeCheck()
	{
	}

	/** One line of the overlay: a side's offer, with the problems found (empty when it matches). */
	@Value
	static class SideResult
	{
		/** "You" or the partner's RSN. */
		String who;
		/** The offer in words, e.g. "1 x Abyssal whip" or "nothing yet". */
		String offered;
		List<String> problems;
		/** True while the side has put nothing into the trade yet (not a mismatch, just not ready). */
		boolean nothingOffered;

		boolean matches()
		{
			return problems.isEmpty();
		}

		/** The side offers something and it is not what the deal says. */
		boolean wrong()
		{
			return !nothingOffered && !problems.isEmpty();
		}
	}

	/** The last comparison; the overlay only renders this. */
	@Value
	static class Result
	{
		SideResult mine;
		SideResult theirs;
		/** True while the confirm screen is open: both offers are then final. */
		boolean confirmScreen;
		/** The first screen's status line says the partner has already accepted. */
		boolean partnerAccepted;
		/** "You offer: 4,500 coins (4,500 to go)": what this player still has to put in. */
		String youOffer;
		/** "You get: 3 x Lobster": what this player must receive. */
		String youGet;

		boolean matches()
		{
			return mine.matches() && theirs.matches();
		}

		/** Either side offers something that differs from the deal (an empty offer is not a mismatch). */
		boolean mismatch()
		{
			return mine.wrong() || theirs.wrong();
		}
	}

	/** What a side must offer: the seller the item, the buyer the coins. */
	static Map<Integer, Long> expectedOffer(Deal deal, Side side)
	{
		Map<Integer, Long> expected = new TreeMap<>();
		if (side == Side.SELL)
		{
			expected.put(deal.getItemId(), deal.getQuantity());
		}
		else
		{
			expected.put(TradeItems.COINS, deal.getPrice() * deal.getQuantity());
		}
		return expected;
	}

	/**
	 * Compare both offers with the deal.
	 *
	 * @param mine     my normalised offer
	 * @param theirs   the partner's normalised offer
	 * @param itemName item id to name, for the messages
	 */
	static Result check(Deal deal, Map<Integer, Long> mine, Map<Integer, Long> theirs, IntFunction<String> itemName)
	{
		return check(deal, mine, theirs, itemName, false, false);
	}

	static Result check(Deal deal, Map<Integer, Long> mine, Map<Integer, Long> theirs, IntFunction<String> itemName,
		boolean confirmScreen)
	{
		return check(deal, mine, theirs, itemName, confirmScreen, false);
	}

	/**
	 * As above; {@code confirmScreen} says the confirm screen (the last chance to decline) is open,
	 * {@code partnerAccepted} that the partner has accepted the first screen.
	 */
	static Result check(Deal deal, Map<Integer, Long> mine, Map<Integer, Long> theirs, IntFunction<String> itemName,
		boolean confirmScreen, boolean partnerAccepted)
	{
		Side mySide = deal.getMySide();
		Side theirSide = mySide == Side.SELL ? Side.BUY : Side.SELL;
		return new Result(
			checkSide("You", mine, expectedOffer(deal, mySide), itemName),
			checkSide(deal.getPartnerRsn(), theirs, expectedOffer(deal, theirSide), itemName),
			confirmScreen, partnerAccepted, TradeNeeds.offerText(deal, mine, itemName), TradeNeeds.getText(deal, itemName));
	}

	private static SideResult checkSide(String who, Map<Integer, Long> offered, Map<Integer, Long> expected,
		IntFunction<String> itemName)
	{
		List<String> problems = new ArrayList<>();
		if (offered.isEmpty())
		{
			problems.add(who + ": nothing offered yet");
			return new SideResult(who, "nothing yet", Collections.unmodifiableList(problems), true);
		}
		for (Map.Entry<Integer, Long> want : expected.entrySet())
		{
			long have = offered.getOrDefault(want.getKey(), 0L);
			if (have == want.getValue())
			{
				continue;
			}
			String name = stack(want.getKey(), want.getValue(), itemName);
			if (have == 0)
			{
				problems.add(who + ": no " + name + " offered");
			}
			else
			{
				problems.add(who + ": " + stack(want.getKey(), have, itemName) + " offered, deal is " + name);
			}
		}
		for (Map.Entry<Integer, Long> extra : offered.entrySet())
		{
			if (!expected.containsKey(extra.getKey()))
			{
				problems.add(who + ": also offers " + stack(extra.getKey(), extra.getValue(), itemName));
			}
		}
		return new SideResult(who, describe(offered, itemName), Collections.unmodifiableList(problems), false);
	}

	/** "700,000 coins" or "3 x Abyssal whip". */
	static String stack(int itemId, long quantity, IntFunction<String> itemName)
	{
		if (itemId == TradeItems.COINS)
		{
			return CoinAmounts.format(quantity) + " coins";
		}
		return CoinAmounts.format(quantity) + " x " + itemName.apply(itemId);
	}

	private static String describe(Map<Integer, Long> offered, IntFunction<String> itemName)
	{
		StringBuilder text = new StringBuilder();
		for (Map.Entry<Integer, Long> entry : offered.entrySet())
		{
			if (text.length() > 0)
			{
				text.append(", ");
			}
			text.append(stack(entry.getKey(), entry.getValue(), itemName));
		}
		return text.toString();
	}
}
