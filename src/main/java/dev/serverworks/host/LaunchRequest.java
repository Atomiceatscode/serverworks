package dev.serverworks.host;

import dev.serverworks.rack.Platform;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Everything decided in the game about a server before it is prepared and launched.
 *
 * @param bindAddress  the address to listen on; empty for every interface
 * @param onlineMode   whether the child checks accounts with Mojang, matching its host
 * @param eulaAccepted whether the person responsible for this machine has agreed to
 *                     Minecraft's EULA; the server will not start without it
 * @param asBackend    set when this server sits behind a proxy
 * @param asProxy      set when this server is a proxy
 * @param settings     {@code server.properties} values from a Config Card; network settings in it are ignored
 * @param world        which world to play
 */
public record LaunchRequest(
		UUID rackId,
		Platform platform,
		String minecraftVersion,
		int ramMb,
		int maxPlayers,
		int port,
		String bindAddress,
		boolean onlineMode,
		boolean eulaAccepted,
		String motd,
		List<ContentResolver.Wanted> disks,
		ProxyRole.@Nullable Backend asBackend,
		ProxyRole.@Nullable Proxy asProxy,
		java.util.Map<String, String> settings,
		WorldPlan world) {

	public LaunchRequest {
		disks = List.copyOf(disks);
		settings = java.util.Map.copyOf(settings);
	}

	public LaunchRequest(UUID rackId, Platform platform, String minecraftVersion, int ramMb, int maxPlayers, int port,
			String bindAddress, boolean onlineMode, boolean eulaAccepted, String motd, List<ContentResolver.Wanted> disks,
			ProxyRole.@Nullable Backend asBackend, ProxyRole.@Nullable Proxy asProxy) {
		this(rackId, platform, minecraftVersion, ramMb, maxPlayers, port, bindAddress, onlineMode, eulaAccepted, motd, disks,
				asBackend, asProxy, java.util.Map.of(), WorldPlan.DEFAULT);
	}

	/** A server that stands on its own. */
	public LaunchRequest(UUID rackId, Platform platform, String minecraftVersion, int ramMb, int maxPlayers, int port,
			String bindAddress, boolean onlineMode, boolean eulaAccepted, String motd, List<ContentResolver.Wanted> disks) {
		this(rackId, platform, minecraftVersion, ramMb, maxPlayers, port, bindAddress, onlineMode, eulaAccepted, motd, disks,
				null, null);
	}
}
