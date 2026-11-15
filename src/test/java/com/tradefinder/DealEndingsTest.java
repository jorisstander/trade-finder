package com.tradefinder;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.tradefinder.api.RealtimeEvent;
import org.junit.Test;

public class DealEndingsTest
{
	private static final String[] ENDINGS = {RealtimeEvent.CANCELLED, RealtimeEvent.EXPIRED,
		RealtimeEvent.COMPLETED, RealtimeEvent.DISPUTED};

	@Test
	public void everyEndingHasAStatusAndANotification()
	{
		for (String type : ENDINGS)
		{
			assertNotNull(type, DealEndings.status(type));
			assertTrue(type, DealEndings.notification(type).startsWith("Trade Finder: "));
		}
	}

	@Test
	public void otherEventsAreNoEnding()
	{
		assertNull(DealEndings.status(RealtimeEvent.MATCHED));
		assertNull(DealEndings.notification(RealtimeEvent.WORLD_CHANGED));
		assertNull(DealEndings.status(RealtimeEvent.TRADE_DECLINED));
	}
}
