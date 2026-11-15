package com.tradefinder;

/** The header dot: what the connection to the server is doing. */
enum ConnectionState
{
	CONNECTED("Connected"),
	CONNECTING("Connecting"),
	UNREACHABLE("Cannot reach the Trade Finder server"),
	LOGGED_OUT("Logged out"),
	OFF("Off: turn on the server in the plugin settings");

	private final String label;

	ConnectionState(String label)
	{
		this.label = label;
	}

	/**
	 * @param inGame the player is logged in to the game: not registered yet then means "still
	 * connecting" (or the server cannot be reached), never "logged out"
	 * @param unreachable calls to the server get no answer
	 */
	static ConnectionState of(boolean backendEnabled, boolean registered, boolean live, boolean inGame,
		boolean unreachable)
	{
		if (!backendEnabled)
		{
			return OFF;
		}
		if (unreachable)
		{
			return UNREACHABLE;
		}
		if (!registered)
		{
			return inGame ? CONNECTING : LOGGED_OUT;
		}
		return live ? CONNECTED : CONNECTING;
	}

	/** Tooltip: the state, plus "as RSN" while registered. */
	String tooltip(String rsn)
	{
		return rsn != null && (this == CONNECTED || this == CONNECTING) ? label + " as " + rsn : label;
	}
}
