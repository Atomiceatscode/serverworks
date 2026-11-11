package dev.serverworks.rack;

/**
 * What the rack rules see at one position: empty space, a rack part, or anything else.
 *
 * @param kind     null for empty space and for blocks that are not rack parts
 * @param tier     set for casings only
 * @param platform set for platform modules only
 */
public record Cell(boolean empty, PartKind kind, CasingTier tier, Platform platform) {
	public static final Cell AIR = new Cell(true, null, null, null);
	public static final Cell OBSTRUCTION = new Cell(false, null, null, null);

	public static Cell part(PartKind kind) {
		return new Cell(false, kind, null, null);
	}

	public static Cell casing(CasingTier tier) {
		return new Cell(false, PartKind.CASING, tier, null);
	}

	public static Cell platform(Platform platform) {
		return new Cell(false, PartKind.PLATFORM, null, platform);
	}

	public boolean isPart() {
		return kind != null;
	}
}
