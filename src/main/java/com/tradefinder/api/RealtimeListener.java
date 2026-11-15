package com.tradefinder.api;

/**
 * Callbacks from {@link TradeFinderSocket}. All of them run on an OkHttp thread: hop to the client
 * thread or Swing before touching the client or the panel.
 */
public interface RealtimeListener
{
	/** The socket is open and the first heartbeat is queued. Catch up with {@code GET /deals/me} here. */
	void onConnected();

	/** A pushed event other than {@code heartbeat.ack} and {@code error}. */
	void onEvent(RealtimeEvent event);

	/** The server refused the token (close code 4401 or HTTP 401). The socket stays closed. */
	void onUnauthorized();

	/** The server says this account is blocked (close code 4403). The socket stays closed. */
	void onBlocked();

	/** The socket closed or failed; a reconnect is due in {@code retryInSeconds}. */
	void onDisconnected(String reason, long retryInSeconds);
}
