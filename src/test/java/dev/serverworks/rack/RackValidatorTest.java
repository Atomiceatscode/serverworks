package dev.serverworks.rack;

import dev.serverworks.rack.RackScan.Problem;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RackValidatorTest {
	/** A tiny world: anything not set is air. */
	private static final class World implements RackValidator.View {
		final Map<Pos, Cell> cells = new HashMap<>();

		@Override
		public Cell at(Pos pos) {
			return cells.getOrDefault(pos, Cell.AIR);
		}

		void set(int x, int y, int z, Cell cell) {
			cells.put(new Pos(x, y, z), cell);
		}

		/** Builds a hollow casing box with its minimum corner at the origin. */
		World shell(int sx, int sy, int sz, CasingTier tier) {
			for (int x = 0; x < sx; x++) {
				for (int y = 0; y < sy; y++) {
					for (int z = 0; z < sz; z++) {
						boolean inside = x > 0 && x < sx - 1 && y > 0 && y < sy - 1 && z > 0 && z < sz - 1;
						if (!inside) {
							set(x, y, z, Cell.casing(tier));
						}
					}
				}
			}
			return this;
		}
	}

	private static final Pos CORE = new Pos(1, 1, 0);

	/** The smallest working rack: one of each required component on face centres of a 3x3x3 box. */
	private static World minimal(CasingTier tier) {
		World world = new World().shell(3, 3, 3, tier);
		world.set(1, 1, 0, Cell.part(PartKind.CORE));
		world.set(1, 1, 2, Cell.platform(Platform.PAPER));
		world.set(0, 1, 1, Cell.part(PartKind.DRIVE_BAY));
		world.set(2, 1, 1, Cell.part(PartKind.MEMORY_BANK));
		world.set(1, 2, 1, Cell.part(PartKind.VENT));
		return world;
	}

	@Test
	void smallestRackForms() {
		RackScan scan = RackValidator.scan(minimal(CasingTier.IRON), CORE);

		assertTrue(scan.formed(), scan.problem().toString());
		RackStructure rack = scan.structure();
		assertEquals(new Pos(0, 0, 0), rack.min());
		assertEquals(new Pos(2, 2, 2), rack.max());
		assertEquals(26, rack.members().size());
		assertEquals(Platform.PAPER, rack.platform());
		assertEquals(new Pos(1, 1, 2), rack.platformPos());
		assertEquals(List.of(new Pos(0, 1, 1)), rack.driveBays());
		assertEquals(8, rack.diskSlots());
		assertEquals(CasingTier.IRON, rack.tier());
		assertEquals(2048, rack.ramMb());
		assertEquals(10, rack.maxPlayers());
	}

	@Test
	void nonCubicRackForms() {
		World world = new World().shell(5, 4, 3, CasingTier.DIAMOND);
		world.set(2, 1, 0, Cell.part(PartKind.CORE));
		world.set(1, 1, 0, Cell.platform(Platform.FABRIC));
		world.set(3, 1, 0, Cell.part(PartKind.DRIVE_BAY));
		world.set(1, 2, 0, Cell.part(PartKind.MEMORY_BANK));
		world.set(2, 3, 1, Cell.part(PartKind.VENT));
		world.set(3, 2, 0, Cell.part(PartKind.DISPLAY));

		RackScan scan = RackValidator.scan(world, new Pos(2, 1, 0));

		assertTrue(scan.formed(), scan.problem().toString());
		assertEquals(5, scan.structure().sizeX());
		assertEquals(4, scan.structure().sizeY());
		assertEquals(3, scan.structure().sizeZ());
		assertEquals(List.of(new Pos(3, 2, 0)), scan.structure().displays());
	}

	@Test
	void loneCoreIsTooSmall() {
		World world = new World();
		world.set(0, 0, 0, Cell.part(PartKind.CORE));
		RackScan scan = RackValidator.scan(world, new Pos(0, 0, 0));
		assertEquals(Problem.TOO_SMALL, scan.problem());
		assertNull(scan.structure());
	}

	@Test
	void missingCornerIsReportedWithItsPosition() {
		World world = minimal(CasingTier.IRON);
		world.cells.remove(new Pos(2, 2, 2));
		RackScan scan = RackValidator.scan(world, CORE);
		assertEquals(Problem.MISSING_BLOCK, scan.problem());
		assertEquals(new Pos(2, 2, 2), scan.problemPos());
	}

	@Test
	void foreignBlockInTheShellCountsAsMissing() {
		World world = minimal(CasingTier.IRON);
		world.set(0, 0, 0, Cell.OBSTRUCTION);
		RackScan scan = RackValidator.scan(world, CORE);
		assertEquals(Problem.MISSING_BLOCK, scan.problem());
		assertEquals(new Pos(0, 0, 0), scan.problemPos());
	}

	@Test
	void componentsMayNotSitOnEdges() {
		World world = minimal(CasingTier.IRON);
		world.set(0, 0, 1, Cell.part(PartKind.VENT));
		RackScan scan = RackValidator.scan(world, CORE);
		assertEquals(Problem.EDGE_NOT_CASING, scan.problem());
		assertEquals(new Pos(0, 0, 1), scan.problemPos());
	}

	@Test
	void insideMustBeEmpty() {
		World world = minimal(CasingTier.IRON);
		world.set(1, 1, 1, Cell.OBSTRUCTION);
		assertEquals(Problem.NOT_HOLLOW, RackValidator.scan(world, CORE).problem());

		world.set(1, 1, 1, Cell.casing(CasingTier.IRON));
		RackScan scan = RackValidator.scan(world, CORE);
		assertEquals(Problem.NOT_HOLLOW, scan.problem());
		assertEquals(new Pos(1, 1, 1), scan.problemPos());
	}

	@Test
	void requiredComponentsAreChecked() {
		World noPlatform = minimal(CasingTier.IRON);
		noPlatform.set(1, 1, 2, Cell.casing(CasingTier.IRON));
		assertEquals(Problem.NO_PLATFORM, RackValidator.scan(noPlatform, CORE).problem());

		World noBay = minimal(CasingTier.IRON);
		noBay.set(0, 1, 1, Cell.casing(CasingTier.IRON));
		assertEquals(Problem.NO_DRIVE_BAY, RackValidator.scan(noBay, CORE).problem());

		World noMemory = minimal(CasingTier.IRON);
		noMemory.set(2, 1, 1, Cell.casing(CasingTier.IRON));
		assertEquals(Problem.NO_MEMORY, RackValidator.scan(noMemory, CORE).problem());

		World noVent = minimal(CasingTier.IRON);
		noVent.set(1, 2, 1, Cell.casing(CasingTier.IRON));
		assertEquals(Problem.NO_VENT, RackValidator.scan(noVent, CORE).problem());
	}

	@Test
	void onlyOneCoreAndOnePlatform() {
		World twoCores = minimal(CasingTier.IRON);
		twoCores.set(1, 0, 1, Cell.part(PartKind.CORE));
		RackScan cores = RackValidator.scan(twoCores, CORE);
		assertEquals(Problem.MULTIPLE_CORES, cores.problem());
		assertEquals(new Pos(1, 0, 1), cores.problemPos());

		World twoPlatforms = minimal(CasingTier.IRON);
		twoPlatforms.set(1, 0, 1, Cell.platform(Platform.FABRIC));
		assertEquals(Problem.MULTIPLE_PLATFORMS, RackValidator.scan(twoPlatforms, CORE).problem());
	}

	@Test
	void ventsMustKeepUpWithComponents() {
		assertEquals(1, RackValidator.ventsNeeded(1, 1));
		assertEquals(1, RackValidator.ventsNeeded(2, 2));
		assertEquals(2, RackValidator.ventsNeeded(3, 2));
		assertEquals(3, RackValidator.ventsNeeded(8, 1));

		World world = new World().shell(5, 5, 3, CasingTier.IRON);
		world.set(2, 2, 0, Cell.part(PartKind.CORE));
		world.set(1, 1, 0, Cell.platform(Platform.PAPER));
		world.set(3, 1, 0, Cell.part(PartKind.VENT));
		for (int[] p : new int[][] {{1, 2}, {1, 3}, {2, 1}}) {
			world.set(p[0], p[1], 0, Cell.part(PartKind.DRIVE_BAY));
		}
		world.set(2, 3, 0, Cell.part(PartKind.MEMORY_BANK));
		world.set(3, 2, 0, Cell.part(PartKind.MEMORY_BANK));
		assertEquals(Problem.NOT_ENOUGH_VENTS, RackValidator.scan(world, new Pos(2, 2, 0)).problem());

		world.set(3, 3, 0, Cell.part(PartKind.VENT));
		RackScan fixed = RackValidator.scan(world, new Pos(2, 2, 0));
		assertTrue(fixed.formed(), fixed.problem().toString());
		assertEquals(24, fixed.structure().diskSlots());
		assertEquals(3072, fixed.structure().ramMb());
	}

	@Test
	void rackIsOnlyAsGoodAsItsWeakestCasing() {
		World world = minimal(CasingTier.NETHERITE);
		assertEquals(CasingTier.NETHERITE, RackValidator.scan(world, CORE).structure().tier());
		assertEquals(100, RackValidator.scan(world, CORE).structure().maxPlayers());

		world.set(0, 0, 0, Cell.casing(CasingTier.IRON));
		assertEquals(CasingTier.IRON, RackValidator.scan(world, CORE).structure().tier());
	}

	@Test
	void memoryIsCappedByTier() {
		World world = new World().shell(7, 7, 3, CasingTier.IRON);
		world.set(3, 3, 0, Cell.part(PartKind.CORE));
		world.set(1, 1, 0, Cell.platform(Platform.PAPER));
		world.set(2, 1, 0, Cell.part(PartKind.DRIVE_BAY));
		for (int x = 1; x <= 5; x++) {
			world.set(x, 5, 0, Cell.part(PartKind.MEMORY_BANK));
		}
		world.set(3, 1, 0, Cell.part(PartKind.VENT));
		world.set(4, 1, 0, Cell.part(PartKind.VENT));
		RackScan scan = RackValidator.scan(world, new Pos(3, 3, 0));
		assertTrue(scan.formed(), scan.problem().toString());
		assertEquals(4096, scan.structure().ramMb());
	}

	@Test
	void oversizedOrTouchingRacksDoNotForm() {
		World tooLong = new World().shell(8, 3, 3, CasingTier.IRON);
		tooLong.set(1, 1, 0, Cell.part(PartKind.CORE));
		assertEquals(Problem.TOO_LARGE, RackValidator.scan(tooLong, CORE).problem());

		// A stray casing touching the rack stretches the outline, so the frame no longer closes.
		World stray = minimal(CasingTier.IRON);
		stray.set(3, 1, 1, Cell.casing(CasingTier.IRON));
		RackScan scan = RackValidator.scan(stray, CORE);
		assertFalse(scan.formed());
		assertEquals(Problem.MISSING_BLOCK, scan.problem());
	}

	@Test
	void platformsAcceptTheRightContent() {
		assertTrue(Platform.PAPER.accepts(List.of("bukkit")));
		assertTrue(Platform.QUILT.accepts(List.of("fabric")));
		assertFalse(Platform.FABRIC.accepts(List.of("quilt")));
		assertFalse(Platform.FABRIC.accepts(List.of("paper", "spigot")));
		assertTrue(Platform.VANILLA.accepts(List.of("datapack", "fabric")));
		assertFalse(Platform.VELOCITY.accepts(List.of("datapack")));
		assertEquals(Platform.NEOFORGE, Platform.byId("neoforge"));
		assertNull(Platform.byId("nope"));
	}
}
