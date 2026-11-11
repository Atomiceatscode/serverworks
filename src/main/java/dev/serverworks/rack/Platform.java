package dev.serverworks.rack;

import java.util.List;
import java.util.Locale;

/**
 * A server software a rack can run. "Platform" is the mod's one word for Forge, Fabric,
 * Paper and the rest, whatever they call themselves.
 *
 * <p>Each platform lists the Modrinth loader tags whose content it can run, most
 * preferred first. That list is what decides whether a disk fits a rack.
 */
public enum Platform {
	VANILLA(Group.VANILLA, 0x6AAA3A, "datapack"),
	FABRIC(Group.MOD, 0xDBD0B4, "fabric", "datapack"),
	QUILT(Group.MOD, 0x9B5FD0, "quilt", "fabric", "datapack"),
	NEOFORGE(Group.MOD, 0xE8873A, "neoforge", "datapack"),
	FORGE(Group.MOD, 0x3E5C8A, "forge", "datapack"),
	PAPER(Group.PLUGIN, 0xF2F2F2, "paper", "spigot", "bukkit", "datapack"),
	PURPUR(Group.PLUGIN, 0xB56FD6, "purpur", "paper", "spigot", "bukkit", "datapack"),
	FOLIA(Group.PLUGIN, 0x4FBF6B, "folia", "datapack"),
	SPIGOT(Group.PLUGIN, 0xF0A030, "spigot", "bukkit", "datapack"),
	BUKKIT(Group.PLUGIN, 0x4A90D9, "bukkit", "datapack"),
	SPONGE(Group.PLUGIN, 0xF0D44A, "sponge", "datapack"),
	VELOCITY(Group.PROXY, 0x32C8E0, "velocity"),
	BUNGEECORD(Group.PROXY, 0xD9B43A, "bungeecord"),
	WATERFALL(Group.PROXY, 0x3A7BE0, "waterfall", "bungeecord");

	private final Group group;
	private final int color;
	private final List<String> acceptedLoaders;
	private final String id;

	Platform(Group group, int color, String... acceptedLoaders) {
		this.group = group;
		this.color = color;
		this.acceptedLoaders = List.of(acceptedLoaders);
		this.id = name().toLowerCase(Locale.ROOT);
	}

	/** Lower-case name used in block ids, translation keys and saved data. */
	public String id() {
		return id;
	}

	public Group group() {
		return group;
	}

	/** Packed RGB accent colour used for this platform's trim and lights. */
	public int color() {
		return color;
	}

	/** Modrinth loader tags this platform can run, most preferred first. */
	public List<String> acceptedLoaders() {
		return acceptedLoaders;
	}

	/** True when at least one of a project's loader tags can run here. */
	public boolean accepts(List<String> projectLoaders) {
		for (String loader : acceptedLoaders) {
			if (projectLoaders.contains(loader)) {
				return true;
			}
		}
		return false;
	}

	public String translationKey() {
		return "serverworks.platform." + id;
	}

	public static Platform byId(String id) {
		for (Platform platform : values()) {
			if (platform.id.equals(id)) {
				return platform;
			}
		}
		return null;
	}

	/** The broad kind of server a platform is, which decides what content it takes. */
	public enum Group {
		VANILLA,
		MOD,
		PLUGIN,
		PROXY;

		public String translationKey() {
			return "serverworks.platform_group." + name().toLowerCase(Locale.ROOT);
		}
	}
}
