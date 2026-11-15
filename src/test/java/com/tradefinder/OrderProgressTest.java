package com.tradefinder;

import static org.junit.Assert.assertEquals;
import com.tradefinder.api.Side;
import org.junit.Test;

public class OrderProgressTest
{
	@Test
	public void text()
	{
		assertEquals("3 of 6 sold", OrderProgress.text(Side.SELL, 3, 6));
		assertEquals("200 of 1,000 bought", OrderProgress.text(Side.BUY, 200, 1000));
		assertEquals("0 of 6 sold", OrderProgress.text(Side.SELL, 0, 6));
		assertEquals("6 of 6 sold", OrderProgress.text(Side.SELL, 9, 6));
	}

	@Test
	public void fraction()
	{
		assertEquals(0.5, OrderProgress.fraction(3, 6), 1e-9);
		assertEquals(0, OrderProgress.fraction(0, 6), 1e-9);
		assertEquals(1, OrderProgress.fraction(9, 6), 1e-9);
		assertEquals(0, OrderProgress.fraction(1, 0), 1e-9);
	}
}
