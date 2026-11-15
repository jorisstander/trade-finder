package com.tradefinder.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.CacheControl;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * The one WebSocket per logged-in client ({@code GET /ws} with the session's bearer token). Sends
 * presence heartbeats, hands pushed events to a {@link RealtimeListener}, and reconnects with
 * capped exponential backoff for as long as {@link #connect()} is in effect. Every callback from
 * OkHttp lands on an OkHttp thread; nothing here touches the client or Swing.
 */
@Slf4j
public class TradeFinderSocket
{
	/** The server closes with this code when the token is bad. */
	public static final int CLOSE_UNAUTHORIZED = 4401;
	/** The server closes with this code when the account is blocked: do not reconnect. */
	public static final int CLOSE_BLOCKED = 4403;
	/** The server marks a player offline after 60 s without a heartbeat; send one every 30 s. */
	public static final long HEARTBEAT_SECONDS = 30;
	static final long BACKOFF_MIN_SECONDS = 1;
	static final long BACKOFF_MAX_SECONDS = 60;
	/** The backoff starts over only once a socket has stayed open this long (a flapping server must not reset it). */
	static final long STABLE_SECONDS = 30;
	private static final int CLOSE_NORMAL = 1000;

	private final OkHttpClient okHttpClient;
	private final Gson gson;
	private final TradeFinderApi api;
	private final ScheduledExecutorService executor;
	private final RealtimeListener listener;

	// All mutable state is guarded by this.
	@Nullable
	private WebSocket webSocket;
	private boolean wanted;
	private boolean open;
	private int failures;
	@Nullable
	private ScheduledFuture<?> reconnectTask;
	@Nullable
	private ScheduledFuture<?> heartbeatTask;
	@Nullable
	private ScheduledFuture<?> stableTask;
	private int world;
	private boolean nearGe;
	private boolean member;

	public TradeFinderSocket(OkHttpClient okHttpClient, TradeFinderApi api, ScheduledExecutorService executor,
		RealtimeListener listener)
	{
		this.okHttpClient = okHttpClient;
		this.gson = api.gson();
		this.api = api;
		this.executor = executor;
		this.listener = listener;
	}

	/** Delay before reconnect attempt number {@code failures} (1-based): 1, 2, 4, 8 ... capped at 60 s. */
	static long backoffSeconds(int failures)
	{
		int shift = Math.max(0, Math.min(failures - 1, 30));
		return Math.min(BACKOFF_MAX_SECONDS, BACKOFF_MIN_SECONDS << shift);
	}

	/**
	 * The wait before a reconnect: {@code seconds} scaled by a random factor between 0.5 and 1, so
	 * many clients that lost the server together do not come back together.
	 *
	 * @param random a value in [0, 1)
	 */
	static long jitteredMillis(long seconds, double random)
	{
		return (long) (seconds * 1000 * (0.5 + 0.5 * random));
	}

	/** Open the socket (no-op while already open or connecting) and keep it open until {@link #disconnect()}. */
	public synchronized void connect()
	{
		wanted = true;
		if (webSocket != null)
		{
			return;
		}
		cancelReconnect();
		String token = api.token();
		HttpUrl base = api.baseHttpUrl();
		if (token == null || base == null)
		{
			log.debug("Trade Finder socket: no token or bad URL, not connecting");
			return;
		}
		Request request = new Request.Builder()
			.url(TradeFinderApi.endpoint(base, "ws"))
			.cacheControl(CacheControl.FORCE_NETWORK)
			.header("Authorization", "Bearer " + token)
			.build();
		failures++;
		webSocket = okHttpClient.newWebSocket(request, new Listener());
	}

	/** Close the socket for good (until the next {@link #connect()}). Cancels reconnects and heartbeats. */
	public synchronized void disconnect()
	{
		wanted = false;
		cancelReconnect();
		cancelHeartbeat();
		cancelStable();
		WebSocket current = webSocket;
		webSocket = null;
		open = false;
		failures = 0;
		if (current != null)
		{
			current.close(CLOSE_NORMAL, "logged out");
		}
	}

	public synchronized boolean isOpen()
	{
		return open;
	}

	/** Record where the player is; sends a heartbeat at once when it changed and the socket is open. */
	public synchronized void updatePresence(int world, boolean nearGe, boolean member)
	{
		boolean changed = world != this.world || nearGe != this.nearGe || member != this.member;
		this.world = world;
		this.nearGe = nearGe;
		this.member = member;
		if (changed && open)
		{
			sendHeartbeat();
		}
	}

	private synchronized void sendHeartbeat()
	{
		WebSocket current = webSocket;
		if (current == null || !open)
		{
			return;
		}
		JsonObject message = new JsonObject();
		message.addProperty("type", "heartbeat");
		message.addProperty("world", world);
		message.addProperty("near_ge", nearGe);
		message.addProperty("member", member);
		// send() only queues the frame; OkHttp writes it on its own thread.
		current.send(gson.toJson(message));
	}

	private void scheduleReconnect(String reason)
	{
		long delay;
		synchronized (this)
		{
			if (!wanted || reconnectTask != null)
			{
				return;
			}
			delay = backoffSeconds(failures);
			reconnectTask = executor.schedule(() ->
			{
				synchronized (this)
				{
					reconnectTask = null;
				}
				connect();
			}, jitteredMillis(delay, ThreadLocalRandom.current().nextDouble()), TimeUnit.MILLISECONDS);
		}
		log.debug("Trade Finder socket: {}; reconnecting in {} s", reason, delay);
		listener.onDisconnected(reason, delay);
	}

	private void cancelReconnect()
	{
		if (reconnectTask != null)
		{
			reconnectTask.cancel(false);
			reconnectTask = null;
		}
	}

	private void cancelStable()
	{
		if (stableTask != null)
		{
			stableTask.cancel(false);
			stableTask = null;
		}
	}

	private void cancelHeartbeat()
	{
		if (heartbeatTask != null)
		{
			heartbeatTask.cancel(false);
			heartbeatTask = null;
		}
	}

	/** Forget {@code closed} if it is still the current socket; false for a stale one. */
	private synchronized boolean release(WebSocket closed)
	{
		if (webSocket != closed)
		{
			return false;
		}
		webSocket = null;
		open = false;
		cancelHeartbeat();
		cancelStable();
		return true;
	}

	/** Stop reconnecting for good: this account is blocked. */
	private void blocked(String why)
	{
		log.debug("Trade Finder socket: {}", why);
		synchronized (this)
		{
			wanted = false;
			failures = 0;
		}
		listener.onBlocked();
	}

	/** Stop reconnecting after a rejected token; the session logic registers again. */
	private void rejected(String why)
	{
		log.debug("Trade Finder socket: {}", why);
		synchronized (this)
		{
			wanted = false;
			failures = 0;
		}
		listener.onUnauthorized();
	}

	private class Listener extends WebSocketListener
	{
		private volatile boolean unauthorized;
		private volatile boolean blocked;

		@Override
		public void onOpen(WebSocket ws, Response response)
		{
			synchronized (TradeFinderSocket.this)
			{
				if (webSocket != ws)
				{
					ws.close(CLOSE_NORMAL, "superseded");
					return;
				}
				open = true;
				// failures stays as it is: it resets only after the socket proved stable.
				cancelStable();
				stableTask = executor.schedule(() ->
				{
					synchronized (TradeFinderSocket.this)
					{
						if (webSocket == ws && open)
						{
							failures = 0;
						}
					}
				}, STABLE_SECONDS, TimeUnit.SECONDS);
				cancelHeartbeat();
				heartbeatTask = executor.scheduleAtFixedRate(TradeFinderSocket.this::sendHeartbeat,
					0, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
			}
			log.debug("Trade Finder socket open");
			listener.onConnected();
		}

		@Override
		public void onMessage(WebSocket ws, String text)
		{
			RealtimeEvent event;
			try
			{
				event = gson.fromJson(text, RealtimeEvent.class);
			}
			catch (Exception e)
			{
				log.debug("Trade Finder socket: unreadable message {}", text, e);
				return;
			}
			if (event == null || event.getType() == null || RealtimeEvent.HEARTBEAT_ACK.equals(event.getType()))
			{
				return;
			}
			if (RealtimeEvent.ERROR.equals(event.getType()))
			{
				log.debug("Trade Finder socket: server error: {}", event.getDetail());
				return;
			}
			listener.onEvent(event);
		}

		@Override
		public void onClosing(WebSocket ws, int code, String reason)
		{
			unauthorized = code == CLOSE_UNAUTHORIZED;
			blocked = code == CLOSE_BLOCKED;
			// Finish the close handshake; onClosed follows.
			ws.close(CLOSE_NORMAL, null);
		}

		@Override
		public void onClosed(WebSocket ws, int code, String reason)
		{
			if (!release(ws))
			{
				return;
			}
			if (unauthorized)
			{
				rejected("token rejected (close " + code + ")");
				return;
			}
			if (blocked)
			{
				blocked("account blocked (close " + code + ")");
				return;
			}
			scheduleReconnect("closed (" + code + ")");
		}

		@Override
		public void onFailure(WebSocket ws, Throwable t, @Nullable Response response)
		{
			if (!release(ws))
			{
				return;
			}
			if (response != null && response.code() == 401)
			{
				rejected("token rejected (HTTP 401)");
				return;
			}
			if (blocked)
			{
				blocked("account blocked (close " + CLOSE_BLOCKED + ")");
				return;
			}
			if (unauthorized)
			{
				// The server may drop the connection right after its 4401 close frame.
				rejected("token rejected (close " + CLOSE_UNAUTHORIZED + ")");
				return;
			}
			scheduleReconnect(response != null ? "HTTP " + response.code() : String.valueOf(t.getMessage()));
		}
	}
}
