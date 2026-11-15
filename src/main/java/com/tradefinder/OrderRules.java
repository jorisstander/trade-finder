package com.tradefinder;

import javax.annotation.Nullable;

/** Small decisions about posting an order, kept free of Swing and the client so they can be tested. */
final class OrderRules
{
	static final String MEMBERS_ITEM_MESSAGE = "This is a members item. You need membership to trade it.";

	private OrderRules()
	{
	}

	/** Why the order cannot be posted for membership reasons, or null when it can. */
	@Nullable
	static String membersBlock(boolean membersItem, boolean member)
	{
		return membersItem && !member ? MEMBERS_ITEM_MESSAGE : null;
	}

	/**
	 * Membership: the account's membership-days varp is above 0, or the client is logged into a
	 * members world (only members can be). Either source is enough.
	 */
	static boolean isMember(int membershipDays, boolean onMembersWorld)
	{
		return membershipDays > 0 || onMembersWorld;
	}
}
