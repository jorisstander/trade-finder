package com.tradefinder;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ConnectionStateTest
{
	@Test
	public void states()
	{
		assertEquals(ConnectionState.OFF, ConnectionState.of(false, true, true, true, false));
		assertEquals(ConnectionState.OFF, ConnectionState.of(false, false, false, true, true));
		assertEquals(ConnectionState.LOGGED_OUT, ConnectionState.of(true, false, false, false, false));
		assertEquals(ConnectionState.CONNECTING, ConnectionState.of(true, true, false, true, false));
		assertEquals(ConnectionState.CONNECTED, ConnectionState.of(true, true, true, true, false));
	}

	@Test
	public void inGameButNotRegisteredIsNeverLoggedOut()
	{
		assertEquals(ConnectionState.CONNECTING, ConnectionState.of(true, false, false, true, false));
	}

	@Test
	public void unreachableWins()
	{
		assertEquals(ConnectionState.UNREACHABLE, ConnectionState.of(true, false, false, true, true));
		assertEquals(ConnectionState.UNREACHABLE, ConnectionState.of(true, true, false, true, true));
		assertEquals(ConnectionState.UNREACHABLE, ConnectionState.of(true, false, false, false, true));
	}

	@Test
	public void tooltipNamesTheAccount()
	{
		assertEquals("Connected as Zezima", ConnectionState.CONNECTED.tooltip("Zezima"));
		assertEquals("Logged out", ConnectionState.LOGGED_OUT.tooltip(null));
	}
}
