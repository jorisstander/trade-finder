package com.tradefinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Map;
import java.util.TreeMap;
import net.runelite.api.Item;
import org.junit.Test;

public class TradeItemsTest
{
	private static final int WHIP = 4151;
	private static final int WHIP_NOTED = 4152;

	private static Map<Integer, Long> map(Object... pairs)
	{
		Map<Integer, Long> map = new TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			map.put((Integer) pairs[i], ((Number) pairs[i + 1]).longValue());
		}
		return map;
	}

	@Test
	public void normaliseMergesStacksAndCountsTokensAsCoins()
	{
		Item[] items = {
			new Item(TradeItems.COINS, 500),
			new Item(-1, 0),
			new Item(TradeItems.PLATINUM_TOKEN, 3_000_000),
			new Item(WHIP, 1),
			new Item(WHIP, 2),
			new Item(TradeItems.COINS, 2_147_483_647),
		};
		Map<Integer, Long> result = TradeItems.normalise(items, id -> id);
		assertEquals(map(TradeItems.COINS, 3_000_000_000L + 500 + 2_147_483_647L, WHIP, 3), result);
		assertTrue(result.get(TradeItems.COINS) > Integer.MAX_VALUE);
	}

	@Test
	public void normaliseCanonicalisesNotedItems()
	{
		Item[] items = {new Item(WHIP_NOTED, 5), new Item(WHIP, 1)};
		assertEquals(map(WHIP, 6), TradeItems.normalise(items, id -> id == WHIP_NOTED ? WHIP : id));
		assertEquals(map(), TradeItems.normalise(null, id -> id));
	}

	@Test
	public void diffSplitsDecreasesAndIncreases()
	{
		Map<Integer, Long> before = map(WHIP, 3, TradeItems.COINS, 100, 1234, 1);
		Map<Integer, Long> after = map(WHIP, 1, TradeItems.COINS, 700_100, 1234, 1, 5678, 2);
		TradeItems.Diff diff = TradeItems.diff(before, after);
		assertEquals(map(WHIP, 2), diff.getGave());
		assertEquals(map(TradeItems.COINS, 700_000, 5678, 2), diff.getReceived());
		assertTrue(TradeItems.diff(before, before).isEmpty());
	}

	@Test
	public void reportKeepsIdsAndQuantities()
	{
		assertEquals(2, TradeItems.toReport(map(WHIP, 1, TradeItems.COINS, 5)).size());
		assertEquals(WHIP, TradeItems.toReport(map(WHIP, 1)).get(0).getItemId());
		assertEquals(1L, TradeItems.toReport(map(WHIP, 1)).get(0).getQuantity());
	}
}
