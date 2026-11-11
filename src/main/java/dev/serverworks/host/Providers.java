package dev.serverworks.host;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.serverworks.host.InstallPlan.Artifact;
import dev.serverworks.host.InstallPlan.Setup;
import dev.serverworks.host.ServerInstance.Failure;
import dev.serverworks.rack.Platform;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The platforms this build can launch, and where each one's server comes from. */
public final class Providers {
	private static final Map<Platform, PlatformProvider> ALL = new EnumMap<>(Platform.class);
	/** Long enough for BuildTools to compile Spigot on a slow machine. */
	static final long BUILD_TIMEOUT_MINUTES = 45;
	static final long INSTALL_TIMEOUT_MINUTES = 20;

	static {
		register(new Vanilla());
		register(new PaperMc(Platform.PAPER, "paper"));
		register(new PaperMc(Platform.FOLIA, "folia"));
		register(new Purpur());
		register(new Fabric());
		register(new Quilt());
		register(new NeoForge());
		register(new Forge());
		register(new Sponge());
		register(new BuildTools(Platform.SPIGOT, "spigot"));
		register(new BuildTools(Platform.BUKKIT, "craftbukkit"));
		register(new Velocity());
		register(new BungeeCord());
	}

	private Providers() {
	}

	private static void register(PlatformProvider provider) {
		ALL.put(provider.platform(), provider);
	}

	public static @Nullable PlatformProvider get(Platform platform) {
		return ALL.get(platform);
	}

	public static boolean supports(Platform platform) {
		return ALL.containsKey(platform);
	}

	/** Why a platform cannot be launched, as a translation key, or null when it can. */
	public static @Nullable String unsupportedReason(Platform platform) {
		if (supports(platform)) {
			return null;
		}
		return platform == Platform.WATERFALL ? "serverworks.platform.waterfall.discontinued"
				: "serverworks.notice.platform_unsupported";
	}

	// ---------------------------------------------------------------- jar platforms

	/** Mojang's own server jar. */
	static final class Vanilla implements PlatformProvider {
		@Override
		public Platform platform() {
			return Platform.VANILLA;
		}

		@Override
		public InstallPlan plan(Fetcher fetcher, String mc) throws PrepareException {
			MojangMeta.ServerInfo info = MojangMeta.server(fetcher, mc);
			return new InstallPlan(new Artifact(info.url(), "vanilla-" + mc + ".jar", Checksum.sha1(info.sha1()), info.size()),
					info.javaVersion(), "Vanilla " + mc, null);
		}
	}

	/** PaperMC's download service (Paper and Folia): the newest stable build, or the newest of any channel. */
	static final class PaperMc implements PlatformProvider {
		private final Platform platform;
		private final String project;

		PaperMc(Platform platform, String project) {
			this.platform = platform;
			this.project = project;
		}

		@Override
		public Platform platform() {
			return platform;
		}

		@Override
		public InstallPlan plan(Fetcher fetcher, String mc) throws PrepareException {
			int java = MojangMeta.server(fetcher, mc).javaVersion();
			Build build = newestBuild(fetcher, project, mc, platform.id() + " has no builds for Minecraft " + mc);
			return new InstallPlan(build.artifact(project + "-" + mc), java,
					display(platform) + " " + mc + " build " + build.id, null);
		}
	}

	/** Purpur's download service, which publishes an MD5 for each build. */
	static final class Purpur implements PlatformProvider {
		@Override
		public Platform platform() {
			return Platform.PURPUR;
		}

		@Override
		public InstallPlan plan(Fetcher fetcher, String mc) throws PrepareException {
			int java = MojangMeta.server(fetcher, mc).javaVersion();
			requireVersionLike(mc);
			JsonObject latest = object(fetcher, "https://api.purpurmc.org/v2/purpur/" + mc + "/latest",
					"Purpur has no builds for Minecraft " + mc);
			String build = string(latest, "build");
			requireVersionLike(build);
			String md5 = latest.has("md5") ? latest.get("md5").getAsString() : null;
			return new InstallPlan(new Artifact("https://api.purpurmc.org/v2/purpur/" + mc + "/" + build + "/download",
					"purpur-" + mc + "-" + build + ".jar", md5 == null ? null : Checksum.md5(md5), 60_000_000),
					java, "Purpur " + mc + " build " + build, null);
		}
	}

