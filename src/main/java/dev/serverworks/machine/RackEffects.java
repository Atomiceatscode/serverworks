package dev.serverworks.machine;

import dev.serverworks.host.ServerInstance.State;
import dev.serverworks.rack.Pos;
import dev.serverworks.rack.RackStructure;
import dev.serverworks.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The sounds and particles that show what a rack's server is doing, seen from outside. */
final class RackEffects {
	private RackEffects() {
	}

	/**
	 * Called every output update (twice a second).
	 *
	 * @param before the state at the previous update, or null the first time after loading
	 */
	static void update(ServerLevel level, BlockPos core, RackStructure rack, @Nullable State before, State now, int tick) {
		if (before != null && before != now) {
			switch (now) {
				case RUNNING -> {
					play(level, core, ModSounds.RACK_BOOT, 1.0F, 1.0F);
					for (Pos gateway : rack.gateways()) {
						play(level, block(gateway), ModSounds.GATEWAY_OPEN, 0.8F, 1.0F);
					}
				}
				case STOPPED -> play(level, core, ModSounds.RACK_POWER_DOWN, 0.9F, 1.0F);
				case CRASHED, FAILED -> {
					play(level, core, ModSounds.RACK_ALARM, 1.0F, 1.0F);
					burst(level, core);
					for (Pos vent : rack.members()) {
						if (level.getBlockState(block(vent)).getBlock() instanceof RackComponentBlock component
								&& component.kind() == dev.serverworks.rack.PartKind.VENT) {
							burst(level, block(vent));
						}
					}
				}
				default -> {
				}
			}
		}
		if (now == State.DOWNLOADING || now == State.INSTALLING || now == State.RESOLVING) {
			// Data streams from the drive bays into the core while the server is being prepared.
			for (Pos bay : rack.driveBays()) {
				stream(level, block(bay), core);
			}
			if (tick % 2 == 0 && !rack.driveBays().isEmpty()) {
				Pos bay = rack.driveBays().get((tick / 2) % rack.driveBays().size());
				play(level, block(bay), ModSounds.DRIVE_SEEK, 0.6F, 0.9F + level.getRandom().nextFloat() * 0.2F);
			}
		}
	}

	private static void stream(ServerLevel level, BlockPos from, BlockPos to) {
		Vec3 start = Vec3.atCenterOf(from);
		Vec3 path = Vec3.atCenterOf(to).subtract(start);
		for (int i = 0; i < 3; i++) {
			double along = level.getRandom().nextDouble() * 0.3;
			Vec3 at = start.add(path.scale(along)).add((level.getRandom().nextDouble() - 0.5) * 0.4,
					(level.getRandom().nextDouble() - 0.5) * 0.4, (level.getRandom().nextDouble() - 0.5) * 0.4);
			Vec3 velocity = path.normalize().scale(0.12);
			// A count of zero makes the offsets a velocity, so each mote travels toward the core.
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 0, velocity.x, velocity.y, velocity.z, 1.0);
		}
	}

	private static void burst(ServerLevel level, BlockPos pos) {
		Vec3 c = Vec3.atCenterOf(pos);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.4, c.z, 8, 0.3, 0.2, 0.3, 0.02);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 20, 0.45, 0.45, 0.45, 0.3);
	}

	private static void play(ServerLevel level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
	}

	private static BlockPos block(Pos pos) {
		return new BlockPos(pos.x(), pos.y(), pos.z());
	}
}
