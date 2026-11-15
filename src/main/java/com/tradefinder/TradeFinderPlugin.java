package com.tradefinder;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.inject.Provides;
import com.tradefinder.api.ApiCallback;
import com.tradefinder.api.Deal;
import com.tradefinder.api.OrderRequest;
import com.tradefinder.api.PostOrderResponse;
import com.tradefinder.api.RealtimeEvent;
import com.tradefinder.api.RealtimeListener;
import com.tradefinder.api.SessionResponse;
import com.tradefinder.api.TradeFinderApi;
import com.tradefinder.api.TradeFinderSocket;
import com.tradefinder.api.TradeReport;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.WorldChanged;
import net.runelite.client.game.WorldService;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

/**
 * Trade Finder: matches buy/sell orders with other players so they can trade face to face.
 * The plugin never sends actions to the game server; it reads client state and draws overlays.
 */
@Slf4j
@PluginDescriptor(
	name = "Trade Finder",
	description = "Match buy and sell orders with other players and trade face to face, skipping the GE tax",
	tags = {"trade", "grand exchange", "ge", "tax"}
)
public class TradeFinderPlugin extends Plugin
{
	/** After a failed registration (or a rejected token), wait this long before the next attempt. */
	private static final Duration REGISTER_RETRY = Duration.ofSeconds(30);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Notifier notifier;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private TradeFinderConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ModelOutlineRenderer modelOutlineRenderer;

	@Inject
	private WorldService worldService;

	@Inject
	private ConfigManager configManager;

	@Inject
	@Named("developerMode")
	private boolean developerMode;

	// Hop button state, client thread only: the world to hop to once the world switcher is open.
	@Nullable
	private net.runelite.api.World pendingHop;
	private int hopAttempts;
	private static final int HOP_ATTEMPTS = 3;

	private TradeFinderApi api;
	private TradeFinderSocket socket;
	private TradeFinderPanel panel;
	private NavigationButton navButton;
	private TradeTracker tracker;
	private AmountPrompt amountPrompt;
	private PartnerHighlightOverlay partnerOverlay;
	private TradeCheckOverlay tradeCheckOverlay;

	// Session state, touched on the client thread only (GameTick / GameStateChanged).
	private boolean registering;
	private String registeredRsn;
	/** Last membership state pushed to the panel (client thread). */
	private boolean memberShown;
	/** Last near-GE flag pushed to the panel (client thread). */
	private boolean nearGeShown;
	private volatile long registeredAccountHash;
	private Instant retryAfter = Instant.MIN;
	/**
	 * Bumped whenever the session is dropped (logout, server off, rejected token, shutdown). A
	 * {@code POST /session} reply from an older generation is ignored.
	 */
	private final AtomicInteger sessionGeneration = new AtomicInteger();

	/** The accepted trade's report until the server has answered it; survives hops, logouts and lost connections. */
	private final ReportOutbox reportOutbox = new ReportOutbox();
	@Nullable
	private volatile ScheduledFuture<?> reportRetry;

	// Swing thread only: the deal whose "partner nearby" notification already fired.
	private long nearbyNotifiedDealId;
	/** The deal and time of the player's last World full click (Swing thread); its own push is not announced. */
	private long worldFullDealId;
	private Instant worldFullClickedAt = Instant.MIN;
	private static final Duration OWN_CLICK_WINDOW = Duration.ofSeconds(10);

