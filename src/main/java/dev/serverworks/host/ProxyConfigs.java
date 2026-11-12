package dev.serverworks.host;

import dev.serverworks.rack.Platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Writes the configuration that lets proxies and the servers behind them find and trust each other. */
public final class ProxyConfigs {
	static final String FABRIC_PROXY_PROJECT = "fabricproxy-lite";
	static final String FABRIC_PROXY_TITLE = "FabricProxy-Lite";

	private ProxyConfigs() {
	}

	// ---------------------------------------------------------------- proxies

	static void velocity(Path dir, LaunchRequest request, ProxyRole.Proxy proxy) throws IOException {
		Path config = dir.resolve("velocity.toml");
		List<String> lines = Files.exists(config) ? Files.readAllLines(config, StandardCharsets.UTF_8)
				: defaultFromJar(dir.resolve(Deployment.SERVER_JAR), "default-velocity.toml");
		String bind = (request.bindAddress().isEmpty() ? "0.0.0.0" : request.bindAddress()) + ":" + request.port();
		lines = ConfigEdits.tomlSetTopLevel(lines, "bind", ConfigEdits.tomlString(bind));
		lines = ConfigEdits.tomlSetTopLevel(lines, "online-mode", Boolean.toString(request.onlineMode()));
		lines = ConfigEdits.tomlSetTopLevel(lines, "show-max-players", Integer.toString(request.maxPlayers()));
		lines = ConfigEdits.tomlSetTopLevel(lines, "player-info-forwarding-mode",
				ConfigEdits.tomlString(proxy.mode().name()));
		lines = ConfigEdits.tomlSetTopLevel(lines, "forwarding-secret-file", ConfigEdits.tomlString("forwarding.secret"));
		List<String> servers = new ArrayList<>();
		List<String> names = new ArrayList<>();
		for (ProxyRole.Target target : proxy.targets()) {
			servers.add(target.name() + " = " + ConfigEdits.tomlString("127.0.0.1:" + target.port()));
			names.add(ConfigEdits.tomlString(target.name()));
		}
		servers.add("try = [" + String.join(", ", names) + "]");
		lines = ConfigEdits.tomlReplaceTable(lines, "servers", servers);
		// The defaults point example hostnames at servers that do not exist, which Velocity rejects.
		lines = ConfigEdits.tomlReplaceTable(lines, "forced-hosts", List.of());
		Files.write(config, lines, StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("forwarding.secret"), proxy.secret(), StandardCharsets.UTF_8);
		HostRuntime.ownerOnly(dir.resolve("forwarding.secret"));
	}

	/**
	 * BungeeCord's config.yml is written whole each start: BungeeCord fills in every key left
	 * out, and its server list is a structure that is simpler to own than to edit.
	 */
	static void bungeeCord(Path dir, LaunchRequest request, ProxyRole.Proxy proxy) throws IOException {
		String host = (request.bindAddress().isEmpty() ? "0.0.0.0" : request.bindAddress()) + ":" + request.port();
		List<String> out = new ArrayList<>();
		out.add("# Written by Serverworks every time the rack starts. Changes here are replaced.");
		out.add("ip_forward: " + (proxy.mode() == Forwarding.LEGACY));
		out.add("online_mode: " + request.onlineMode());
		out.add("player_limit: " + request.maxPlayers());
		out.add("listeners:");
		out.add("- host: " + ConfigEdits.yamlString(host));
		out.add("  query_port: " + request.port());
		out.add("  query_enabled: false");
		out.add("  motd: " + ConfigEdits.yamlString(request.motd()));
		out.add("  max_players: " + request.maxPlayers());
		out.add("  force_default_server: false");
		out.add("  forced_hosts: {}");
		out.add("  tab_list: GLOBAL_PING");
		out.add("  tab_size: 60");
		out.add("  bind_local_address: true");
		out.add("  ping_passthrough: false");
		out.add("  proxy_protocol: false");
		out.add("  priorities:");
		for (ProxyRole.Target target : proxy.targets()) {
			out.add("  - " + target.name());
		}
		out.add("servers:");
		for (ProxyRole.Target target : proxy.targets()) {
			out.add("  " + target.name() + ":");
			out.add("    address: " + ConfigEdits.yamlString("127.0.0.1:" + target.port()));
			out.add("    motd: " + ConfigEdits.yamlString(target.name()));
			out.add("    restricted: false");
		}
		Files.write(dir.resolve("config.yml"), out, StandardCharsets.UTF_8);
	}