	/** SpongeVanilla: the newest recommended build, or the newest release candidate when there is none. */
	static final class Sponge implements PlatformProvider {
		private static final String BASE = "https://dl-api.spongepowered.org/v2/groups/org.spongepowered/artifacts/spongevanilla/versions";

		@Override
		public Platform platform() {
			return Platform.SPONGE;
		}

		@Override
		public InstallPlan plan(Fetcher fetcher, String mc) throws PrepareException {
			int java = MojangMeta.server(fetcher, mc).javaVersion();
			requireVersionLike(mc);
			String version = firstKey(fetcher, BASE + "?tags=minecraft:" + mc + "&recommended=true&limit=1");
			if (version == null) {
				version = firstKey(fetcher, BASE + "?tags=minecraft:" + mc + "&limit=1");
			}
			if (version == null) {
				throw new PrepareException(Failure.UNSUPPORTED, "Sponge has no builds for Minecraft " + mc);
			}
			requireVersionLike(version);
			JsonObject details = object(fetcher, BASE + "/" + version, "Sponge build details could not be read");
			for (JsonElement element : details.getAsJsonArray("assets")) {
				JsonObject asset = element.getAsJsonObject();
				if ("universal".equals(string(asset, "classifier")) && "jar".equals(string(asset, "extension"))) {
					String sha1 = asset.has("sha1") ? asset.get("sha1").getAsString() : null;
					return new InstallPlan(new Artifact(string(asset, "downloadUrl"), "spongevanilla-" + version + ".jar",
							sha1 == null ? null : Checksum.sha1(sha1), 90_000_000), java, "SpongeVanilla " + version, null);
				}
			}
			throw new PrepareException(Failure.UNSUPPORTED, "Sponge " + version + " has no server download");
		}

		private static @Nullable String firstKey(Fetcher fetcher, String url) throws PrepareException {
			JsonObject artifacts = object(fetcher, url, "Sponge's download list could not be read").getAsJsonObject("artifacts");
			return artifacts == null || artifacts.isEmpty() ? null : artifacts.keySet().iterator().next();
		}
	}

	// ---------------------------------------------------------------- mod loaders

	/**
	 * Fabric's server launcher: a small jar that fetches the vanilla server and Fabric's
	 * libraries on first run. Fabric publishes no digest for it, so it relies on HTTPS alone.
	 */
	static final class Fabric implements PlatformProvider {
		@Override
		public Platform platform() {
			return Platform.FABRIC;
		}

		@Override
		public InstallPlan plan(Fetcher fetcher, String mc) throws PrepareException {
			int java = MojangMeta.server(fetcher, mc).javaVersion();
			String loader = firstStable(fetcher, "https://meta.fabricmc.net/v2/versions/loader/" + mc, "loader");
			String installer = firstStable(fetcher, "https://meta.fabricmc.net/v2/versions/installer", null);
			if (loader == null || installer == null) {
				throw new PrepareException(Failure.UNSUPPORTED, "Fabric does not support Minecraft " + mc);
			}
			requireVersionLike(loader);
			requireVersionLike(installer);
			String url = "https://meta.fabricmc.net/v2/versions/loader/" + mc + "/" + loader + "/" + installer + "/server/jar";
			String file = "fabric-" + mc + "-loader." + loader + "-launcher." + installer + ".jar";
			return new InstallPlan(new Artifact(url, file, null, 200_000), java, "Fabric " + mc + " loader " + loader, loader);
		}
	}

	/** Quilt's installer, which lays out the server (and fetches vanilla) inside the server folder. */
	static final class Quilt implements PlatformProvider {
		@Override
		public Platform platform() {
			return Platform.QUILT;
		}

