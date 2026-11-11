package dev.serverworks.machine;

import dev.serverworks.rack.RackValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The loaded Server Cores, so that a rack block can find the cores it might belong to
 * without searching the world around it.
 */
public final class RackCores {
	private static final Set<ServerCoreBlockEntity> LOADED = ConcurrentHashMap.newKeySet();
	/** A changed block can only matter to a core within one maximum-size rack of it. */
	private static final int RANGE = RackValidator.MAX_SIZE;

	private RackCores() {
	}

	static void add(ServerCoreBlockEntity core) {
		LOADED.add(core);
	}

	static void remove(ServerCoreBlockEntity core) {
		LOADED.remove(core);
	}

	/** Asks every core close enough to care to re-check its structure on its next tick. */
	public static void markDirtyNear(ServerLevel level, BlockPos pos) {
		for (ServerCoreBlockEntity core : LOADED) {
			if (core.getLevel() == level && within(core.getBlockPos(), pos, RANGE)) {
				core.markDirty();
			}
		}
	}

	/** The core whose formed rack includes this position, if any. */
	public static @Nullable ServerCoreBlockEntity owning(ServerLevel level, BlockPos pos) {
		for (ServerCoreBlockEntity core : LOADED) {
			if (core.getLevel() == level && !core.isRemoved() && core.contains(pos)) {
				return core;
			}
		}
		return null;
	}

	/** A loaded core by its rack id, in any dimension. */
	public static @Nullable ServerCoreBlockEntity byRackId(java.util.UUID rackId) {
		for (ServerCoreBlockEntity core : LOADED) {
			if (!core.isRemoved() && core.rackId().equals(rackId)) {
				return core;
			}
		}
		return null;
	}

	/** The closest core within reach of this position, formed or not. */
	public static @Nullable ServerCoreBlockEntity nearest(ServerLevel level, BlockPos pos) {
		ServerCoreBlockEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (ServerCoreBlockEntity core : LOADED) {
			if (core.getLevel() != level || core.isRemoved() || !within(core.getBlockPos(), pos, RANGE)) {
				continue;
			}
			double distance = core.getBlockPos().distSqr(pos);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = core;
			}
		}
		return best;
	}

	private static boolean within(BlockPos a, BlockPos b, int range) {
		return Math.abs(a.getX() - b.getX()) <= range && Math.abs(a.getY() - b.getY()) <= range
				&& Math.abs(a.getZ() - b.getZ()) <= range;
	}
}
