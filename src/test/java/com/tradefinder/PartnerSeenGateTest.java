package com.tradefinder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PartnerSeenGateTest
{
	@Test
	public void sendsOncePerDeal()
	{
		PartnerSeenGate gate = new PartnerSeenGate();
		assertFalse(gate.due(false, 1));
		assertTrue(gate.due(true, 2));
		assertFalse(gate.due(true, 3));
		assertFalse(gate.due(true, 400));
	}

	@Test
	public void retriesAfterAFailureOnceTheWaitIsOver()
	{
		PartnerSeenGate gate = new PartnerSeenGate();
		assertTrue(gate.due(true, 100));
		gate.failed(100);
		assertFalse(gate.due(true, 105));
		assertFalse(gate.due(false, 110));
		assertTrue(gate.due(true, 110));
		assertFalse(gate.due(true, 111));
	}

	@Test
	public void aWorldChangeAsksAgain()
	{
		PartnerSeenGate gate = new PartnerSeenGate();
		assertTrue(gate.due(true, 1));
		gate.reset();
		assertFalse(gate.due(false, 2));
		assertTrue(gate.due(true, 3));
	}
}
