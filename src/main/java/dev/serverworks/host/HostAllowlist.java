package dev.serverworks.host;

import java.net.URI;
import java.util.Set;

/**
 * The only hosts the server runtime will ever talk to. Everything it downloads is code
 * that will run on this machine, so there is no way to add to this list from the game.
 *
 * <p>Installers that Serverworks runs (NeoForge, Forge, Quilt, BuildTools) fetch their own
 * libraries from their projects' servers; that traffic is theirs, not this list's.
 */
public final class HostAllowlist {
	private static final Set<String> HOSTS = Set.of(
			"api.modrinth.com",
			"cdn.modrinth.com",
			"piston-meta.mojang.com",
			"piston-data.mojang.com",
			"fill.papermc.io",
			"fill-data.papermc.io",
			"meta.fabricmc.net",
			"api.purpurmc.org",
			"meta.quiltmc.org",
			"maven.quiltmc.org",
			"maven.neoforged.net",
			"files.minecraftforge.net",
			"maven.minecraftforge.net",
			"dl-api.spongepowered.org",
			"repo.spongepowered.org",
			"hub.spigotmc.org");

	private HostAllowlist() {
	}

	public static boolean allows(URI uri) {
		return uri != null
				&& "https".equals(uri.getScheme())
				&& uri.getHost() != null
				&& HOSTS.contains(uri.getHost())
				&& uri.getPort() == -1
				&& uri.getUserInfo() == null;
	}

	public static URI require(String url) throws PrepareException {
		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException e) {
			throw new PrepareException(ServerInstance.Failure.DOWNLOAD, "Not a valid address: " + url);
		}
		if (!allows(uri)) {
			throw new PrepareException(ServerInstance.Failure.DOWNLOAD, "Refusing to download from " + uri.getHost());
		}
		return uri;
	}
}