	@Override
	protected void startUp()
	{
		// The server address used to be a setting; drop a value an older build saved.
		configManager.unsetConfiguration(TradeFinderConfig.GROUP, TradeFinderConfig.LEGACY_BACKEND_URL_KEY);
		api = new TradeFinderApi(okHttpClient, gson, this::backendUrl);
		socket = new TradeFinderSocket(okHttpClient, api, executor, new SocketListener());
		tracker = new TradeTracker(client, itemManager, new TradeListener());
		amountPrompt = new AmountPrompt(client, itemManager, tracker);
		partnerOverlay = new PartnerHighlightOverlay(this, tracker, modelOutlineRenderer);
		tradeCheckOverlay = new TradeCheckOverlay(this, client, tracker);
		overlayManager.add(partnerOverlay);
		overlayManager.add(tradeCheckOverlay);
		panel = new TradeFinderPanel(this, itemManager, executor, config.backendEnabled());

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Trade Finder")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		log.info("Trade Finder started");
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		if (partnerOverlay != null)
		{
			overlayManager.remove(partnerOverlay);
			partnerOverlay = null;
		}
		if (tradeCheckOverlay != null)
		{
			overlayManager.remove(tradeCheckOverlay);
			tradeCheckOverlay = null;
		}
		TradeTracker currentTracker = tracker;
		AmountPrompt prompt = amountPrompt;
		tracker = null;
		amountPrompt = null;
		// A hop is only ever made for a fresh click: nothing pending survives a shutdown.
		clientThread.invoke(() ->
		{
			pendingHop = null;
			hopAttempts = 0;
			if (prompt != null)
			{
				prompt.clear();
			}
			if (currentTracker != null)
			{
				currentTracker.clear();
			}
		});
		sessionGeneration.incrementAndGet();
		cancelReportRetry();
		// One last try for a trade report that is still waiting; it is not cancelled with the other calls.
		ReportOutbox.Entry last = api == null ? null : reportOutbox.peek(registeredAccountHash);
		if (last != null && api.hasToken())
		{
			api.reportAndForget(last.dealId, last.report);
		}
		reportOutbox.clear();
		if (socket != null)
		{
			socket.disconnect();
			socket = null;
		}
		if (panel != null)
		{
			panel.dispose();
			panel = null;
		}
		if (api != null)
		{
			api.cancelAll();
			api.clearToken();
			api = null;
		}
		registering = false;
		registeredRsn = null;
		registeredAccountHash = 0;
		memberShown = false;
		nearGeShown = false;
		retryAfter = Instant.MIN;
		nearbyNotifiedDealId = 0;
		log.info("Trade Finder stopped");
	}

