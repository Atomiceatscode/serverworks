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
}
