package dev.serverworks.machine;

import dev.serverworks.item.DiskLabel;
import dev.serverworks.rack.CasingTier;
import dev.serverworks.rack.Platform;
import dev.serverworks.rack.RackScan;
import dev.serverworks.rack.RackScan.Problem;
import dev.serverworks.rack.RackStructure;
import dev.serverworks.rack.RackValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the Server Core screen shows about a rack, as sent from server to client.
 *
 * @param problemPos the block to fix, when the problem is about one block
 * @param platform   null until the rack is formed
 * @param tier       null until the rack is formed
 * @param disks      the labels of every disk in the rack's drive bays
 */
public record RackStatus(
		Problem problem,
		@Nullable BlockPos problemPos,
		@Nullable Platform platform,
		@Nullable CasingTier tier,
		int sizeX,
		int sizeY,
		int sizeZ,
		int driveBays,
		int memoryBanks,
		int vents,
		int ramMb,
		int maxPlayers,
		List<DiskLabel> disks,
		int linkCount,
		boolean redstoneControl) {

	/** Far more than the largest rack can hold; a bound for reading untrusted lengths. */
	private static final int MAX_DISKS = 2048;

	public static final RackStatus UNKNOWN = new RackStatus(Problem.TOO_SMALL, null, null, null, 0, 0, 0, 0, 0, 0, 0, 0,
			List.of(), 0, false);

	public RackStatus {
		disks = List.copyOf(disks);
	}

	public static RackStatus of(RackScan scan, List<DiskLabel> disks, int linkCount, boolean redstoneControl) {
		if (!scan.formed()) {
			BlockPos pos = scan.problemPos() == null ? null
					: new BlockPos(scan.problemPos().x(), scan.problemPos().y(), scan.problemPos().z());
			return new RackStatus(scan.problem(), pos, null, null, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), 0, redstoneControl);
		}
		RackStructure rack = scan.structure();
		return new RackStatus(Problem.NONE, null, rack.platform(), rack.tier(), rack.sizeX(), rack.sizeY(), rack.sizeZ(),
				rack.driveBays().size(), rack.memoryBanks(), rack.vents(), rack.ramMb(), rack.maxPlayers(), disks, linkCount,
				redstoneControl);
	}

	public boolean formed() {
		return problem == Problem.NONE;
	}

	public int diskSlots() {
		return driveBays * RackStructure.DISKS_PER_BAY;
	}

	public int ventsNeeded() {
		return RackValidator.ventsNeeded(driveBays, memoryBanks);
	}

	/** True when the rack's platform can run this disk's project. */
	public boolean accepts(DiskLabel disk) {
		return platform != null && platform.accepts(disk.loaders());
	}

	public void write(FriendlyByteBuf out) {
		out.writeEnum(problem);
		out.writeBoolean(problemPos != null);
		if (problemPos != null) {
			out.writeBlockPos(problemPos);
		}
		out.writeVarInt(platform == null ? 0 : platform.ordinal() + 1);
		out.writeVarInt(tier == null ? 0 : tier.ordinal() + 1);
		out.writeVarInt(sizeX);
		out.writeVarInt(sizeY);
		out.writeVarInt(sizeZ);
		out.writeVarInt(driveBays);
		out.writeVarInt(memoryBanks);
		out.writeVarInt(vents);
		out.writeVarInt(ramMb);
		out.writeVarInt(maxPlayers);
		out.writeVarInt(disks.size());
		for (DiskLabel disk : disks) {
			DiskLabel.STREAM_CODEC.encode(out, disk);
		}
		out.writeVarInt(linkCount);
		out.writeBoolean(redstoneControl);
	}

	public static RackStatus read(FriendlyByteBuf in) {
		Problem problem = in.readEnum(Problem.class);
		BlockPos problemPos = in.readBoolean() ? in.readBlockPos() : null;
		Platform platform = pick(Platform.values(), in.readVarInt());
		CasingTier tier = pick(CasingTier.values(), in.readVarInt());
		int sizeX = in.readVarInt();
		int sizeY = in.readVarInt();
		int sizeZ = in.readVarInt();
		int driveBays = in.readVarInt();
		int memoryBanks = in.readVarInt();
		int vents = in.readVarInt();
		int ramMb = in.readVarInt();
		int maxPlayers = in.readVarInt();
		int count = in.readVarInt();
		if (count < 0 || count > MAX_DISKS) {
			throw new IllegalArgumentException("Unreasonable disk count " + count);
		}
		List<DiskLabel> disks = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			disks.add(DiskLabel.STREAM_CODEC.decode(in));
		}
		return new RackStatus(problem, problemPos, platform, tier, sizeX, sizeY, sizeZ, driveBays, memoryBanks, vents,
				ramMb, maxPlayers, disks, in.readVarInt(), in.readBoolean());
	}

	private static <T> @Nullable T pick(T[] values, int indexPlusOne) {
		return indexPlusOne >= 1 && indexPlusOne <= values.length ? values[indexPlusOne - 1] : null;
	}
}
