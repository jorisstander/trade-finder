package com.tradefinder.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DeclinedCallbackTest
{
	private static class Recorder implements ApiCallback<Deal>
	{
		boolean succeeded;
		Deal result;
		String failure;

		@Override
		public void onSuccess(Deal deal)
		{
			succeeded = true;
			result = deal;
		}

		@Override
		public void onFailure(String message)
		{
			failure = message;
		}
	}

	@Test
	public void conflictOnDeclinedIsANullSuccess()
	{
		Recorder recorder = new Recorder();
		TradeFinderApi.isAlreadyDeclined("declined", recorder).onFailure(409, "conflict");
		assertTrue(recorder.succeeded);
		assertNull(recorder.result);
		assertNull(recorder.failure);
	}

	@Test
	public void conflictOnOpenedStillFails()
	{
		Recorder recorder = new Recorder();
		TradeFinderApi.isAlreadyDeclined("opened", recorder).onFailure(409, "conflict");
		assertEquals("conflict", recorder.failure);
		assertEquals(false, recorder.succeeded);
	}

	@Test
	public void otherErrorsOnDeclinedStillFail()
	{
		Recorder recorder = new Recorder();
		TradeFinderApi.isAlreadyDeclined("declined", recorder).onFailure(500, "Server error 500");
		assertEquals("Server error 500", recorder.failure);
		assertEquals(false, recorder.succeeded);
	}
}
