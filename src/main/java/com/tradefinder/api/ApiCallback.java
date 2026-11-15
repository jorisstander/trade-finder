package com.tradefinder.api;

/**
 * Result of an asynchronous backend call. Both methods run on an OkHttp thread: hop to the
 * client thread ({@code clientThread.invoke}) or Swing ({@code SwingUtilities.invokeLater})
 * before touching the client or the panel.
 */
public interface ApiCallback<T>
{
	void onSuccess(T result);

	/**
	 * @param message short, user-readable reason (the backend's {@code detail} when it sent one)
	 */
	void onFailure(String message);

	/**
	 * Same as {@link #onFailure(String)} but with the HTTP status when the server answered. Override
	 * to react to a specific status; by default it just reports the message.
	 */
	default void onFailure(int httpCode, String message)
	{
		onFailure(message);
	}
}