	@Provides
	TradeFinderConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TradeFinderConfig.class);
	}

	/** The fixed server address. Only a developer-mode client may override it, with a JVM property. */
	private String backendUrl()
	{
		if (developerMode)
		{
			String override = System.getProperty(TradeFinderConfig.DEV_BACKEND_URL_PROPERTY);
			if (override != null && !override.trim().isEmpty())
			{
				return override.trim();
			}
		}
		return TradeFinderConfig.BACKEND_URL;
	}

	// ---- session ----

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		tracker.onGameStateChanged(event.getGameState());
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Logged out: the next login registers again (possibly another account).
			dropSession();
			pendingHop = null;
			hopAttempts = 0;
			onPanel(p ->
			{
				p.setCurrentWorld(0);
				p.setNearGe(false);
			});
		}
		else if (event.getGameState() == GameState.LOGGED_IN)
		{
			int world = client.getWorld();
			onPanel(p -> p.setCurrentWorld(world));
		}
		// LOGGED_IN: the local player name is often still null here; onGameTick registers once it is set.
	}

	/**
	 * Registers when logged in and not yet registered for this account hash + RSN, and reports
	 * presence (world, near GE) while registered. Cheap per tick: a few comparisons.
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		tracker.onGameTick();
		performPendingHop();
		if (!config.backendEnabled() || registering)
		{
			return;
		}
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}
		String rsn = local.getName();
		long accountHash = client.getAccountHash();
		if (api.hasToken() && rsn.equals(registeredRsn) && accountHash == registeredAccountHash)
		{
			reportPresence(local);
			return;
		}
		if (Instant.now().isBefore(retryAfter))
		{
			return;
		}
		// Seed the presence first so the socket's first heartbeat after registration is right.
		reportPresence(local);
		register(accountHash, rsn);
	}

	/**
	 * World, near-GE flag and membership for the heartbeat. Membership is the account's membership
	 * days varp ({@link VarPlayerID#ACCOUNT_CREDIT}, the varp the older {@code VarPlayer.MEMBERSHIP_DAYS}
	 * names): above 0 while membership is active. As a second source, a members world: only members
	 * can be logged into one. Client thread.
	 */
	private void reportPresence(Player local)
	{
		boolean member = OrderRules.isMember(client.getVarpValue(VarPlayerID.ACCOUNT_CREDIT), client.getWorldType().contains(WorldType.MEMBERS));
		boolean nearGe = GrandExchangeArea.contains(local.getWorldLocation());
		socket.updatePresence(client.getWorld(), nearGe, member);
		if (nearGe != nearGeShown)
		{
			nearGeShown = nearGe;
			onPanel(p -> p.setNearGe(nearGe));
		}
		if (member != memberShown)
		{
			memberShown = member;
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.setMember(member);
				}
			});
		}
	}

	private void register(long accountHash, String rsn)
	{
		registering = true;
		log.debug("Registering session for {} ({})", rsn, accountHash);
		int generation = sessionGeneration.get();
		// The name shown above a player's head uses non-breaking spaces; the server gets plain ones.
		String sentRsn = rsn.replace('\u00A0', ' ');
		api.createSession(String.valueOf(accountHash), sentRsn, () -> sessionGeneration.get() == generation, new ApiCallback<SessionResponse>()
		{
			@Override
			public void onSuccess(SessionResponse result)
			{
				if (sessionGeneration.get() != generation)
				{
					log.debug("Ignoring a session reply that arrived after the session was dropped");
					return;
				}
				clientThread.invoke(() ->
				{
					registering = false;
					registeredRsn = rsn;
					registeredAccountHash = accountHash;
					retryAfter = Instant.MIN;
					// A trade report that was not delivered goes out now, but only for the account that made it.
					reportOutbox.keepOnlyFor(accountHash);
					sendPendingReport();
					if (tracker != null)
					{
						tracker.resetPartnerSeen();
					}
				});
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.setRegistered(result.getRsn());
					}
				});
				TradeFinderSocket current = socket;
				if (current != null)
				{
					current.connect();
				}
				refresh();
				loadHistory();
			}

			@Override
			public void onFailure(String message)
			{
				if (sessionGeneration.get() != generation)
				{
					return;
				}
				log.debug("Session registration failed: {}", message);
				clientThread.invoke(() ->
				{
					registering = false;
					retryAfter = Instant.now().plus(REGISTER_RETRY);
				});
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.setRegistrationError(message);
					}
				});
			}
		});
	}

	/** Run {@code action} on the Swing thread with the panel, unless the plugin has shut down by then. */
	private void onPanel(Consumer<TradeFinderPanel> action)
	{
		SwingUtilities.invokeLater(() ->
		{
			TradeFinderPanel current = panel;
			if (current != null)
			{
				action.accept(current);
			}
		});
	}

	/** Forget the token and session and close the socket; safe from any thread. */
	private void dropSession()
	{
		sessionGeneration.incrementAndGet();
		if (socket != null)
		{
			socket.disconnect();
		}
		api.cancelAll();
		reportOutbox.attemptsCancelled();
		api.clearToken();
		clientThread.invoke(() ->
		{
			registering = false;
			registeredRsn = null;
			registeredAccountHash = 0;
			memberShown = false;
			nearGeShown = false;
			retryAfter = Instant.MIN;
			if (tracker != null)
			{
				tracker.resetPartnerSeen(); // a /seen call cancelled with the session was never delivered
			}
		});
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.setRegistered(null);
			}
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!TradeFinderConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		switch (event.getKey())
		{
			case "backendEnabled":
				boolean enabled = config.backendEnabled();
				if (!enabled)
				{
					dropSession();
				}
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.setBackendEnabled(enabled);
					}
				});
				break;
			default:
				break;
		}
	}

	// ---- in-game: partner, trade window, completion (client thread) ----

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		tracker.onPlayerSpawned(event.getPlayer());
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		tracker.onPlayerDespawned(event.getPlayer());
	}

	@Subscribe
	public void onWorldChanged(WorldChanged event)
	{
		tracker.onWorldChanged();
		int world = client.getWorld();
		onPanel(p ->
		{
			p.setCurrentWorld(world);
			p.setDealStatus(" ");
		});
	}

	// ---- Hop button: one world hop per click, like the core World Hopper; never without a click ----

	/**
	 * Hop to {@code worldId} exactly like the core World Hopper: look the world up in the world
	 * list, build the client's world object, open the world switcher and hop on the next tick.
	 * Called from the Swing thread on a click; one hop per click.
	 */
	void hopToWorld(int worldId)
	{
		WorldResult result = worldService.getWorlds();
		if (result == null || client.getGameState() != GameState.LOGGED_IN)
		{
			panel.setDealStatus("World list not loaded yet; try again in a moment.");
			return;
		}
		World world = result.findWorld(worldId);
		if (world == null)
		{
			panel.setDealStatus("World " + worldId + " is not in the world list.");
			return;
		}
		panel.setDealStatus("Hopping...");
		clientThread.invoke(() ->
		{
			net.runelite.api.World rsWorld = client.createWorld();
			rsWorld.setActivity(world.getActivity());
			rsWorld.setAddress(world.getAddress());
			rsWorld.setId(world.getId());
			rsWorld.setPlayerCount(world.getPlayers());
			rsWorld.setLocation(world.getRegion().getId());
			rsWorld.setTypes(WorldUtil.toWorldTypes(world.getTypes()));
			pendingHop = rsWorld;
			hopAttempts = 0;
		});
	}

	/** Client thread, each tick: the world switcher must be open before the client will hop. */
	private void performPendingHop()
	{
		net.runelite.api.World target = pendingHop;
		if (target == null)
		{
			return;
		}
		if (client.getWidget(InterfaceID.Worldswitcher.BUTTONS) == null)
		{
			if (++hopAttempts > HOP_ATTEMPTS)
			{
				pendingHop = null;
				hopAttempts = 0;
				onPanel(p -> p.setDealStatus("Could not open the world switcher."));
				return;
			}
			client.openWorldHopper();
			return;
		}
		pendingHop = null;
		client.hopToWorld(target);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (amountPrompt != null)
		{
			amountPrompt.onMenuOptionClicked(event.getMenuOption(), event.getItemId());
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (amountPrompt != null)
		{
			amountPrompt.onClientTick();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		tracker.onWidgetLoaded(event.getGroupId());
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		tracker.onWidgetClosed(event.getGroupId());
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		tracker.onItemContainerChanged(event.getContainerId());
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		if (type == ChatMessageType.TRADE || type == ChatMessageType.GAMEMESSAGE || type == ChatMessageType.MESBOX)
		{
			tracker.onChatMessage(event.getMessage());
		}
	}

	/** The panel's active deal changed (Swing); hand it to the tracker on the client thread. */
	void dealChanged(@Nullable Deal deal)
	{
		clientThread.invoke(() ->
		{
			if (tracker != null)
			{
				tracker.setDeal(deal);
			}
		});
	}

	/** Trade window events from the tracker (client thread): tell the backend and the card. */
	private class TradeListener implements TradeTracker.Listener
	{
		@Override
		public void tradeOpened(Deal deal)
		{
			log.debug("Trade window open with {} for deal {}", deal.getPartnerRsn(), deal.getId());
			api.tradeState(deal.getId(), "opened", onSwing(updated -> {},
				message -> panel.setDealStatus("Could not tell the server that the trade window opened.")));
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.setDealStatus("Trading with " + deal.getPartnerRsn()
						+ ". Check the Trade Finder box on the trade window before you accept.");
				}
			});
		}

		@Override
		public void tradeDeclined(Deal deal)
		{
			log.debug("Trade window closed without accepting for deal {}", deal.getId());
			api.tradeState(deal.getId(), "declined", onSwing(updated -> {},
				message -> panel.setDealStatus("Could not tell the server that the trade was closed.")));
		}

		@Override
		public void partnerSeen(Deal deal)
		{
			log.debug("Partner of deal {} is in view at the meeting spot", deal.getId());
			api.partnerSeen(deal.getId(), new ApiCallback<JsonElement>()
			{
				@Override
				public void onSuccess(JsonElement result)
				{
				}

				@Override
				public void onFailure(String message)
				{
					retry();
				}

				@Override
				public void onFailure(int httpCode, String message)
				{
					if (!ReportOutbox.isFinalAnswer(httpCode))
					{
						retry();
					}
				}

				private void retry()
				{
					clientThread.invoke(() ->
					{
						TradeTracker current = tracker;
						if (current != null)
						{
							current.partnerSeenFailed(deal.getId());
						}
					});
				}
			});
		}

		@Override
		public void tradeMismatch(Deal deal, TradeCheck.Result result)
		{
			log.debug("Trade does not match deal {}", deal.getId());
			notifier.notify(TradeAlert.notificationText(result));
		}

		@Override
		public void tradeAccepted(Deal deal, TradeItems.Diff diff)
		{
			log.debug("Trade accepted for deal {}: gave {}, received {}", deal.getId(), diff.getGave(), diff.getReceived());
			TradeReport report = new TradeReport(TradeReport.ACCEPTED,
				TradeItems.toReport(diff.getGave()), TradeItems.toReport(diff.getReceived()));
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.setDealStatus("Trade accepted. Reporting what changed hands...");
				}
			});
			reportOutbox.put(client.getAccountHash(), deal.getId(), report);
			sendPendingReport();
		}
	}

	/**
	 * Send the waiting trade report, if there is one and this account's session is up. Retried with
	 * backoff until the server answers (success, or a 4xx that retrying cannot change); a lost
	 * session just pauses it until the next registration. Client thread.
	 */
	private void sendPendingReport()
	{
		TradeFinderApi current = api;
		if (current == null || registering || !current.hasToken())
		{
			return;
		}
		ReportOutbox.Entry entry = reportOutbox.claim(registeredAccountHash);
		if (entry == null)
		{
			return;
		}
		current.report(entry.dealId, entry.report, new ApiCallback<Deal>()
		{
			@Override
			public void onSuccess(Deal result)
			{
				reportOutbox.finished(entry);
				showDealStatus("Trade accepted. Waiting for your partner's report...");
			}

			@Override
			public void onFailure(String message)
			{
				retryReport(entry); // no answer: server unreachable or unreadable reply
			}

			@Override
			public void onFailure(int httpCode, String message)
			{
				if (ReportOutbox.isFinalAnswer(httpCode))
				{
					reportOutbox.finished(entry);
					showDealStatus("The server could not take the trade report: " + message);
					return;
				}
				retryReport(entry);
			}
		});
	}

	private void retryReport(ReportOutbox.Entry entry)
	{
		long delay = reportOutbox.failed(entry);
		showDealStatus("The trade went through, but the server could not be reached to report it. Trying again...");
		cancelReportRetry();
		reportRetry = executor.schedule(() -> clientThread.invoke(this::sendPendingReport), delay, TimeUnit.SECONDS);
	}

	private void cancelReportRetry()
	{
		ScheduledFuture<?> task = reportRetry;
		reportRetry = null;
		if (task != null)
		{
			task.cancel(false);
		}
	}

	private void showDealStatus(String status)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.setDealStatus(status);
			}
		});
	}

	// ---- realtime ----

	/** WebSocket callbacks (OkHttp threads) marshalled onto Swing, or the client thread for the session. */
	private class SocketListener implements RealtimeListener
	{
		@Override
		public void onConnected()
		{
			// Catch up on anything pushed while the socket was down.
			refresh();
			onPanel(p ->
			{
				p.setLive(true);
				p.setSocketDown(false);
			});
		}

		@Override
		public void onEvent(RealtimeEvent event)
		{
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					handleEvent(event);
				}
			});
		}

		@Override
		public void onUnauthorized()
		{
			// The token died server-side: drop it, and register again after the usual retry delay.
			dropSession();
			clientThread.invoke(() -> retryAfter = Instant.now().plus(REGISTER_RETRY));
		}

		@Override
		public void onBlocked()
		{
			onPanel(p -> p.setBlocked());
		}

		@Override
		public void onDisconnected(String reason, long retryInSeconds)
		{
			// The second failure in a row is no blip any more: say so instead of showing a stale card.
			boolean down = retryInSeconds >= 2;
			onPanel(p ->
			{
				p.setLive(false);
				p.setSocketDown(down);
			});
		}
	}

	/** Apply one pushed event to the panel. Swing thread. */
	private void handleEvent(RealtimeEvent event)
	{
		Deal deal = event.getDeal();
		switch (event.getType())
		{
			case RealtimeEvent.MATCHED:
				if (deal != null)
				{
					panel.showDeal(deal);
					panel.setDealStatus("Matched! Meet your partner at the Grand Exchange.");
					notifyMatched(deal);
				}
				refreshOrders();
				loadMe();
				break;
			case RealtimeEvent.CANCELLED:
			case RealtimeEvent.EXPIRED:
			case RealtimeEvent.COMPLETED:
			case RealtimeEvent.DISPUTED:
				dealEnded(event);
				break;
			case RealtimeEvent.WORLD_CHANGED:
				if (deal != null)
				{
					// The new world needs its own "partner is at the GE" notification.
					if (nearbyNotifiedDealId == deal.getId())
					{
						nearbyNotifiedDealId = 0;
					}
					panel.showDeal(deal);
					panel.dealWorldChanged();
					panel.setDealStatus("The meeting moved to world " + deal.getWorld() + ".");
					// No notification for the player's own click on World full.
					boolean own = worldFullDealId == deal.getId()
						&& Instant.now().isBefore(worldFullClickedAt.plus(OWN_CLICK_WINDOW));
					if (!own)
					{
						notify("Trade Finder: the meeting moved to world " + deal.getWorld() + ".");
					}
				}
				break;
			case RealtimeEvent.TRADE_DECLINED:
				if (deal != null)
				{
					panel.showDeal(deal);
				}
				panel.setDealStatus("Trade screen closed without accepting. The deal is still on.");
				break;
			case RealtimeEvent.PARTNER_NEARBY:
				panel.setPartnerNearby(event.getDealId());
				if (nearbyNotifiedDealId != event.getDealId())
				{
					nearbyNotifiedDealId = event.getDealId();
					notify("Trade Finder: your trade partner is at the Grand Exchange on world " + panel.dealWorld() + ".");
				}
				break;
			default:
				log.debug("Trade Finder: ignoring event {}", event.getType());
		}
	}

	/** A deal ended (cancelled, expired, completed, disputed): status line on the Deal tab plus a notification. Swing thread. */
	private void dealEnded(RealtimeEvent event)
	{
		reportOutbox.dealEnded(event.getDealId());
		panel.dealEnded(event.getDealId(), DealEndings.status(event.getType()));
		notify(DealEndings.notification(event.getType()));
		// A deal ending changes the slots and may start a posting pause: reload orders, deals and /me.
		refresh();
	}

	/** One-line match notification naming the item and the partner. Item names need the client thread. */
	private void notifyMatched(Deal deal)
	{
		clientThread.invoke(() ->
		{
			ItemComposition composition = itemManager.getItemComposition(deal.getItemId());
			String verb = deal.getMySide() == com.tradefinder.api.Side.SELL ? "selling" : "buying";
			notifier.notify("Trade Finder: matched with " + deal.getPartnerRsn() + ", " + verb + " "
				+ CoinAmounts.format(deal.getQuantity()) + " x " + composition.getName()
				+ " for " + CoinAmounts.shortFormat(deal.getPrice()) + " each.");
		});
	}

	private void notify(String message)
	{
		clientThread.invoke(() -> notifier.notify(message));
	}

	// ---- panel actions (called on the Swing thread) ----

	/** Reload own orders, the active deal and the slots / posting pause into the panel. */
	void refresh()
	{
		if (api == null)
		{
			return;
		}
		refreshOrders();
		loadMe();
		api.myDeals(onSwing(deals -> panel.showDeal(firstActive(deals)), message -> {}));
	}

	/** {@code GET /me}: slots used and any posting pause. Failures leave the panel as it is. */
	void loadMe()
	{
		if (api == null)
		{
			return;
		}
		api.me(onSwing(me -> panel.showMe(me), message -> log.debug("Trade Finder: /me failed: {}", message)));
	}

	void refreshOrders()
	{
		if (api == null)
		{
			return;
		}
		api.myOrders(onSwing(orders -> panel.showOrders(orders), message -> panel.onCancelFailed(message)));
	}

	/** Load the History tab (deals done, no-shows, closed deals). */
	void loadHistory()
	{
		if (api == null)
		{
			return;
		}
		api.history(onSwing(history -> panel.showHistory(history), message -> panel.onHistoryFailed(message)));
	}

	void postOrder(OrderRequest request)
	{
		api.postOrder(request, this.<PostOrderResponse>onSwing(response ->
		{
			panel.onOrderPosted(response.getOrder(), response.getDeal());
			refresh();
		}, message ->
		{
			panel.onOrderFailed(message);
			loadMe(); // a 409 (slots) or 429 (pause) comes with new state to show
		}));
	}

	/** The meeting world is full: ask the server for the next backup world (the answer also arrives as a push). */
	void worldFull(long dealId, int world)
	{
		worldFullDealId = dealId;
		worldFullClickedAt = Instant.now();
		api.worldFull(dealId, world, onSwing(deal ->
		{
			panel.showDeal(deal);
			panel.dealWorldChanged();
			panel.setDealStatus("The meeting is on world " + deal.getWorld() + ".");
		}, message -> panel.onWorldFullFailed(message)));
	}

	void cancelOrder(long orderId)
	{
		api.cancelOrder(orderId, onSwing(order -> refresh(), message ->
		{
			panel.onCancelFailed(message);
			refresh();
		}));
	}

	void checkPrice(int itemId, Long limitPrice)
	{
		api.price(itemId, limitPrice, onSwing(info -> panel.showPrice(itemId, info),
			message -> panel.showPriceError(itemId, message)));
	}

	/**
	 * How many of {@code itemId} the inventory holds, noted stacks included (both sides are
	 * canonicalised). Client thread for the count, the result handed to Swing.
	 */
	void countInInventory(int itemId, IntConsumer onSwing)
	{
		clientThread.invoke(() ->
		{
			int count = inventoryCount(itemId);
			SwingUtilities.invokeLater(() -> onSwing.accept(count));
		});
	}

	/** Client thread. */
	private int inventoryCount(int itemId)
	{
		int canonical = itemManager.canonicalize(itemId);
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return 0;
		}
		long total = 0;
		for (Item item : inventory.getItems())
		{
			if (item.getId() > 0 && itemManager.canonicalize(item.getId()) == canonical)
			{
				total += item.getQuantity();
			}
		}
		return (int) Math.min(Integer.MAX_VALUE, total);
	}

	/**
	 * "Sell via Trade Finder" next to Examine on tradeable inventory items. The entry only changes
	 * the side panel: it never sends anything to the game server.
	 */
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.backendEnabled() || api == null || !api.hasToken() || navButton == null)
		{
			return;
		}
		if (event.getActionParam1() != InterfaceID.Inventory.ITEMS || !"Examine".equals(event.getOption()))
		{
			return;
		}
		int itemId = event.getMenuEntry().getItemId();
		if (itemId <= 0)
		{
			return;
		}
		int canonical = itemManager.canonicalize(itemId);
		ItemComposition composition = itemManager.getItemComposition(canonical);
		if (!composition.isTradeable())
		{
			return;
		}
		String name = composition.getName();
		client.getMenu().createMenuEntry(-1)
			.setOption("Sell via Trade Finder")
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE)
			.setItemId(itemId)
			.onClick(e ->
			{
				// Client thread: count first, then fill the panel on Swing.
				int count = inventoryCount(canonical);
				SwingUtilities.invokeLater(() ->
				{
					panel.prefillSell(canonical, name, count);
					clientToolbar.openPanel(navButton);
				});
			});
	}

	/** Whether the canonical item is a members item; needs the client thread, result handed to Swing. */
	void resolveMembers(int itemId, Consumer<Boolean> onSwing)
	{
		clientThread.invoke(() ->
		{
			boolean members = itemManager.getItemComposition(itemManager.canonicalize(itemId)).isMembers();
			SwingUtilities.invokeLater(() -> onSwing.accept(members));
		});
	}

	/** Item names need the client thread; the result is handed to Swing. */
	void resolveItemName(int itemId, Consumer<String> onSwing)
	{
		clientThread.invoke(() ->
		{
			ItemComposition composition = itemManager.getItemComposition(itemId);
			String name = composition.getName();
			SwingUtilities.invokeLater(() -> onSwing.accept(name));
		});
	}

	private static Deal firstActive(List<Deal> deals)
	{
		for (Deal deal : deals)
		{
			if ("deal_on".equals(deal.getStatus()) || "trading".equals(deal.getStatus()))
			{
				return deal;
			}
		}
		return null;
	}

	/** Wrap panel updates so backend results (OkHttp threads) reach Swing, and are dropped after shutdown. */
	private <T> ApiCallback<T> onSwing(Consumer<T> onSuccess, Consumer<String> onFailure)
	{
		return new ApiCallback<T>()
		{
			@Override
			public void onSuccess(T result)
			{
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						onSuccess.accept(result);
					}
				});
			}

			@Override
			public void onFailure(String message)
			{
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						onFailure.accept(message); // each call shows its own short line; nothing is hidden
					}
				});
			}
		};
	}
}
