package com.tradefinder;

import com.tradefinder.api.TradedItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;
import lombok.Value;
import net.runelite.api.Item;
import net.runelite.api.gameval.ItemID;

/**
 * Pure helpers for item stacks on the trade screens and in the inventory. Stacks are merged by
 * item id, Platinum tokens are counted as coins (1 token = 1,000 coins) and amounts are long, so a
 * trade above 2,147,483,647 coins compares the same way whether it was paid in coins, tokens or both.
 */
final class TradeItems
{
	static final int COINS = ItemID.COINS;
	static final int PLATINUM_TOKEN = ItemID.PLATINUM;
	static final long PLATINUM_TOKEN_VALUE = 1_000L;

	private TradeItems()
	{
	}

	/** What changed hands from one player's point of view, both maps normalised like {@link #normalise}. */
	@Value
	static class Diff
	{
		Map<Integer, Long> gave;
		Map<Integer, Long> received;

		boolean isEmpty()
		{
			return gave.isEmpty() && received.isEmpty();
		}
	}

	/**
	 * Merge stacks by item id, with Platinum tokens folded into coins. {@code canonical} maps noted
	 * and placeholder ids to the base item (the plugin passes {@code ItemManager::canonicalize}), so
	 * a seller may hand over the item noted or unnoted. Empty slots are skipped.
	 */
	static Map<Integer, Long> normalise(Item[] items, IntUnaryOperator canonical)
	{
		Map<Integer, Long> totals = new TreeMap<>();
		if (items == null)
		{
			return totals;
		}
		for (Item item : items)
		{
			if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			int id = canonical.applyAsInt(item.getId());
			long quantity = item.getQuantity();
			if (id == PLATINUM_TOKEN)
			{
				id = COINS;
				quantity *= PLATINUM_TOKEN_VALUE;
			}
			totals.merge(id, quantity, Long::sum);
		}
		return totals;
	}

	/** Items whose count went down ({@code gave}) or up ({@code received}) between two normalised snapshots. */
	static Diff diff(Map<Integer, Long> before, Map<Integer, Long> after)
	{
		Map<Integer, Long> gave = new TreeMap<>();
		Map<Integer, Long> received = new TreeMap<>();
		for (Map.Entry<Integer, Long> entry : before.entrySet())
		{
			long now = after.getOrDefault(entry.getKey(), 0L);
			if (now < entry.getValue())
			{
				gave.put(entry.getKey(), entry.getValue() - now);
			}
		}
		for (Map.Entry<Integer, Long> entry : after.entrySet())
		{
			long was = before.getOrDefault(entry.getKey(), 0L);
			if (entry.getValue() > was)
			{
				received.put(entry.getKey(), entry.getValue() - was);
			}
		}
		return new Diff(gave, received);
	}

	/** A normalised map as the backend's report items; coins stay coins, the backend normalises the same way. */
	static List<TradedItem> toReport(Map<Integer, Long> items)
	{
		List<TradedItem> list = new ArrayList<>(items.size());
		for (Map.Entry<Integer, Long> entry : items.entrySet())
		{
			list.add(new TradedItem(entry.getKey(), entry.getValue()));
		}
		return list;
	}
}
