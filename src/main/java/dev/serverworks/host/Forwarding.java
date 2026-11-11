package dev.serverworks.host;

import dev.serverworks.rack.Platform;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * How a proxy tells the servers behind it who a player really is.
 *
 * <p>MODERN (Velocity's signed forwarding) and LEGACY (BungeeCord's) keep each player's
 * real account and skin. NONE works with any server, but players appear with offline
 * identities, so their inventories are not shared with the server joined directly.
 */
public enum Forwarding {
	MODERN,
	LEGACY,
	NONE;

	/** The best mode that the proxy and every server behind it understand. */
	public static Forwarding choose(Platform proxy, Collection<Platform> backends) {
		for (Forwarding mode : values()) {
			if (proxySupport(proxy).contains(mode) && backends.stream().allMatch(b -> backendSupport(b).contains(mode))) {
				return mode;
			}
		}
		return NONE;
	}

	public static Set<Forwarding> proxySupport(Platform proxy) {
		return switch (proxy) {
			case VELOCITY -> EnumSet.allOf(Forwarding.class);
			case BUNGEECORD -> EnumSet.of(LEGACY, NONE);
			default -> EnumSet.noneOf(Forwarding.class);
		};
	}

	public static Set<Forwarding> backendSupport(Platform backend) {
		return switch (backend) {
			case PAPER, PURPUR, FOLIA -> EnumSet.allOf(Forwarding.class);
			case SPIGOT -> EnumSet.of(LEGACY, NONE);
			// Fabric and Quilt get FabricProxy-Lite added automatically for modern forwarding.
			case FABRIC, QUILT -> EnumSet.of(MODERN, NONE);
			case VELOCITY, BUNGEECORD, WATERFALL -> EnumSet.noneOf(Forwarding.class);
			default -> EnumSet.of(NONE);
		};
	}

	/** True when this platform can sit behind a proxy at all. */
	public static boolean canBeBackend(Platform platform) {
		return !backendSupport(platform).isEmpty();
	}
}
