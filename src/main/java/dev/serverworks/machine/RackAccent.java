package dev.serverworks.machine;

import dev.serverworks.rack.Platform;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/** The platform colour a formed casing wears, as a block state value. */
public enum RackAccent implements StringRepresentable {
	NONE(null),
	VANILLA(Platform.VANILLA),
	FABRIC(Platform.FABRIC),
	QUILT(Platform.QUILT),
	NEOFORGE(Platform.NEOFORGE),
	FORGE(Platform.FORGE),
	PAPER(Platform.PAPER),
	PURPUR(Platform.PURPUR),
	FOLIA(Platform.FOLIA),
	SPIGOT(Platform.SPIGOT),
	BUKKIT(Platform.BUKKIT),
	SPONGE(Platform.SPONGE),
	VELOCITY(Platform.VELOCITY),
	BUNGEECORD(Platform.BUNGEECORD),
	WATERFALL(Platform.WATERFALL);

	private final @Nullable Platform platform;

	RackAccent(@Nullable Platform platform) {
		this.platform = platform;
	}

	public static RackAccent of(@Nullable Platform platform) {
		if (platform != null) {
			for (RackAccent accent : values()) {
				if (accent.platform == platform) {
					return accent;
				}
			}
		}
		return NONE;
	}

	@Override
	public String getSerializedName() {
		return platform == null ? "none" : platform.id();
	}
}
