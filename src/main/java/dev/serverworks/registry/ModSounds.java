package dev.serverworks.registry;

import dev.serverworks.Serverworks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
	public static final SoundEvent RACK_HUM = register("rack_hum");
	public static final SoundEvent RACK_BOOT = register("rack_boot");
	public static final SoundEvent RACK_POWER_DOWN = register("rack_power_down");
	public static final SoundEvent RACK_ALARM = register("rack_alarm");
	public static final SoundEvent DRIVE_INSERT = register("drive_insert");
	public static final SoundEvent DRIVE_EJECT = register("drive_eject");
	public static final SoundEvent DRIVE_SEEK = register("drive_seek");
	public static final SoundEvent DISK_WRITE = register("disk_write");
	public static final SoundEvent GATEWAY_OPEN = register("gateway_open");
	public static final SoundEvent GATEWAY_TRAVEL = register("gateway_travel");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = Serverworks.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}
}
