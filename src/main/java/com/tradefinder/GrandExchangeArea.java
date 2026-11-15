package com.tradefinder;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/**
 * Where "near the Grand Exchange" is for the presence heartbeat: the GE building in Varrock plus the
 * paved square around it, up to the surrounding walls. South-west corner (3146, 3468), north-east
 * corner (3184, 3508), ground floor. The check is one rectangle comparison per game tick.
 */
final class GrandExchangeArea
{
	static final int WEST = 3146;
	static final int SOUTH = 3468;
	static final int EAST = 3184;
	static final int NORTH = 3508;
	static final int PLANE = 0;

	static final WorldArea AREA = new WorldArea(WEST, SOUTH, EAST - WEST + 1, NORTH - SOUTH + 1, PLANE);

	private GrandExchangeArea()
	{
	}

	/** True when the point lies inside the area (x and y; the GE has no upper floors). */
	static boolean contains(WorldPoint point)
	{
		return point != null && AREA.contains2D(point);
	}
}
