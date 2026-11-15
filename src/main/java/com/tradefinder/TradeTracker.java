package com.tradefinder;

import com.tradefinder.api.Deal;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/**
 * Everything the plugin watches in the game world for the active deal: which {@link Player} is
 * the partner (kept through spawn/despawn events, never by scanning the scene per tick), whether
 * the trade window with the partner is open, how both offers compare with the deal, and what the
 * inventory looked like when the window opened so an accepted trade can be reported as a diff.
 * All state lives on the client thread; the overlays read two volatile snapshots.
 */
@Slf4j
class TradeTracker
{
	/**
	 * The game stores the other player's trade offer under the same container id as the own one,
	 * with this flag (bit 15) set. This is a client convention, not a documented value, and gameval
	 * has no constant for it; it is the same flag the client uses for the other side of a trade.
	 */
	private static final int OTHER_PLAYER_CONTAINER_FLAG = 0x8000;

	/** The partner's copy of the trade offer. */
	static final int TRADE_OTHER = InventoryID.TRADEOFFER | OTHER_PLAYER_CONTAINER_FLAG;

	/**
	 * The report is taken on the first inventory change after "Accepted trade."; if none shows up
	 * (the update came first, in the same tick) the diff is taken this many ticks after the message.
	 */
	private static final int REPORT_FALLBACK_TICKS = 3;
	/** Between the first screen closing and the confirm screen loading there is no trade widget; wait this long. */
	private static final int CLOSE_DELAY_TICKS = 2;
	/** Give the title text this long to appear before falling back to the partner being in view. */
	private static final int TITLE_WAIT_TICKS = 3;

	/** Part of the first screen's status line once the partner accepted ("Other player has accepted."). */
	static final String PARTNER_ACCEPTED_TEXT = "has accepted";

	static final String ACCEPTED_MESSAGE = "Accepted trade.";

	/** What the plugin does when the trade with the partner changes state. Client thread. */
	interface Listener
	{
		void tradeOpened(Deal deal);

		void tradeDeclined(Deal deal);

		/** The check found the trade does not match the deal; once per trade window. */
		void tradeMismatch(Deal deal, TradeCheck.Result result);

		void tradeAccepted(Deal deal, TradeItems.Diff diff);

		/** The partner is in view at the meeting spot; once per deal and world (see {@link PartnerSeenGate}). */
		void partnerSeen(Deal deal);
	}

	private final Client client;
	private final ItemManager itemManager;
	private final Listener listener;
	private final PartnerSeenGate seenGate = new PartnerSeenGate();

	@Nullable
	private Deal deal;

	// Read by the overlays each frame; written on the client thread only.
	@Nullable
	private volatile Player partner;
	@Nullable
	private volatile TradeCheck.Result lastCheck;

	// Trade window state, client thread only.
	private boolean windowOpen;
	private int titlePendingSince = -1;
	private boolean accepted;
	private boolean mismatchNotified;
	private boolean partnerAcceptedSeen;
	private Map<Integer, Long> inventoryBefore = Collections.emptyMap();
	private int closeCheckTick = -1;
	private int reportTick = -1;
	/** The inventory as of its last change, and when that was: lets an accepted trade be diffed after a hop or logout. */
	private Map<Integer, Long> lastInventory = Collections.emptyMap();
	private int lastInventoryTick = -1;
	private int acceptTick = -1;

