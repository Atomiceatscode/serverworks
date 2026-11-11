package dev.serverworks.rack;

import dev.serverworks.rack.RackScan.Problem;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides whether the blocks around a Server Core make a valid rack.
 *
 * <p>A rack is a hollow box, 3 to 7 blocks along each side. Its edges and corners are
 * casing; its faces are casing or components; its inside is empty. It needs exactly one
 * core and one platform module, at least one drive bay, memory bank and vent, and one
 * vent for every {@value #COMPONENTS_PER_VENT} bays and banks.
 */
public final class RackValidator {
	public static final int MIN_SIZE = 3;
	public static final int MAX_SIZE = 7;
	public static final int COMPONENTS_PER_VENT = 4;

	private static final int[][] NEIGHBOURS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	private RackValidator() {
	}

	/** Reads the world one position at a time. */
	@FunctionalInterface
	public interface View {
		Cell at(Pos pos);
	}

	public static RackScan scan(View view, Pos core) {
		// 1. Collect every rack block connected to the core, without wandering further
		//    than a maximum-size rack could reach.
		Set<Pos> connected = new HashSet<>();
		Deque<Pos> queue = new ArrayDeque<>();
		connected.add(core);
		queue.add(core);
		boolean overflow = false;
		int reach = MAX_SIZE - 1;
		while (!queue.isEmpty()) {
			Pos pos = queue.poll();
			for (int[] d : NEIGHBOURS) {
				Pos next = pos.offset(d[0], d[1], d[2]);
				if (connected.contains(next) || !view.at(next).isPart()) {
					continue;
				}
				if (Math.abs(next.x() - core.x()) > reach || Math.abs(next.y() - core.y()) > reach
						|| Math.abs(next.z() - core.z()) > reach) {
					overflow = true;
					continue;
				}
				connected.add(next);
				queue.add(next);
			}
		}

		// 2. The box those blocks span is the rack's claimed outline.
		int minX = core.x(), minY = core.y(), minZ = core.z();
		int maxX = minX, maxY = minY, maxZ = minZ;
		for (Pos pos : connected) {
			minX = Math.min(minX, pos.x());
			minY = Math.min(minY, pos.y());
			minZ = Math.min(minZ, pos.z());
			maxX = Math.max(maxX, pos.x());
			maxY = Math.max(maxY, pos.y());
			maxZ = Math.max(maxZ, pos.z());
		}
		int sx = maxX - minX + 1, sy = maxY - minY + 1, sz = maxZ - minZ + 1;
		if (overflow || sx > MAX_SIZE || sy > MAX_SIZE || sz > MAX_SIZE) {
			return RackScan.fail(Problem.TOO_LARGE, null);
		}
		if (sx < MIN_SIZE || sy < MIN_SIZE || sz < MIN_SIZE) {
			return RackScan.fail(Problem.TOO_SMALL, null);
		}

		// 3. Check every position of the box and tally the components.
		List<Pos> members = new ArrayList<>();
		List<Pos> driveBays = new ArrayList<>();
		Pos platformPos = null;
		Platform platform = null;
		CasingTier tier = null;
		int cores = 0, platforms = 0, memory = 0, vents = 0;
		List<Pos> gateways = new ArrayList<>();
		List<Pos> displays = new ArrayList<>();
		Pos extraCore = null;
		Pos extraPlatform = null;
		for (int y = minY; y <= maxY; y++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int x = minX; x <= maxX; x++) {
					Pos pos = new Pos(x, y, z);
					Cell cell = view.at(pos);
					int extremes = (x == minX || x == maxX ? 1 : 0) + (y == minY || y == maxY ? 1 : 0)
							+ (z == minZ || z == maxZ ? 1 : 0);
					if (extremes == 0) {
						if (!cell.empty()) {
							return RackScan.fail(Problem.NOT_HOLLOW, pos);
						}
						continue;
					}
					if (!cell.isPart()) {
						return RackScan.fail(Problem.MISSING_BLOCK, pos);
					}
					if (extremes >= 2 && cell.kind() != PartKind.CASING) {
						return RackScan.fail(Problem.EDGE_NOT_CASING, pos);
					}
					members.add(pos);
					switch (cell.kind()) {
						case CASING -> {
							if (tier == null || cell.tier().ordinal() < tier.ordinal()) {
								tier = cell.tier();
							}
						}
						case CORE -> {
							cores++;
							if (!pos.equals(core)) {
								extraCore = pos;
							}
						}
						case PLATFORM -> {
							platforms++;
							if (platformPos == null) {
								platformPos = pos;
								platform = cell.platform();
							} else {
								extraPlatform = pos;
							}
						}
						case DRIVE_BAY -> driveBays.add(pos);
						case MEMORY_BANK -> memory++;
						case VENT -> vents++;
						case GATEWAY -> gateways.add(pos);
						case DISPLAY -> displays.add(pos);
					}
				}
			}
		}

		// 4. Component rules, in the order a builder would naturally fix them.
		if (cores > 1) {
			return RackScan.fail(Problem.MULTIPLE_CORES, extraCore);
		}
		if (platforms == 0) {
			return RackScan.fail(Problem.NO_PLATFORM, null);
		}
		if (platforms > 1) {
			return RackScan.fail(Problem.MULTIPLE_PLATFORMS, extraPlatform);
		}
		if (driveBays.isEmpty()) {
			return RackScan.fail(Problem.NO_DRIVE_BAY, null);
		}
		if (memory == 0) {
			return RackScan.fail(Problem.NO_MEMORY, null);
		}
		if (vents == 0) {
			return RackScan.fail(Problem.NO_VENT, null);
		}
		if (vents < ventsNeeded(driveBays.size(), memory)) {
			return RackScan.fail(Problem.NOT_ENOUGH_VENTS, null);
		}

		return RackScan.ok(new RackStructure(new Pos(minX, minY, minZ), new Pos(maxX, maxY, maxZ), core, platformPos,
				platform, tier, members, driveBays, memory, vents, gateways, displays));
	}

	public static int ventsNeeded(int driveBays, int memoryBanks) {
		return Math.max(1, (driveBays + memoryBanks + COMPONENTS_PER_VENT - 1) / COMPONENTS_PER_VENT);
	}
}