		@Override
		public InstallPlan plan(Fetcher fetcher, String mc) throws PrepareException {
			int java = MojangMeta.server(fetcher, mc).javaVersion();
			// Quilt's per-version list is unordered and lags behind; its main list is newest first.
			fetcher.text(HostAllowlist.require("https://meta.quiltmc.org/v3/versions/loader/" + mc));
			String loader = firstStable(fetcher, "https://meta.quiltmc.org/v3/versions/loader", null);
			if (loader == null) {
				throw new PrepareException(Failure.UNSUPPORTED, "Quilt does not support Minecraft " + mc);
			}
			requireVersionLike(loader);
			JsonElement installers = MojangMeta.parse(fetcher.text(HostAllowlist.require("https://meta.quiltmc.org/v3/versions/installer")));
			JsonObject installer = installers.getAsJsonArray().get(0).getAsJsonObject();
			String version = string(installer, "version");
			requireVersionLike(version);
			JsonObject hashes = installer.getAsJsonObject("hashes");
			Checksum checksum = hashes != null && hashes.has("sha512") ? Checksum.sha512(hashes.get("sha512").getAsString()) : null;
			Artifact artifact = new Artifact(string(installer, "url"), "quilt-installer-" + version + ".jar", checksum,
					installer.has("file_size") ? installer.get("file_size").getAsLong() : 9_000_000);
			return new InstallPlan(artifact, java, "Quilt " + mc + " loader " + loader, loader, Setup.INSTALL_IN_INSTANCE,
					List.of("install", "server", mc, loader, "--download-server", "--install-dir=."), null,
					InstallPlan.VANILLA_READY);
		}
	}

	/** NeoForge's installer. Builds for Minecraft 26.3 are numbered 26.3.0.N. */
	static final class NeoForge implements PlatformProvider {
		private static final String MAVEN = "https://maven.neoforged.net/releases/net/neoforged/neoforge/";

		@Override
		public Platform platform() {
			return Platform.NEOFORGE;
		}

		@Override
		public InstallPlan plan(Fetcher fetcher, String mc) throws PrepareException {
			int java = MojangMeta.server(fetcher, mc).javaVersion();
			String prefix = (mc.chars().filter(c -> c == '.').count() == 1 ? mc + ".0" : mc) + ".";
			String metadata = fetcher.text(HostAllowlist.require(MAVEN + "maven-metadata.xml"));
			String chosen = null;
			String chosenBeta = null;
			Matcher matcher = Pattern.compile("<version>([^<]+)</version>").matcher(metadata);
			while (matcher.find()) {
				String version = matcher.group(1);
				if (version.startsWith(prefix)) {
					if (version.contains("-")) {
						chosenBeta = version;
					} else {
						chosen = version;
					}
				}
			}
			String version = chosen != null ? chosen : chosenBeta;
			if (version == null) {
				throw new PrepareException(Failure.UNSUPPORTED, "NeoForge does not support Minecraft " + mc);
			}
			requireVersionLike(version);
			String jar = MAVEN + version + "/neoforge-" + version + "-installer.jar";
			String sha1 = fetcher.text(HostAllowlist.require(jar + ".sha1")).strip();
			return new InstallPlan(new Artifact(jar, "neoforge-" + version + "-installer.jar", Checksum.sha1(sha1), 10_000_000),
					java, "NeoForge " + version, version, Setup.INSTALL_IN_INSTANCE, List.of("--installServer", "."), null,
					InstallPlan.VANILLA_READY);
		}
	}

	/** Forge's installer, choosing the recommended build and falling back to the latest. */
	static final class Forge implements PlatformProvider {
		@Override
		public Platform platform() {
			return Platform.FORGE;
		}

