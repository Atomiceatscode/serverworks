package dev.serverworks.rack;

import dev.serverworks.rack.RackPreview.Hint;
import dev.serverworks.rack.RackPreview.Kind;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RackPreviewTest {
	private static final class World implements RackValidator.View {
		final Map<Pos, Cell> cells = new HashMap<>();

		@Override
		public Cell at(Pos pos) {
			return cells.getOrDefault(pos, Cell.AIR);
		}
	}

	@Test
	void aLoneCoreGetsTheSmallestRackBehindIt() {
		World world = new World();
		Pos core = new Pos(10, 64, 10);
		world.cells.put(core, Cell.part(PartKind.CORE));

		// The core faces north (toward -z), so the rack extends south of it.
		List<Hint> hints = RackPreview.hints(world, core, new Pos(0, 0, 1));

		assertEquals(25, hints.size(), "every shell position but the core's own");
		assertTrue(hints.stream().allMatch(h -> h.kind() == Kind.MISSING));
		assertTrue(hints.stream().allMatch(h -> h.pos().z() >= 10 && h.pos().z() <= 12));
		assertTrue(hints.stream().noneMatch(h -> h.pos().equals(new Pos(10, 64, 11))), "the middle stays empty");
	}

	@Test
	void aPartlyBuiltRackShowsEveryGapAndMistake() {
		World world = new World();
		for (int x = 0; x < 3; x++) {
			for (int y = 0; y < 3; y++) {
				for (int z = 0; z < 3; z++) {
					if (!(x == 1 && y == 1 && z == 1)) {
						world.cells.put(new Pos(x, y, z), Cell.casing(CasingTier.IRON));
					}
				}
			}
		}
		Pos core = new Pos(1, 1, 0);
		world.cells.put(core, Cell.part(PartKind.CORE));
		world.cells.remove(new Pos(2, 2, 2));
		world.cells.remove(new Pos(0, 2, 2));
		world.cells.put(new Pos(0, 0, 0), Cell.part(PartKind.VENT));
		world.cells.put(new Pos(1, 1, 1), Cell.OBSTRUCTION);

		List<Hint> hints = RackPreview.hints(world, core, new Pos(0, 0, 1));

		assertTrue(hints.contains(new Hint(new Pos(2, 2, 2), Kind.MISSING)));
		assertTrue(hints.contains(new Hint(new Pos(0, 2, 2), Kind.MISSING)));
		assertTrue(hints.contains(new Hint(new Pos(0, 0, 0), Kind.WRONG)));
		assertTrue(hints.contains(new Hint(new Pos(1, 1, 1), Kind.CLEAR)));
		assertEquals(4, hints.size());
	}

	@Test
	void aFormedRackNeedsNoHints() {
		World world = new World();
		for (int x = 0; x < 3; x++) {
			for (int y = 0; y < 3; y++) {
				for (int z = 0; z < 3; z++) {
					if (!(x == 1 && y == 1 && z == 1)) {
						world.cells.put(new Pos(x, y, z), Cell.casing(CasingTier.IRON));
					}
				}
			}
		}
		world.cells.put(new Pos(1, 1, 0), Cell.part(PartKind.CORE));
		world.cells.put(new Pos(1, 1, 2), Cell.platform(Platform.PAPER));
		world.cells.put(new Pos(0, 1, 1), Cell.part(PartKind.DRIVE_BAY));
		world.cells.put(new Pos(2, 1, 1), Cell.part(PartKind.MEMORY_BANK));
		world.cells.put(new Pos(1, 2, 1), Cell.part(PartKind.VENT));
		assertTrue(RackPreview.hints(world, new Pos(1, 1, 0), new Pos(0, 0, 1)).isEmpty());
	}
}
