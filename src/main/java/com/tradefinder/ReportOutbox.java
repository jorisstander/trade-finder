package com.tradefinder;

import com.tradefinder.api.TradeReport;
import javax.annotation.Nullable;

/**
 * The trade report that still has to reach the server. It lives outside {@link TradeTracker}, so a
 * hop, a logout or a dropped connection right after "Accepted trade." does not lose it, and it is
 * tied to the account that made the trade: it is never sent through another account's session.
 * Pure bookkeeping (the plugin does the sending and the timing); thread safe.
 */
final class ReportOutbox
{
	static final long RETRY_MIN_SECONDS = 2;
	static final long RETRY_MAX_SECONDS = 60;

	/** One report waiting to be sent. */
	static final class Entry
	{
		final long accountHash;
		final long dealId;
		final TradeReport report;
		private int failures;
		private boolean inFlight;

		Entry(long accountHash, long dealId, TradeReport report)
		{
			this.accountHash = accountHash;
			this.dealId = dealId;
			this.report = report;
		}
	}

	@Nullable
	private Entry pending;

	/** Remember a report; it replaces any report waiting for another deal. */
	synchronized void put(long accountHash, long dealId, TradeReport report)
	{
		pending = new Entry(accountHash, dealId, report);
	}

	/**
	 * The report to send now, or null when there is none, it belongs to another account than the
	 * one with the session, or an attempt is already under way.
	 */
	@Nullable
	synchronized Entry claim(long sessionAccountHash)
	{
		Entry entry = pending;
		if (entry == null || entry.inFlight || entry.accountHash != sessionAccountHash)
		{
			return null;
		}
		entry.inFlight = true;
		return entry;
	}

	/** The waiting report of this account whether or not an attempt is under way (for the last send at shutdown), or null. */
	@Nullable
	synchronized Entry peek(long sessionAccountHash)
	{
		return pending != null && pending.accountHash == sessionAccountHash ? pending : null;
	}

	/** The server answered for {@code entry}: success, or an answer that retrying cannot change. */
	synchronized void finished(Entry entry)
	{
		if (pending == entry)
		{
			pending = null;
		}
	}

	/**
	 * The attempt for {@code entry} failed and should be retried.
	 *
	 * @return seconds to wait before the next attempt
	 */
	synchronized long failed(Entry entry)
	{
		entry.inFlight = false;
		entry.failures++;
		return backoffSeconds(entry.failures);
	}

	/** In-flight calls were cancelled (session dropped): their answers will never arrive. */
	synchronized void attemptsCancelled()
	{
		if (pending != null)
		{
			pending.inFlight = false;
		}
	}

	/** Drop the report unless it belongs to this account (a different account logged in). */
	synchronized void keepOnlyFor(long accountHash)
	{
		if (pending != null && pending.accountHash != accountHash)
		{
			pending = null;
		}
	}

	/** The deal ended on the server; its report no longer matters. */
	synchronized void dealEnded(long dealId)
	{
		if (pending != null && pending.dealId == dealId)
		{
			pending = null;
		}
	}

	synchronized void clear()
	{
		pending = null;
	}

	synchronized boolean isEmpty()
	{
		return pending == null;
	}

	/** Delay after failure number {@code failures} (1-based): 2, 4, 8 ... capped at 60 s. */
	static long backoffSeconds(int failures)
	{
		int shift = Math.max(0, Math.min(failures - 1, 30));
		return Math.min(RETRY_MAX_SECONDS, RETRY_MIN_SECONDS << shift);
	}

	/** Whether the server's answer settles the report: any 4xx, except those that mean "try again later". */
	static boolean isFinalAnswer(int httpCode)
	{
		if (httpCode < 400 || httpCode >= 500)
		{
			return false;
		}
		return httpCode != 401 && httpCode != 408 && httpCode != 429;
	}
}
