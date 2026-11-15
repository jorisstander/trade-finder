package com.tradefinder.api;

import static org.junit.Assert.assertEquals;
import okhttp3.HttpUrl;
import org.junit.Test;

public class TradeFinderSocketTest
{
	@Test
	public void backoffDoublesAndCaps()
	{
		assertEquals(1, TradeFinderSocket.backoffSeconds(0));
		assertEquals(1, TradeFinderSocket.backoffSeconds(1));
		assertEquals(2, TradeFinderSocket.backoffSeconds(2));
		assertEquals(4, TradeFinderSocket.backoffSeconds(3));
		assertEquals(32, TradeFinderSocket.backoffSeconds(6));
		assertEquals(60, TradeFinderSocket.backoffSeconds(7));
		assertEquals(60, TradeFinderSocket.backoffSeconds(100));
	}

	@Test
	public void jitterStaysBetweenHalfAndFullDelay()
	{
		assertEquals(4000, TradeFinderSocket.jitteredMillis(8, 0.0));
		assertEquals(6000, TradeFinderSocket.jitteredMillis(8, 0.5));
		assertEquals(7996, TradeFinderSocket.jitteredMillis(8, 0.999));
		assertEquals(500, TradeFinderSocket.jitteredMillis(1, 0.0));
	}

	private static String endpoint(String base, String path)
	{
		return TradeFinderApi.endpoint(HttpUrl.parse(base), path).toString();
	}

	@Test
	public void endpointsWorkWithTrailingSlashAndPathPrefix()
	{
		assertEquals("http://localhost:8000/ws", endpoint("http://localhost:8000", "ws"));
		assertEquals("http://localhost:8000/ws", endpoint("http://localhost:8000/", "ws"));
		assertEquals("https://tf.example.com/api/ws", endpoint("https://tf.example.com/api", "ws"));
		assertEquals("https://tf.example.com/api/ws", endpoint("https://tf.example.com/api/", "ws"));
		assertEquals("https://tf.example.com/api/deals/7/trade", endpoint("https://tf.example.com/api/", "deals/7/trade"));
	}
}
