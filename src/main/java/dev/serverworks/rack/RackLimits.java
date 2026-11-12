package dev.serverworks.rack;

/** How much a rack's server gets: memory from the RAM sticks in its Memory Banks, and players from memory. */
public final class RackLimits {
	/** Every rack's server starts with this much, Memory Bank or not. */
	public static final int BASE_RAM_MB = 1024;
	/** The most memory one rack's server may have, however many sticks it holds. */
	public static final int MAX_RAM_MB = 32 * 1024;
	public static final int PLAYERS_PER_GB = 10;
	public static final int MAX_PLAYERS = 100;

	private RackLimits() {
	}

	/** The server's memory: the base amount plus every stick, up to the maximum. */
	public static int ramMb(int stickMb) {
		return Math.min(MAX_RAM_MB, BASE_RAM_MB + Math.max(0, stickMb));
	}

	/** Ten players for each gigabyte of memory, up to a hundred. */
	public static int playersFor(int ramMb) {
		return Math.clamp((long) ramMb / 1024 * PLAYERS_PER_GB, PLAYERS_PER_GB, MAX_PLAYERS);
	}
}
