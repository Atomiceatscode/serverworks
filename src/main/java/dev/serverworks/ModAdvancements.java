package dev.serverworks;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Grants the Serverworks advancements that no vanilla trigger can detect. */
public final class ModAdvancements {
	public static final String BUILD_RACK = "build_rack";
	public static final String NETHERITE_RACK = "netherite_rack";
	public static final String START_SERVER = "start_server";
	public static final String GO_THROUGH = "go_through";
	public static final String PROXY = "proxy";

	private ModAdvancements() {
	}

	public static void award(ServerPlayer player, String name) {
		AdvancementHolder holder = player.level().getServer().getAdvancements().get(Serverworks.id("main/" + name));
		if (holder != null) {
			player.getAdvancements().award(holder, "done");
		}
	}

	/** Grants an advancement to everyone close enough to have built what happened here. */
	public static void awardNear(ServerLevel level, BlockPos pos, String name) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 16 * 16) {
				award(player, name);
			}
		}
	}
}
