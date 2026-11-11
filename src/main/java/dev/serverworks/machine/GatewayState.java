package dev.serverworks.machine;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** What a Gateway looks like: dark, open to everyone, or limited to players with the right mods. */
public enum GatewayState implements StringRepresentable {
	CLOSED,
	OPEN,
	RESTRICTED;

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