		@Override
		public InstallPlan plan(Fetcher fetcher, String mc) throws PrepareException {
			int java = MojangMeta.server(fetcher, mc).javaVersion();
			JsonObject promos = object(fetcher, "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json",
					"Forge's version list could not be read").getAsJsonObject("promos");
			JsonElement build = promos.has(mc + "-recommended") ? promos.get(mc + "-recommended") : promos.get(mc + "-latest");
			if (build == null) {
				throw new PrepareException(Failure.UNSUPPORTED, "Forge does not support Minecraft " + mc);
			}
			String version = mc + "-" + build.getAsString();
			requireVersionLike(version);
			String jar = "https://maven.minecraftforge.net/net/minecraftforge/forge/" + version + "/forge-" + version + "-installer.jar";
			String sha1 = fetcher.text(HostAllowlist.require(jar + ".sha1")).strip();
			return new InstallPlan(new Artifact(jar, "forge-" + version + "-installer.jar", Checksum.sha1(sha1), 10_000_000),
					java, "Forge " + version, build.getAsString(), Setup.INSTALL_IN_INSTANCE, List.of("--installServer"), null,
					InstallPlan.VANILLA_READY);
		}
	}

	// ---------------------------------------------------------------- built locally

	/**
	 * Spigot and CraftBukkit are only distributed as source, so Spigot's BuildTools compiles
	 * them. That takes several minutes the first time per Minecraft version; the result is cached.
	 */
	static final class BuildTools implements PlatformProvider {
		private static final String JOB = "https://hub.spigotmc.org/jenkins/job/BuildTools/";
		private final Platform platform;
		private final String target;

		BuildTools(Platform platform, String target) {
			this.platform = platform;
			this.target = target;
		}

		@Override
		public Platform platform() {
			return platform;
		}

		@Override
		public InstallPlan plan(Fetcher fetcher, String mc) throws PrepareException {
			int java = MojangMeta.server(fetcher, mc).javaVersion();
			requireVersionLike(mc);
			fetcher.text(HostAllowlist.require("https://hub.spigotmc.org/versions/" + mc + ".json"));
			int number = object(fetcher, JOB + "lastSuccessfulBuild/api/json?tree=number", "BuildTools could not be found")
					.get("number").getAsInt();
			Artifact tool = new Artifact(JOB + number + "/artifact/target/BuildTools.jar", "BuildTools-" + number + ".jar",
					null, 5_000_000);
			return new InstallPlan(tool, java, display(platform) + " " + mc, null, Setup.BUILD_JAR,
					List.of("--rev", mc, "--compile", target, "--output-dir", "."), target + "-" + mc + ".jar",
					InstallPlan.VANILLA_READY);
		}
	}

	// ---------------------------------------------------------------- proxies

	/** Velocity from PaperMC's download service: the newest release of the newest line. */
	static final class Velocity implements PlatformProvider {
		@Override
		public Platform platform() {
			return Platform.VELOCITY;
		}

		@Override
		public InstallPlan plan(Fetcher fetcher, String mc) throws PrepareException {
			JsonObject versions = object(fetcher, "https://fill.papermc.io/v3/projects/velocity", "Velocity could not be found")
					.getAsJsonObject("versions");
			String chosen = null;
			for (String family : versions.keySet()) {
				for (JsonElement element : versions.getAsJsonArray(family)) {
					String version = element.getAsString();
					if (!version.contains("SNAPSHOT")) {
						chosen = version;
						break;
					}
				}
				if (chosen != null) {
					break;
				}
			}
			if (chosen == null) {
				throw new PrepareException(Failure.UNSUPPORTED, "Velocity has no release builds");
			}
			Build build = newestBuild(fetcher, "velocity", chosen, "Velocity " + chosen + " has no builds");
			return new InstallPlan(build.artifact("velocity-" + chosen), 21, "Velocity " + chosen + " build " + build.id, null)
					.withReadyMarker("Done (");
		}
	}

	/** BungeeCord from Spigot's build server. No digest is published, so it relies on HTTPS alone. */
	static final class BungeeCord implements PlatformProvider {
		private static final String JOB = "https://hub.spigotmc.org/jenkins/job/BungeeCord/";

		@Override
		public Platform platform() {
			return Platform.BUNGEECORD;
		}

