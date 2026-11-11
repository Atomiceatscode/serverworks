package dev.serverworks.client.sound;

import dev.serverworks.machine.RackComponentBlock;
import dev.serverworks.machine.ServerCoreBlockEntity;
import dev.serverworks.registry.ModSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** The steady hum of a running rack, heard near its Server Core. */
public final class RackHum {
	private static final double HEARING_DISTANCE = 20;
	private static final Set<BlockPos> CORES = new HashSet<>();
	private static final Map<BlockPos, Loop> PLAYING = new HashMap<>();

	private RackHum() {
	}

	public static void register() {
		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof ServerCoreBlockEntity) {
				CORES.add(blockEntity.getBlockPos().immutable());
			}
		});
		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof ServerCoreBlockEntity) {
				CORES.remove(blockEntity.getBlockPos());
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(RackHum::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			CORES.clear();
			PLAYING.clear();
			return;
		}
		PLAYING.values().removeIf(loop -> loop.isStopped() || !minecraft.getSoundManager().isActive(loop));
		for (BlockPos pos : CORES) {
			if (!PLAYING.containsKey(pos) && running(minecraft.level.getBlockState(pos))
					&& minecraft.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < HEARING_DISTANCE * HEARING_DISTANCE) {
				Loop loop = new Loop(pos);
				PLAYING.put(pos, loop);
				minecraft.getSoundManager().play(loop);
			}
		}
	}

	private static boolean running(BlockState state) {
		return state.hasProperty(RackComponentBlock.ACTIVE) && state.getValue(RackComponentBlock.ACTIVE);
	}

	/** Loops while the core stays active and in earshot, fading in and out. */
	private static final class Loop extends AbstractTickableSoundInstance {
		private final BlockPos core;
		private float fade;

		Loop(BlockPos core) {
			super(ModSounds.RACK_HUM, SoundSource.BLOCKS, RandomSource.create());
			this.core = core;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.01F;
			this.attenuation = SoundInstance.Attenuation.LINEAR;
			this.x = core.getX() + 0.5;
			this.y = core.getY() + 0.5;
			this.z = core.getZ() + 0.5;
		}

		@Override
		public void tick() {
			Minecraft minecraft = Minecraft.getInstance();
			boolean keep = minecraft.level != null && minecraft.player != null && running(minecraft.level.getBlockState(core))
					&& minecraft.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(core)) < (HEARING_DISTANCE + 4) * (HEARING_DISTANCE + 4);
			fade = Math.clamp(fade + (keep ? 0.05F : -0.08F), 0F, 1F);
			volume = Math.max(0.01F, fade * 0.6F);
			if (!keep && fade <= 0F) {
				stop();
			}
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}
}
