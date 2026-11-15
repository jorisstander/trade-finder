package com.tradefinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.tradefinder.api.Deal;
import com.tradefinder.api.Side;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;

public class TradeCheckTest
{
	private static final int WHIP = 4151;

	private static Deal deal(Side mySide, long quantity, long price)
	{
		Deal deal = new Deal();
		deal.setId(1);
		deal.setItemId(WHIP);
		deal.setQuantity(quantity);
		deal.setPrice(price);
		deal.setMySide(mySide);
		deal.setPartnerRsn("Bea");
		return deal;
	}

	private static Map<Integer, Long> map(Object... pairs)
	{
		Map<Integer, Long> map = new TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			map.put((Integer) pairs[i], ((Number) pairs[i + 1]).longValue());
		}
		return map;
	}

	private static String name(int id)
	{
		return id == WHIP ? "Abyssal whip" : "item " + id;
	}

	@Test
	public void sellerSideMatchesWhenBothOffersAreExact()
	{
		Deal deal = deal(Side.SELL, 2, 700_000);
		TradeCheck.Result result = TradeCheck.check(deal, map(WHIP, 2), map(TradeItems.COINS, 1_400_000), TradeCheckTest::name);
		assertTrue(result.matches());
		assertEquals("2 x Abyssal whip", result.getMine().getOffered());
		assertEquals("1,400,000 coins", result.getTheirs().getOffered());
		assertEquals("Bea", result.getTheirs().getWho());
	}

	@Test
	public void buyerSideAcceptsTokensAsCoinsAboveMaxCash()
	{
		Deal deal = deal(Side.BUY, 1, 3_000_000_000L);
		// Tokens are already folded into coins by TradeItems.normalise.
		TradeCheck.Result result = TradeCheck.check(deal, map(TradeItems.COINS, 3_000_000_000L), map(WHIP, 1), TradeCheckTest::name);
		assertTrue(result.matches());
		assertEquals(map(TradeItems.COINS, 3_000_000_000L), TradeCheck.expectedOffer(deal, Side.BUY));
		assertEquals(map(WHIP, 1), TradeCheck.expectedOffer(deal, Side.SELL));
	}

	@Test
	public void reportsWrongAmountsExtrasAndEmptyOffers()
	{
		Deal deal = deal(Side.SELL, 2, 700_000);
		TradeCheck.Result result = TradeCheck.check(deal, map(WHIP, 1, 1234, 3), map(), TradeCheckTest::name);
		assertFalse(result.matches());
		assertEquals(2, result.getMine().getProblems().size());
		assertEquals("You: 1 x Abyssal whip offered, deal is 2 x Abyssal whip", result.getMine().getProblems().get(0));
		assertEquals("You: also offers 3 x item 1234", result.getMine().getProblems().get(1));
		assertEquals("Bea: nothing offered yet", result.getTheirs().getProblems().get(0));

		result = TradeCheck.check(deal, map(WHIP, 2), map(TradeItems.COINS, 650_000), TradeCheckTest::name);
		assertTrue(result.getMine().matches());
		assertEquals("Bea: 650,000 coins offered, deal is 1,400,000 coins", result.getTheirs().getProblems().get(0));

		result = TradeCheck.check(deal, map(1234, 1), map(TradeItems.COINS, 1_400_000), TradeCheckTest::name);
		assertEquals("You: no 2 x Abyssal whip offered", result.getMine().getProblems().get(0));
	}

	@Test
	public void emptyOffersAreNotAMismatch()
	{
		Deal deal = deal(Side.SELL, 1, 100);
		TradeCheck.Result result = TradeCheck.check(deal, map(), map(), TradeCheckTest::name);
		assertFalse(result.matches());
		assertFalse(result.mismatch());
		assertFalse(TradeAlert.isAlert(result));
		assertTrue(TradeAlert.warningLines(result).isEmpty());
	}

	@Test
	public void wrongOfferIsAMismatchWithWarningLines()
	{
		Deal deal = deal(Side.SELL, 1, 100);
		TradeCheck.Result result = TradeCheck.check(deal, map(WHIP, 1), map(TradeItems.COINS, 99), TradeCheckTest::name);
		assertTrue(TradeAlert.isAlert(result));
		java.util.List<String> lines = TradeAlert.warningLines(result);
		assertEquals(TradeAlert.HEADLINE, lines.get(0));
		assertEquals(2, lines.size());
		assertTrue(lines.get(1).startsWith("Bea"));
		assertTrue(TradeAlert.notificationText(result).contains("does not match"));
	}

	@Test
	public void notifiesOnlyOncePerWindow()
	{
		Deal deal = deal(Side.SELL, 1, 100);
		TradeCheck.Result bad = TradeCheck.check(deal, map(WHIP, 1), map(TradeItems.COINS, 99), TradeCheckTest::name);
		TradeCheck.Result good = TradeCheck.check(deal, map(WHIP, 1), map(TradeItems.COINS, 100), TradeCheckTest::name);
		assertTrue(TradeAlert.shouldNotify(false, bad));
		assertFalse(TradeAlert.shouldNotify(true, bad));
		assertFalse(TradeAlert.shouldNotify(false, good));
		assertFalse(TradeAlert.shouldNotify(false, null));
	}

	@Test
	public void pulseStaysBetweenZeroAndOne()
	{
		for (long t = 0; t < 2000; t += 37)
		{
			float p = TradeAlert.pulse(t);
			assertTrue(p >= 0f && p <= 1f);
		}
	}

	@Test
	public void emptyPartnerIsNoAlertOnTheFirstScreenUntilTheyHaveAccepted()
	{
		Deal deal = deal(Side.SELL, 1, 100);
		TradeCheck.Result waiting = TradeCheck.check(deal, map(WHIP, 1), map(), TradeCheckTest::name, false, false);
		assertFalse(TradeAlert.isAlert(waiting));
		assertFalse(TradeAlert.shouldNotify(false, waiting));
		assertTrue(TradeAlert.warningLines(waiting).isEmpty());

		TradeCheck.Result accepted = TradeCheck.check(deal, map(WHIP, 1), map(), TradeCheckTest::name, false, true);
		assertTrue(TradeAlert.isAlert(accepted));
		assertTrue(TradeAlert.shouldNotify(false, accepted));
		assertEquals(TradeAlert.HEADLINE, TradeAlert.warningLines(accepted).get(0));
		assertTrue(TradeAlert.warningLines(accepted).get(1).contains("nothing offered"));
	}

	@Test
	public void emptyPartnerIsAnAlertOnTheConfirmScreen()
	{
		Deal deal = deal(Side.SELL, 1, 100);
		TradeCheck.Result confirm = TradeCheck.check(deal, map(WHIP, 1), map(), TradeCheckTest::name, true);
		assertTrue(TradeAlert.isAlert(confirm));
		assertTrue(TradeAlert.shouldNotify(false, confirm));
	}

	@Test
	public void bothEmptyIsAnAlertOnlyOnceCommitted()
	{
		Deal deal = deal(Side.SELL, 1, 100);
		assertFalse(TradeAlert.isAlert(TradeCheck.check(deal, map(), map(), TradeCheckTest::name, false, false)));
		assertTrue(TradeAlert.isAlert(TradeCheck.check(deal, map(), map(), TradeCheckTest::name, true, false)));
		assertTrue(TradeAlert.isAlert(TradeCheck.check(deal, map(), map(), TradeCheckTest::name, false, true)));
	}

	@Test
	public void latchRearmsWhenTheCheckMatchesAgain()
	{
		Deal deal = deal(Side.SELL, 1, 100);
		TradeCheck.Result bad = TradeCheck.check(deal, map(WHIP, 1), map(TradeItems.COINS, 99), TradeCheckTest::name);
		TradeCheck.Result good = TradeCheck.check(deal, map(WHIP, 1), map(TradeItems.COINS, 100), TradeCheckTest::name);
		boolean latch = false;
		assertTrue(TradeAlert.shouldNotify(latch, bad));
		latch = TradeAlert.nextLatch(latch, bad);
		assertTrue(latch);
		assertFalse(TradeAlert.shouldNotify(latch, bad));
		latch = TradeAlert.nextLatch(latch, good);
		assertFalse(latch);
		assertTrue(TradeAlert.shouldNotify(latch, bad));
	}
}
