package dev.serverworks.rack;

import java.util.Locale;

/**
 * The outcome of looking for a rack around a Server Core: either a valid structure, or
 * the first thing wrong with it and, where it helps, where.
 *
 * @param structure  set only when {@code problem} is {@link Problem#NONE}
 * @param problemPos the block to fix, or null when the problem is not about one block
 */
public record RackScan(Problem problem, Pos problemPos, RackStructure structure) {
	public static RackScan ok(RackStructure structure) {
		return new RackScan(Problem.NONE, null, structure);
	}

	public static RackScan fail(Problem problem, Pos pos) {
		return new RackScan(problem, pos, null);
	}

	public boolean formed() {
		return problem == Problem.NONE;
	}

	public enum Problem {
		NONE,
		TOO_SMALL,
		TOO_LARGE,
		/** A shell position holds something that is not a rack block. */
		MISSING_BLOCK,
		/** Edges and corners must be casing, not components. */
		EDGE_NOT_CASING,
		/** The inside of the rack must be empty. */
		NOT_HOLLOW,
		MULTIPLE_CORES,
		NO_PLATFORM,
		MULTIPLE_PLATFORMS,
		NO_DRIVE_BAY,
		NO_MEMORY,
		NO_VENT,
		NOT_ENOUGH_VENTS;

		public String translationKey() {
			return "serverworks.rack.problem." + name().toLowerCase(Locale.ROOT);
		}
	}
}
