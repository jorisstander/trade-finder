package com.tradefinder;

import static org.junit.Assert.assertEquals;
import com.tradefinder.api.Deal;
import com.tradefinder.api.Side;
import java.util.Map;
import org.junit.Test;

public class TradeNeedsTest
{
	private static final int LOBSTER = 379;

	private static Deal deal(Side mySide, long quantity, long price)
	{
		Deal deal = new Deal();
		deal.setMySide(mySide);
		deal.setItemId(LOBSTER);
		deal.setQuantity(quantity);
		deal.setPrice(price);
		return deal;
	}

	@Test
	public void buyerOwesCoinsMinusWhatIsOffered()
	{
		Deal deal = deal(Side.BUY, 3, 1_500);
		assertEquals(4_500, TradeNeeds.promptAmount(deal, Map.of(), TradeNeeds.Target.COINS));
		assertEquals(3_500, TradeNeeds.promptAmount(deal, Map.of(TradeItems.COINS, 1_000L), TradeNeeds.Target.COINS));
		assertEquals(0, TradeNeeds.promptAmount(deal, Map.of(TradeItems.COINS, 4_500L), TradeNeeds.Target.COINS));
		assertEquals(0, TradeNeeds.promptAmount(deal, Map.of(TradeItems.COINS, 9_000L), TradeNeeds.Target.COINS));
	}

	@Test
	public void tokensAreMissingCoinsOverAThousandRoundedUp()
	{
		Deal deal = deal(Side.BUY, 1, 4_500);
		assertEquals(5, TradeNeeds.promptAmount(deal, Map.of(), TradeNeeds.Target.TOKENS));
		assertEquals(4, TradeNeeds.promptAmount(deal, Map.of(TradeItems.COINS, 500L), TradeNeeds.Target.TOKENS));
		assertEquals(0, TradeNeeds.promptAmount(deal, Map.of(TradeItems.COINS, 4_500L), TradeNeeds.Target.TOKENS));
	}

	@Test
	public void sellerOwesItemsMinusWhatIsOffered()
	{
		Deal deal = deal(Side.SELL, 3, 1_500);
		assertEquals(3, TradeNeeds.promptAmount(deal, Map.of(), TradeNeeds.Target.ITEM));
		assertEquals(1, TradeNeeds.promptAmount(deal, Map.of(LOBSTER, 2L), TradeNeeds.Target.ITEM));
		assertEquals(0, TradeNeeds.promptAmount(deal, Map.of(LOBSTER, 3L), TradeNeeds.Target.ITEM));
	}

	@Test
	public void noLineForWhatThePlayerDoesNotOwe()
	{
		assertEquals(0, TradeNeeds.promptAmount(deal(Side.SELL, 3, 1_500), Map.of(), TradeNeeds.Target.COINS));
		assertEquals(0, TradeNeeds.promptAmount(deal(Side.SELL, 3, 1_500), Map.of(), TradeNeeds.Target.TOKENS));
		assertEquals(0, TradeNeeds.promptAmount(deal(Side.BUY, 3, 1_500), Map.of(), TradeNeeds.Target.ITEM));
		assertEquals(0, TradeNeeds.promptAmount(deal(Side.BUY, 3, 1_500), Map.of(), TradeNeeds.Target.OTHER));
	}

	@Test
	public void amountsThePromptCannotTakeGiveNoLine()
	{
		Deal deal = deal(Side.BUY, 1, 3_000_000_000L);
		assertEquals(0, TradeNeeds.promptAmount(deal, Map.of(), TradeNeeds.Target.COINS));
		assertEquals(3_000_000, TradeNeeds.promptAmount(deal, Map.of(), TradeNeeds.Target.TOKENS));
	}

	@Test
	public void targetFromTheOfferedItem()
	{
		Deal deal = deal(Side.BUY, 3, 1_500);
		assertEquals(TradeNeeds.Target.COINS, TradeNeeds.targetOf(deal, TradeItems.COINS));
		assertEquals(TradeNeeds.Target.TOKENS, TradeNeeds.targetOf(deal, TradeItems.PLATINUM_TOKEN));
		assertEquals(TradeNeeds.Target.ITEM, TradeNeeds.targetOf(deal, LOBSTER));
		assertEquals(TradeNeeds.Target.OTHER, TradeNeeds.targetOf(deal, 1));
		assertEquals(TradeNeeds.Target.COINS, TradeNeeds.owedTarget(deal));
		assertEquals(TradeNeeds.Target.ITEM, TradeNeeds.owedTarget(deal(Side.SELL, 1, 1)));
	}

	@Test
	public void boxLines()
	{
		Deal buy = deal(Side.BUY, 3, 1_500);
		assertEquals("You offer: 4,500 coins (4,500 to go)", TradeNeeds.offerText(buy, Map.of(), id -> "Lobster"));
		assertEquals("You offer: 4,500 coins (3,500 to go)",
			TradeNeeds.offerText(buy, Map.of(TradeItems.COINS, 1_000L), id -> "Lobster"));
		assertEquals("You offer: 4,500 coins (done)",
			TradeNeeds.offerText(buy, Map.of(TradeItems.COINS, 4_500L), id -> "Lobster"));
		assertEquals("You offer: 4,500 coins (500 too much)",
			TradeNeeds.offerText(buy, Map.of(TradeItems.COINS, 5_000L), id -> "Lobster"));
		assertEquals("You get: 3 x Lobster", TradeNeeds.getText(buy, id -> "Lobster"));
		Deal sell = deal(Side.SELL, 3, 1_500);
		assertEquals("You offer: 3 x Lobster (1 to go)", TradeNeeds.offerText(sell, Map.of(LOBSTER, 2L), id -> "Lobster"));
		assertEquals("You get: 4,500 coins", TradeNeeds.getText(sell, id -> "Lobster"));
	}
}
