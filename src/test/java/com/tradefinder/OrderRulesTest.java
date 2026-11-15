package com.tradefinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class OrderRulesTest
{
	@Test
	public void membersItemNeedsMembership()
	{
		assertEquals("This is a members item. You need membership to trade it.", OrderRules.membersBlock(true, false));
		assertNull(OrderRules.membersBlock(true, true));
		assertNull(OrderRules.membersBlock(false, false));
	}

	@Test
	public void membershipFromDays()
	{
		assertTrue(OrderRules.isMember(12, false));
		assertFalse(OrderRules.isMember(0, false));
		assertFalse(OrderRules.isMember(-1, false));
	}

	@Test
	public void aMembersWorldAloneProvesMembership()
	{
		assertTrue(OrderRules.isMember(0, true));
	}
}