	// ---------------------------------------------------------------- servers behind a proxy

	/** Configures, or un-configures, a server for the proxy in front of it. Null means it stands alone. */
	static void backend(Path dir, Platform platform, ProxyRole.Backend backend) throws IOException {
		Forwarding mode = backend == null ? Forwarding.NONE : backend.mode();
		String secret = backend == null ? "" : backend.secret();
		switch (platform) {
			case PAPER, PURPUR, FOLIA -> {
				yaml(dir.resolve("config/paper-global.yml"), List.of(
						new Setting(List.of("proxies", "velocity", "enabled"), Boolean.toString(mode == Forwarding.MODERN)),
						new Setting(List.of("proxies", "velocity", "online-mode"), "true"),
						new Setting(List.of("proxies", "velocity", "secret"), ConfigEdits.yamlString(secret))));
				yaml(dir.resolve("spigot.yml"), List.of(
						new Setting(List.of("settings", "bungeecord"), Boolean.toString(mode == Forwarding.LEGACY))));
			}
			case SPIGOT -> yaml(dir.resolve("spigot.yml"), List.of(
					new Setting(List.of("settings", "bungeecord"), Boolean.toString(mode == Forwarding.LEGACY))));
			case FABRIC, QUILT -> {
				if (mode == Forwarding.MODERN) {
					Path toml = dir.resolve("config/FabricProxy-Lite.toml");
					Files.createDirectories(toml.getParent());
					List<String> lines = Files.exists(toml) ? Files.readAllLines(toml, StandardCharsets.UTF_8) : new ArrayList<>();
					lines = ConfigEdits.tomlSetTopLevel(lines, "hackOnlineMode", "true");
					lines = ConfigEdits.tomlSetTopLevel(lines, "secret", ConfigEdits.tomlString(secret));
					Files.write(toml, lines, StandardCharsets.UTF_8);
					HostRuntime.ownerOnly(toml);
				}
			}
			default -> {
				// Nothing to configure: these accept proxied players only without forwarding.
			}
		}
	}

	/**
	 * A plugin server that only listens on this computer sees every player arrive from the
	 * same address (through a Gateway or a proxy), so Bukkit's per-address connection
	 * throttle would turn away players who join together. It is switched off there.
	 */
	static void localOnly(Path dir, Platform platform, boolean localOnly) throws IOException {
		if (platform.group() != Platform.Group.PLUGIN || platform == Platform.SPONGE) {
			return;
		}
		yaml(dir.resolve("bukkit.yml"), List.of(new Setting(List.of("settings", "connection-throttle"),
				localOnly ? "-1" : "4000")));
	}

	private record Setting(List<String> path, String value) {
	}

	/** Applies settings to a YAML file. A file the server has not created yet is started with just these. */
	private static void yaml(Path file, List<Setting> settings) throws IOException {
		Files.createDirectories(file.getParent());
		List<String> lines = Files.exists(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : new ArrayList<>();
		for (Setting setting : settings) {
			lines = ConfigEdits.yamlSet(lines, setting.path(), setting.value());
		}
		Files.write(file, lines, StandardCharsets.UTF_8);
		HostRuntime.ownerOnly(file);
	}

	private static List<String> defaultFromJar(Path jar, String entry) throws IOException {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry found = zip.getEntry(entry);
			if (found == null) {
				throw new IOException("The proxy jar has no " + entry);
			}
			try (InputStream in = zip.getInputStream(found)) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
			}
		}
	}

	/** A name the proxy can use for a server: lowercase letters, digits and dashes. */
	public static String serverName(String base, int index) {
		String clean = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
		return (clean.isEmpty() ? "server" : clean) + "-" + index;
	}
}