		@Override
		public InstallPlan plan(Fetcher fetcher, String mc) throws PrepareException {
			int number = object(fetcher, JOB + "lastSuccessfulBuild/api/json?tree=number", "BungeeCord could not be found")
					.get("number").getAsInt();
			return new InstallPlan(new Artifact(JOB + number + "/artifact/bootstrap/target/BungeeCord.jar",
					"BungeeCord-" + number + ".jar", null, 25_000_000), 17, "BungeeCord build " + number, null)
					.withReadyMarker("Listening on /");
		}
	}

	// ---------------------------------------------------------------- helpers

	private record Build(int id, String url, String sha256, long size) {
		Artifact artifact(String prefix) {
			return new Artifact(url, prefix + "-" + id + ".jar", Checksum.sha256(sha256), size);
		}
	}

	/** The newest STABLE build of a PaperMC project version, or the newest of any channel. */
	private static Build newestBuild(Fetcher fetcher, String project, String version, String missing) throws PrepareException {
		requireVersionLike(version);
		JsonElement parsed = MojangMeta.parse(fetcher.text(HostAllowlist.require(
				"https://fill.papermc.io/v3/projects/" + project + "/versions/" + version + "/builds")));
		if (!parsed.isJsonArray() || parsed.getAsJsonArray().isEmpty()) {
			throw new PrepareException(Failure.UNSUPPORTED, missing);
		}
		JsonArray builds = parsed.getAsJsonArray();
		JsonObject chosen = builds.get(0).getAsJsonObject();
		for (JsonElement element : builds) {
			JsonObject build = element.getAsJsonObject();
			if ("STABLE".equals(string(build, "channel"))) {
				chosen = build;
				break;
			}
		}
		JsonObject download = chosen.getAsJsonObject("downloads").getAsJsonObject("server:default");
		return new Build(chosen.get("id").getAsInt(), string(download, "url"),
				download.getAsJsonObject("checksums").get("sha256").getAsString(), download.get("size").getAsLong());
	}

	/** The first entry marked stable, else the first entry. Entries are newest first. */
	private static @Nullable String firstStable(Fetcher fetcher, String url, @Nullable String nestedKey) throws PrepareException {
		JsonElement parsed = MojangMeta.parse(fetcher.text(HostAllowlist.require(url)));
		if (!parsed.isJsonArray() || parsed.getAsJsonArray().isEmpty()) {
			return null;
		}
		String fallback = null;
		for (JsonElement element : parsed.getAsJsonArray()) {
			JsonObject entry = element.getAsJsonObject();
			JsonObject item = nestedKey == null ? entry : entry.getAsJsonObject(nestedKey);
			String version = item.get("version").getAsString();
			if (fallback == null) {
				fallback = version;
			}
			boolean stable = item.has("stable") ? item.get("stable").getAsBoolean() : !version.contains("beta");
			if (stable) {
				return version;
			}
		}
		return fallback;
	}

	private static JsonObject object(Fetcher fetcher, String url, String error) throws PrepareException {
		JsonElement parsed = MojangMeta.parse(fetcher.text(HostAllowlist.require(url)));
		if (!parsed.isJsonObject()) {
			throw new PrepareException(Failure.UNSUPPORTED, error);
		}
		return parsed.getAsJsonObject();
	}

	private static String string(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element == null || element.isJsonNull() ? "" : element.getAsString();
	}

	private static String display(Platform platform) {
		String id = platform.id();
		return switch (platform) {
			case BUKKIT -> "CraftBukkit";
			default -> Character.toUpperCase(id.charAt(0)) + id.substring(1);
		};
	}

	/** Versions go into URLs and file names, so they must look like versions. */
	static void requireVersionLike(String version) throws PrepareException {
		if (!version.matches("[0-9A-Za-z.+_-]{1,60}") || version.contains("..")) {
			throw new PrepareException(Failure.DOWNLOAD, "Unexpected version string: " + version);
		}
	}

	static List<String> copy(List<String> list) {
		return new ArrayList<>(list);
	}
}
