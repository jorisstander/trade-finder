package com.tradefinder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(TradeFinderConfig.GROUP)
public interface TradeFinderConfig extends Config
{
	String GROUP = "trade-finder";

	/** The Trade Finder server. Deliberately not a setting: it is the same server for every player. */
	String BACKEND_URL = "https://api.osrstrade.app";

	/** Removed setting; any value saved by an older build is cleared on start-up. */
	String LEGACY_BACKEND_URL_KEY = "backendUrl";

	/** Developer mode only: {@code -Dtradefinder.backendUrl=http://localhost:8000} points a dev client at a local backend. */
	String DEV_BACKEND_URL_PROPERTY = "tradefinder.backendUrl";

	@ConfigItem(
		keyName = "backendEnabled",
		name = "Connect to Trade Finder server",
		description = "Send your orders and presence to the Trade Finder server so you can be matched with other players",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 0
	)
	default boolean backendEnabled()
	{
		return false;
	}
}
