package com.tradefinder;

import com.tradefinder.api.Deal;
import com.tradefinder.api.Side;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * What this player still has to offer for the deal and what they must get, and the amount the
 * "Enter amount" prompt can be filled with. Pure arithmetic on a normalised offer (Platinum tokens
 * already counted as coins), so it is tested without the client.
 */
final class TradeNeeds
{
	private TradeNeeds()
	{
	}

	/** What the amount prompt is for. */
	enum Target
	{
		COINS, TOKENS, ITEM, OTHER
	}

	/** Which of the three the offered item id is: coins, Platinum tokens, the deal item, or something else. */
	static Target targetOf(Deal deal, int itemId)
	{
		if (itemId == TradeItems.COINS)
		{
			return Target.COINS;
		}
		if (itemId == TradeItems.PLATINUM_TOKEN)
		{
			return Target.TOKENS;
		}
		return itemId == deal.getItemId() ? Target.ITEM : Target.OTHER;
	}

	/** When the item behind the prompt is unknown: the thing this player owes (buyer coins, seller the item). */
	static Target owedTarget(Deal deal)
	{
		return deal.getMySide() == Side.BUY ? Target.COINS : Target.ITEM;
	}

	/** Coins the buyer still has to offer (tokens count as coins); 0 for a seller or when enough is offered. */
	static long missingCoins(Deal deal, Map<Integer, Long> mine)
	{
		if (deal.getMySide() != Side.BUY)
		{
			return 0;
		}
		return Math.max(0, deal.getPrice() * deal.getQuantity() - mine.getOrDefault(TradeItems.COINS, 0L));
	}

	/** Items the seller still has to offer; 0 for a buyer or when enough is offered. */
	static long missingItems(Deal deal, Map<Integer, Long> mine)
	{
		if (deal.getMySide() != Side.SELL)
		{
			return 0;
		}
		return Math.max(0, deal.getQuantity() - mine.getOrDefault(deal.getItemId(), 0L));
	}

	/**
	 * The number to put into the prompt for {@code target}, or 0 for "no line": coins as missing,
	 * tokens as the missing coins divided by 1,000 rounded up, the deal item as missing items. A
	 * number the prompt cannot take (above 2,147,483,647) gives 0.
	 */
	static long promptAmount(Deal deal, Map<Integer, Long> mine, Target target)
	{
		long amount;
		switch (target)
		{
			case COINS:
				amount = missingCoins(deal, mine);
				break;
			case TOKENS:
				amount = (missingCoins(deal, mine) + TradeItems.PLATINUM_TOKEN_VALUE - 1) / TradeItems.PLATINUM_TOKEN_VALUE;
				break;
			case ITEM:
				amount = missingItems(deal, mine);
				break;
			default:
				amount = 0;
		}
		return amount > CoinAmounts.MAX_STACK ? 0 : amount;
	}

	/** "You offer: 4,500 coins (4,500 to go)": what this player has to put into the trade. */
	static String offerText(Deal deal, Map<Integer, Long> mine, IntFunction<String> itemName)
	{
		boolean buying = deal.getMySide() == Side.BUY;
		int id = buying ? TradeItems.COINS : deal.getItemId();
		long need = buying ? deal.getPrice() * deal.getQuantity() : deal.getQuantity();
		long have = mine.getOrDefault(id, 0L);
		String progress = have == need ? "done" : have < need ? CoinAmounts.format(need - have) + " to go"
			: CoinAmounts.format(have - need) + " too much";
		return "You offer: " + TradeCheck.stack(id, need, itemName) + " (" + progress + ")";
	}

	/** "You get: 3 x Lobster" or "You get: 4,500 coins". */
	static String getText(Deal deal, IntFunction<String> itemName)
	{
		boolean buying = deal.getMySide() == Side.BUY;
		return "You get: " + (buying ? TradeCheck.stack(deal.getItemId(), deal.getQuantity(), itemName)
			: TradeCheck.stack(TradeItems.COINS, deal.getPrice() * deal.getQuantity(), itemName));
	}
}
