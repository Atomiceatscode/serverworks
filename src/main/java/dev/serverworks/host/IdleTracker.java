package dev.serverworks.host;

/**
 * Decides when an empty server has been empty long enough to put to sleep. A server
 * that has only just started gets the full grace period before anyone has joined.
 */
public final class IdleTracker {
	private final long idleMillis;
	private long emptySince = -1;

	public IdleTracker(long idleMillis) {
		this.idleMillis = idleMillis;
	}

	/** Records a player count. Returns true once the server has been empty for the whole period. */
	public synchronized boolean observe(int online, long nowMillis) {
		if (online != 0) {
			// Players present, or the count is unknown: never sleep on a guess.
			emptySince = -1;
			return false;
		}
		if (emptySince < 0) {
			emptySince = nowMillis;
		}
		return nowMillis - emptySince >= idleMillis;
	}

	public synchronized void reset() {
		emptySince = -1;
	}
}
