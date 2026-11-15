package com.tradefinder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class RealtimeTest
{
	@Test
	public void grandExchangeBounds()
	{
		assertTrue(GrandExchangeArea.contains(new WorldPoint(3165, 3487, 0))); // the GE clerks
		assertTrue(GrandExchangeArea.contains(new WorldPoint(3146, 3468, 0))); // south-west corner
		assertTrue(GrandExchangeArea.contains(new WorldPoint(3184, 3508, 0))); // north-east corner
		assertFalse(GrandExchangeArea.contains(new WorldPoint(3185, 3487, 0))); // one tile east
		assertFalse(GrandExchangeArea.contains(new WorldPoint(3165, 3467, 0))); // one tile south
		assertFalse(GrandExchangeArea.contains(new WorldPoint(3212, 3422, 0))); // Varrock square
		assertFalse(GrandExchangeArea.contains(null));
	}
}
