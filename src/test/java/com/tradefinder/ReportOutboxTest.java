package com.tradefinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.tradefinder.api.TradeReport;
import java.util.List;
import org.junit.Test;

public class ReportOutboxTest
{
	private static final TradeReport REPORT = new TradeReport(TradeReport.ACCEPTED, List.of(), List.of());

	@Test
	public void claimOnlyForTheAccountThatTraded()
	{
		ReportOutbox outbox = new ReportOutbox();
		outbox.put(7, 100, REPORT);
		assertNull(outbox.claim(8));
		assertNull(outbox.claim(0));
		assertNotNull(outbox.claim(7));
	}

	@Test
	public void oneAttemptAtATime()
	{
		ReportOutbox outbox = new ReportOutbox();
		outbox.put(7, 100, REPORT);
		ReportOutbox.Entry first = outbox.claim(7);
		assertNull(outbox.claim(7));
		outbox.failed(first);
		assertSame(first, outbox.claim(7));
	}

	@Test
	public void survivesCancelledCallsAndIsSentAgain()
	{
		ReportOutbox outbox = new ReportOutbox();
		outbox.put(7, 100, REPORT);
		assertNotNull(outbox.claim(7));
		outbox.attemptsCancelled();
		assertNotNull(outbox.claim(7));
	}

	@Test
	public void peekSeesAReportThatIsInFlight()
	{
		ReportOutbox outbox = new ReportOutbox();
		outbox.put(7, 100, REPORT);
		outbox.claim(7);
		assertNotNull(outbox.peek(7));
		assertNull(outbox.peek(8));
	}

	@Test
	public void finishedClears()
	{
		ReportOutbox outbox = new ReportOutbox();
		outbox.put(7, 100, REPORT);
		outbox.finished(outbox.claim(7));
		assertTrue(outbox.isEmpty());
		assertNull(outbox.claim(7));
	}

	@Test
	public void anotherAccountDropsIt()
	{
		ReportOutbox outbox = new ReportOutbox();
		outbox.put(7, 100, REPORT);
		outbox.keepOnlyFor(7);
		assertFalse(outbox.isEmpty());
		outbox.keepOnlyFor(8);
		assertTrue(outbox.isEmpty());
	}

	@Test
	public void endedDealDropsOnlyItsReport()
	{
		ReportOutbox outbox = new ReportOutbox();
		outbox.put(7, 100, REPORT);
		outbox.dealEnded(101);
		assertFalse(outbox.isEmpty());
		outbox.dealEnded(100);
		assertTrue(outbox.isEmpty());
	}

	@Test
	public void staleAnswerDoesNotClearANewerReport()
	{
		ReportOutbox outbox = new ReportOutbox();
		outbox.put(7, 100, REPORT);
		ReportOutbox.Entry old = outbox.claim(7);
		outbox.put(7, 101, REPORT);
		outbox.finished(old);
		assertFalse(outbox.isEmpty());
	}

	@Test
	public void backoffGrowsAndCaps()
	{
		assertEquals(2, ReportOutbox.backoffSeconds(1));
		assertEquals(4, ReportOutbox.backoffSeconds(2));
		assertEquals(32, ReportOutbox.backoffSeconds(5));
		assertEquals(60, ReportOutbox.backoffSeconds(6));
		assertEquals(60, ReportOutbox.backoffSeconds(500));
	}

	@Test
	public void finalAnswers()
	{
		assertTrue(ReportOutbox.isFinalAnswer(409));
		assertTrue(ReportOutbox.isFinalAnswer(404));
		assertTrue(ReportOutbox.isFinalAnswer(422));
		assertFalse(ReportOutbox.isFinalAnswer(401));
		assertFalse(ReportOutbox.isFinalAnswer(429));
		assertFalse(ReportOutbox.isFinalAnswer(500));
		assertFalse(ReportOutbox.isFinalAnswer(502));
	}
}
