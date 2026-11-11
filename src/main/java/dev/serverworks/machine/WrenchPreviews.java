package dev.serverworks.machine;

import dev.serverworks.rack.Pos;
import dev.serverworks.rack.RackPreview;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a Wrench's build preview on screen for a few seconds: an outline around every block
 * the rack still needs (blue), every block in the wrong place (orange) and every block that
 * must be cleared from the inside (red). Only the player who used the Wrench sees it.
 */
public final class WrenchPreviews {
	private static final int DURATION_TICKS = 120;
	private static final int REDRAW_TICKS = 10;
	private static final DustParticleOptions MISSING = new DustParticleOptions(0x4FD0FF, 0.8F);
	private static final DustParticleOptions WRONG = new DustParticleOptions(0xFFA030, 0.9F);
	private static final DustParticleOptions CLEAR = new DustParticleOptions(0xFF4030, 0.9F);

	private record Preview(ServerLevel level, List<RackPreview.Hint> hints, long until) {
	}

	private static final Map<UUID, Preview> ACTIVE = new ConcurrentHashMap<>();

	private WrenchPreviews() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (ACTIVE.isEmpty() || server.getTickCount() % REDRAW_TICKS != 0) {
				return;
			}
			ACTIVE.entrySet().removeIf(entry -> {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				Preview preview = entry.getValue();
				if (player == null || player.level() != preview.level() || preview.level().getGameTime() > preview.until()) {
					return true;
				}
				draw(player, preview);
				return false;
			});
		});
	}

	public static void show(ServerPlayer player, ServerLevel level, List<RackPreview.Hint> hints) {
		if (hints.isEmpty()) {
			ACTIVE.remove(player.getUUID());
			return;
		}
		Preview preview = new Preview(level, List.copyOf(hints), level.getGameTime() + DURATION_TICKS);
		ACTIVE.put(player.getUUID(), preview);
		draw(player, preview);
	}

	private static void draw(ServerPlayer player, Preview preview) {
		for (RackPreview.Hint hint : preview.hints()) {
			DustParticleOptions particle = switch (hint.kind()) {
				case MISSING -> MISSING;
				case WRONG -> WRONG;
				case CLEAR -> CLEAR;
			};
			outline(player, preview.level(), hint.pos(), particle);
		}
	}

	/** The twelve edges of a block, a few motes each. */
	private static void outline(ServerPlayer player, ServerLevel level, Pos pos, DustParticleOptions particle) {
		double x = pos.x();
		double y = pos.y();
		double z = pos.z();
		for (int i = 0; i <= 3; i++) {
			double t = 0.05 + i * 0.3;
			for (int a = 0; a <= 1; a++) {
				for (int b = 0; b <= 1; b++) {
					spawn(player, level, particle, x + t, y + a, z + b);
					spawn(player, level, particle, x + a, y + t, z + b);
					spawn(player, level, particle, x + a, y + b, z + t);
				}
			}
		}
	}

	private static void spawn(ServerPlayer player, ServerLevel level, DustParticleOptions particle, double x, double y,
			double z) {
		level.sendParticles(player, particle, true, false, x, y, z, 1, 0, 0, 0, 0);
	}
}
