package dev.serverworks.rack;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything wrong with a rack that is still being built, block by block, so the Wrench
 * can show it all at once instead of one problem at a time.
 */
public final class RackPreview {
	/** Shown at most, so a huge mistake does not flood the screen with particles. */
	public static final int MAX_HINTS = 160;

	private RackPreview() {
	}

	public enum Kind {
		/** A rack block belongs here. */
		MISSING,
		/** Something is here that must not be: a component on an edge, or a foreign block in the frame. */
		WRONG,
		/** The inside of the rack must be empty here. */
		CLEAR
	}

	public record Hint(Pos pos, Kind kind) {
	}

	/**
	 * @param inward one step from the core into the rack it fronts: the opposite of the way
	 *               the core faces. Used to suggest the smallest rack around a lone core.
	 */
	public static List<Hint> hints(RackValidator.View view, Pos core, Pos inward) {
		RackScan scan = RackValidator.scan(view, core);
		if (scan.formed()) {
			return List.of();
		}
		Pos min;
		Pos max;
		Bounds bounds = Bounds.of(view, core);
		if (bounds.sizeX() >= RackValidator.MIN_SIZE && bounds.sizeY() >= RackValidator.MIN_SIZE
				&& bounds.sizeZ() >= RackValidator.MIN_SIZE && scan.problem() != RackScan.Problem.TOO_LARGE) {
			min = bounds.min();
			max = bounds.max();
		} else {
			// Too little built to infer a shape: suggest a 3 x 3 x 3 rack with the core in the middle of one face.
			Pos centre = core.offset(inward.x(), inward.y(), inward.z());
			min = centre.offset(-1, -1, -1);
			max = centre.offset(1, 1, 1);
		}
		List<Hint> hints = new ArrayList<>();
		for (int y = min.y(); y <= max.y() && hints.size() < MAX_HINTS; y++) {
			for (int z = min.z(); z <= max.z() && hints.size() < MAX_HINTS; z++) {
				for (int x = min.x(); x <= max.x() && hints.size() < MAX_HINTS; x++) {
					Pos pos = new Pos(x, y, z);
					Cell cell = view.at(pos);
					int extremes = (x == min.x() || x == max.x() ? 1 : 0) + (y == min.y() || y == max.y() ? 1 : 0)
							+ (z == min.z() || z == max.z() ? 1 : 0);
					if (extremes == 0) {
						if (!cell.empty()) {
							hints.add(new Hint(pos, Kind.CLEAR));
						}
					} else if (!cell.isPart()) {
						hints.add(new Hint(pos, cell.empty() ? Kind.MISSING : Kind.WRONG));
					} else if (extremes >= 2 && cell.kind() != PartKind.CASING) {
						hints.add(new Hint(pos, Kind.WRONG));
					}
				}
			}
		}
		return hints;
	}

	/** The box spanned by the rack blocks connected to the core, within a maximum-size rack's reach. */
	private record Bounds(Pos min, Pos max) {
		int sizeX() {
			return max.x() - min.x() + 1;
		}

		int sizeY() {
			return max.y() - min.y() + 1;
		}

		int sizeZ() {
			return max.z() - min.z() + 1;
		}

		static Bounds of(RackValidator.View view, Pos core) {
			java.util.Set<Pos> seen = new java.util.HashSet<>();
			java.util.ArrayDeque<Pos> queue = new java.util.ArrayDeque<>();
			seen.add(core);
			queue.add(core);
			int reach = RackValidator.MAX_SIZE - 1;
			int[] lo = {core.x(), core.y(), core.z()};
			int[] hi = {core.x(), core.y(), core.z()};
			int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
			while (!queue.isEmpty()) {
				Pos pos = queue.poll();
				for (int[] d : steps) {
					Pos next = pos.offset(d[0], d[1], d[2]);
					if (seen.contains(next) || !view.at(next).isPart() || Math.abs(next.x() - core.x()) > reach
							|| Math.abs(next.y() - core.y()) > reach || Math.abs(next.z() - core.z()) > reach) {
						continue;
					}
					seen.add(next);
					queue.add(next);
					lo[0] = Math.min(lo[0], next.x());
					lo[1] = Math.min(lo[1], next.y());
					lo[2] = Math.min(lo[2], next.z());
					hi[0] = Math.max(hi[0], next.x());
					hi[1] = Math.max(hi[1], next.y());
					hi[2] = Math.max(hi[2], next.z());
				}
			}
			return new Bounds(new Pos(lo[0], lo[1], lo[2]), new Pos(hi[0], hi[1], hi[2]));
		}
	}
}