	TradeTracker(Client client, ItemManager itemManager, Listener listener)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.listener = listener;
	}

	@Nullable
	Player partner()
	{
		return partner;
	}

	@Nullable
	TradeCheck.Result lastCheck()
	{
		return lastCheck;
	}

	@Nullable
	Deal deal()
	{
		return deal;
	}

	/** The trade window with the partner is open and not yet accepted: offers can still change. Client thread. */
	boolean canOffer()
	{
		return deal != null && windowOpen && !accepted;
	}

	/** This player's current offer, normalised (tokens as coins). Client thread. */
	Map<Integer, Long> myOffer()
	{
		return normalise(client.getItemContainer(InventoryID.TRADEOFFER));
	}

	// ---- deal ----

	/** The panel's active deal changed (or ended: null). Client thread. */
	void setDeal(@Nullable Deal newDeal)
	{
		boolean same = newDeal != null && deal != null && newDeal.getId() == deal.getId();
		boolean moved = same && newDeal.getWorld() != deal.getWorld();
		deal = newDeal;
		if (moved)
		{
			// The meeting moved to another world: the partner seen on the old one is gone; see them again there.
			seenGate.reset();
			partner = null;
			findPartner();
		}
		if (same)
		{
			return;
		}
		seenGate.reset();
		partner = null;
		resetTrade();
		if (newDeal != null)
		{
			findPartner();
		}
	}

	/** Reset everything; called from {@code shutDown()}. */
	void clear()
	{
		deal = null;
		partner = null;
		resetTrade();
	}

	/**
	 * One pass over the players in view when a deal starts, so a partner who is already standing
	 * next to us is found without waiting for a spawn event. Never repeated per tick.
	 */
	private void findPartner()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		WorldView view = client.getTopLevelWorldView();
		if (view == null)
		{
			return;
		}
		for (Player player : view.players())
		{
			if (isPartner(player))
			{
				partner = player;
				return;
			}
		}
	}

	private boolean isPartner(@Nullable Player player)
	{
		Deal current = deal;
		if (current == null || player == null || player == client.getLocalPlayer() || player.getName() == null)
		{
			return false;
		}
		if (client.getWorld() != current.getWorld())
		{
			return false;
		}
		return Text.standardize(player.getName()).equals(Text.standardize(current.getPartnerRsn()));
	}

	// ---- players ----

	void onPlayerSpawned(Player player)
	{
		if (partner == null && isPartner(player))
		{
			partner = player;
			log.debug("Trade partner {} is in view", player.getName());
		}
	}

	void onPlayerDespawned(Player player)
	{
		if (partner == player)
		{
			partner = null;
		}
	}

	void onGameStateChanged(GameState state)
	{
		if (state == GameState.HOPPING || state == GameState.LOGIN_SCREEN || state == GameState.CONNECTION_LOST)
		{
			// The player list is rebuilt after a hop; spawn events find the partner again.
			partner = null;
			reportPendingAcceptedTrade();
			resetTrade();
		}
	}

	void onWorldChanged()
	{
		seenGate.reset();
		if (deal != null && client.getWorld() != deal.getWorld())
		{
			partner = null;
		}
	}

	// ---- trade window ----

	void onWidgetLoaded(int groupId)
	{
		if (groupId == InterfaceID.TRADECONFIRM && windowOpen && !accepted)
		{
			runCheck(); // the confirm screen is the last chance to decline: re-judge with its rules
			return;
		}
		if (deal == null || groupId != InterfaceID.TRADEMAIN || windowOpen)
		{
			return;
		}
		// Snapshot before anything can be offered; whether it is the partner is settled from the title.
		inventoryBefore = snapshotInventory();
		accepted = false;
		mismatchNotified = false;
		closeCheckTick = -1;
		reportTick = -1;
		titlePendingSince = client.getTickCount();
	}

	void onWidgetClosed(int groupId)
	{
		if (groupId != InterfaceID.TRADEMAIN && groupId != InterfaceID.TRADECONFIRM)
		{
			return;
		}
		if (windowOpen || titlePendingSince >= 0)
		{
			closeCheckTick = client.getTickCount() + CLOSE_DELAY_TICKS;
		}
	}

	void onChatMessage(String message)
	{
		if (windowOpen && !accepted && ACCEPTED_MESSAGE.equals(Text.removeTags(message)))
		{
			accepted = true;
			acceptTick = client.getTickCount();
			lastCheck = null;
			reportTick = client.getTickCount() + REPORT_FALLBACK_TICKS;
		}
	}

	void onItemContainerChanged(int containerId)
	{
		if (containerId == InventoryID.INV)
		{
			lastInventory = snapshotInventory();
			lastInventoryTick = client.getTickCount();
		}
		if (accepted && reportTick >= 0 && containerId == InventoryID.INV)
		{
			finishAcceptedTrade(); // the goods changed hands: this is the diff
			return;
		}
		if (windowOpen && !accepted && (containerId == InventoryID.TRADEOFFER || containerId == TRADE_OTHER))
		{
			runCheck();
		}
	}

	/** Telling the server failed (no answer, or an answer worth retrying): try again in a moment. Client thread. */
	void partnerSeenFailed(long dealId)
	{
		if (deal != null && deal.getId() == dealId)
		{
			seenGate.failed(client.getTickCount());
		}
	}

	/** One tick-cheap check: the partner in view and standing inside the Grand Exchange area. */
	private void checkPartnerSeen(int tick)
	{
		Deal current = deal;
		Player player = partner;
		if (current == null || player == null)
		{
			return;
		}
		if (seenGate.due(GrandExchangeArea.contains(player.getWorldLocation()), tick))
		{
			listener.partnerSeen(current);
		}
	}

	void onGameTick()
	{
		int tick = client.getTickCount();
		checkPartnerSeen(tick);
		if (titlePendingSince >= 0)
		{
			resolveTitle(tick);
		}
		if (windowOpen && !accepted && partnerAccepted() != partnerAcceptedSeen)
		{
			runCheck(); // accepting changes no container, so look at the status line each tick
		}
		if (reportTick >= 0 && tick >= reportTick)
		{
			finishAcceptedTrade();
			return;
		}
		if (closeCheckTick >= 0 && tick >= closeCheckTick)
		{
			closeCheckTick = -1;
			if (accepted || tradeWidgetOpen())
			{
				return; // accepted: never "declined"; otherwise moved from the first screen to the confirm screen
			}
			boolean wasOpen = windowOpen;
			Deal current = deal;
			resetTrade();
			if (wasOpen && current != null)
			{
				listener.tradeDeclined(current);
			}
		}
	}

	/**
	 * A hop, logout or lost connection came before the inventory diff was taken. If the inventory
	 * changed at or after the accept, the last known inventory is the after-state: report with it
	 * instead of losing the trade. Without a change since the accept there is nothing true to report.
	 */
	private void reportPendingAcceptedTrade()
	{
		Deal current = deal;
		if (!accepted || reportTick < 0 || current == null || lastInventoryTick < acceptTick)
		{
			return;
		}
		listener.tradeAccepted(current, TradeItems.diff(inventoryBefore, lastInventory));
	}

	/** Forget that the partner was reported as seen (the session dropped or a new one started): tell the server again. */
	void resetPartnerSeen()
	{
		seenGate.reset();
	}

	/** "Accepted trade." was seen: diff the inventory against the snapshot and hand it to the listener. */
	private void finishAcceptedTrade()
	{
		Deal current = deal;
		TradeItems.Diff diff = TradeItems.diff(inventoryBefore, snapshotInventory());
		resetTrade();
		if (current != null)
		{
			listener.tradeAccepted(current, diff);
		}
	}

	/** The first trade screen loaded: is it the partner? Read from the title once its text is in. */
	private void resolveTitle(int tick)
	{
		Widget title = client.getWidget(InterfaceID.Trademain.TITLE);
		if (title == null)
		{
			titlePendingSince = -1; // closed again before we looked
			return;
		}
		String text = title.getText();
		boolean withPartner;
		if (text != null && !text.isEmpty())
		{
			Deal current = deal;
			withPartner = current != null && TradeTitle.isWith(text, current.getPartnerRsn());
		}
		else if (tick - titlePendingSince < TITLE_WAIT_TICKS)
		{
			return; // the title is filled in by a script shortly after load
		}
		else
		{
			// Unreadable title: do not guess. A stranger trading next to the partner must not be reported for the deal.
			withPartner = false;
			log.debug("Trade title not readable; treating the window as not the partner's");
		}
		titlePendingSince = -1;
		if (!withPartner)
		{
			inventoryBefore = Collections.emptyMap();
			closeCheckTick = -1;
			return;
		}
		windowOpen = true;
		Deal current = deal;
		if (current != null)
		{
			listener.tradeOpened(current);
		}
		runCheck();
	}

	/** The first trade screen's status line says "Other player has accepted." once the partner accepted. */
	private boolean partnerAccepted()
	{
		Widget status = client.getWidget(InterfaceID.Trademain.STATUS);
		if (status == null || status.isHidden())
		{
			return false;
		}
		String text = status.getText();
		return text != null && Text.removeTags(text).toLowerCase().contains(PARTNER_ACCEPTED_TEXT);
	}

	private boolean tradeWidgetOpen()
	{
		return client.getWidget(InterfaceID.Trademain.UNIVERSE) != null
			|| client.getWidget(InterfaceID.Tradeconfirm.UNIVERSE) != null;
	}

	private void resetTrade()
	{
		windowOpen = false;
		titlePendingSince = -1;
		accepted = false;
		mismatchNotified = false;
		partnerAcceptedSeen = false;
		inventoryBefore = Collections.emptyMap();
		closeCheckTick = -1;
		reportTick = -1;
		acceptTick = -1;
		lastCheck = null;
	}

	/** Compare both offers with the deal; runs on container changes, never per frame. */
	private void runCheck()
	{
		Deal current = deal;
		if (current == null)
		{
			lastCheck = null;
			return;
		}
		Map<Integer, Long> mine = normalise(client.getItemContainer(InventoryID.TRADEOFFER));
		Map<Integer, Long> theirs = normalise(client.getItemContainer(TRADE_OTHER));
		boolean confirm = client.getWidget(InterfaceID.Tradeconfirm.UNIVERSE) != null;
		partnerAcceptedSeen = partnerAccepted();
		TradeCheck.Result result = TradeCheck.check(current, mine, theirs,
			id -> itemManager.getItemComposition(id).getName(), confirm, partnerAcceptedSeen);
		lastCheck = result;
		boolean notify = TradeAlert.shouldNotify(mismatchNotified, result);
		mismatchNotified = TradeAlert.nextLatch(mismatchNotified, result);
		if (notify)
		{
			listener.tradeMismatch(current, result);
		}
	}

	private Map<Integer, Long> snapshotInventory()
	{
		return normalise(client.getItemContainer(InventoryID.INV));
	}

	private Map<Integer, Long> normalise(@Nullable ItemContainer container)
	{
		return TradeItems.normalise(container == null ? null : container.getItems(), itemManager::canonicalize);
	}
}
