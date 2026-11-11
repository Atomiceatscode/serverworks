package dev.serverworks.rack;

import java.util.List;

/**
 * A complete, valid rack.
 *
 * @param members every rack block in the shell, including the core
 * @param tier    the weakest casing tier in the frame
 */
public record RackStructure(
		Pos min,
		Pos max,
		Pos core,
		Pos platformPos,
		Platform platform,
		CasingTier tier,
		List<Pos> members,
		List<Pos> driveBays,
		int memoryBanks,
		int vents,
		List<Pos> gateways,
		List<Pos> displays) {

	public static final int DISKS_PER_BAY = 8;
	private static final int BASE_RAM_MB = 1024;
	private static final int RAM_PER_BANK_MB = 1024;

	public RackStructure {
		members = List.copyOf(members);
		driveBays = List.copyOf(driveBays);
		gateways = List.copyOf(gateways);
		displays = List.copyOf(displays);
	}

	public int sizeX() {
		return max.x() - min.x() + 1;
	}

	public int sizeY() {
		return max.y() - min.y() + 1;
	}

	public int sizeZ() {
		return max.z() - min.z() + 1;
	}

	public boolean contains(Pos pos) {
		return pos.x() >= min.x() && pos.x() <= max.x()
				&& pos.y() >= min.y() && pos.y() <= max.y()
				&& pos.z() >= min.z() && pos.z() <= max.z();
	}

	public int diskSlots() {
		return driveBays.size() * DISKS_PER_BAY;
	}

	/** Memory the child server gets: a base amount plus each bank, up to what the frame allows. */
	public int ramMb() {
		return Math.min(tier.maxRamMb(), BASE_RAM_MB + memoryBanks * RAM_PER_BANK_MB);
	}

	public int maxPlayers() {
		return tier.maxPlayers();
	}
}
