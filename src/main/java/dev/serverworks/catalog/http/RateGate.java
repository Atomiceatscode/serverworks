package dev.serverworks.catalog.http;

import java.util.Map;

/**
 * Keeps requests inside the catalog's published rate limit by reading the
 * {@code x-ratelimit-*} headers of every response.
 *
 * <p>While plenty of budget remains it does nothing. When the remaining budget drops to
 * the reserve, it holds further requests until the window resets.
 */
public final class RateGate {
	/** Requests kept in hand so that a burst from another thread cannot overshoot. */
	private static final int RESERVE = 5;
	private static final long MAX_HOLD_MILLIS = 60_000;

	private long holdUntilMillis;

	/** How long the next request must wait, in milliseconds; zero when it may go now. */
	public synchronized long delayMillis(long nowMillis) {
		return Math.max(0, holdUntilMillis - nowMillis);
	}

	/** Records what a response said about the remaining budget. */
	public synchronized void onResponse(Map<String, String> headers, long nowMillis) {
		Long remaining = parse(headers.get("x-ratelimit-remaining"));
		Long resetSeconds = parse(headers.get("x-ratelimit-reset"));
		if (remaining == null || resetSeconds == null) {
			return;
		}
		if (remaining <= RESERVE) {
			hold(nowMillis, resetSeconds * 1000);
		}
	}

	/** Records an explicit "too many requests" answer. Returns how long to wait before retrying. */
	public synchronized long onRejected(Map<String, String> headers, long nowMillis) {
		Long resetSeconds = parse(headers.get("x-ratelimit-reset"));
		Long retryAfter = parse(headers.get("retry-after"));
		long seconds = resetSeconds != null ? resetSeconds : retryAfter != null ? retryAfter : 5;
		hold(nowMillis, Math.max(1, seconds) * 1000);
		return delayMillis(nowMillis);
	}

	private void hold(long nowMillis, long forMillis) {
		long until = nowMillis + Math.min(MAX_HOLD_MILLIS, Math.max(0, forMillis));
		holdUntilMillis = Math.max(holdUntilMillis, until);
	}

	private static Long parse(String value) {
		if (value == null) {
			return null;
		}
		try {
			return Long.parseLong(value.strip());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
