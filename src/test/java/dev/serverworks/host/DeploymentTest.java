package dev.serverworks.host;

import dev.serverworks.catalog.model.ProjectVersion;
import dev.serverworks.catalog.model.ProjectVersion.Channel;
import dev.serverworks.catalog.model.ProjectVersion.Dependency;
import dev.serverworks.catalog.model.ProjectVersion.VersionFile;
import dev.serverworks.host.ContentResolver.Wanted;
import dev.serverworks.host.ServerInstance.Failure;
import dev.serverworks.host.ServerInstance.State;
import dev.serverworks.rack.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentTest {
	@TempDir
	Path root;

	private static final String MANIFEST = """
			{"versions":[{"id":"26.3","url":"https://piston-meta.mojang.com/v1/packages/abc/26.3.json"}]}""";
	private static final byte[] SERVER_BYTES = "server jar bytes".getBytes(StandardCharsets.UTF_8);
	private static final byte[] PLUGIN_BYTES = "plugin bytes".getBytes(StandardCharsets.UTF_8);
	private static final byte[] LIB_BYTES = "library bytes".getBytes(StandardCharsets.UTF_8);
	private static final byte[] PACK_MOD_BYTES = "pack mod bytes".getBytes(StandardCharsets.UTF_8);

	private static byte[] zip(Map<String, byte[]> entries) {
		try {
			java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
			try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(bytes)) {
				for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
					out.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
					out.write(entry.getValue());
					out.closeEntry();
				}
			}
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static byte[] modpack(String minecraft, String modUrl) {
		String index = """
				{"formatVersion":1,"game":"minecraft","versionId":"1.0","name":"Test Pack",
				 "files":[
				  {"path":"mods/packmod.jar","hashes":{"sha1":"%s","sha512":"%s"},"env":{"client":"required","server":"required"},
				   "downloads":["%s"],"fileSize":%d},
				  {"path":"mods/shaders.jar","hashes":{"sha1":"00","sha512":"%s"},"env":{"client":"required","server":"unsupported"},
				   "downloads":["https://cdn.modrinth.com/data/sh/shaders.jar"],"fileSize":1}],
				 "dependencies":{"minecraft":"%s","fabric-loader":"0.20.1"}}""".formatted(hex("SHA-1", PACK_MOD_BYTES),
				hex("SHA-512", PACK_MOD_BYTES), modUrl, PACK_MOD_BYTES.length, "0".repeat(128), minecraft);
		Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
		entries.put("modrinth.index.json", index.getBytes(StandardCharsets.UTF_8));
		entries.put("overrides/config/pack.cfg", "from the pack".getBytes(StandardCharsets.UTF_8));
		entries.put("server-overrides/server-only.txt", "server only".getBytes(StandardCharsets.UTF_8));
		return zip(entries);
	}

	private static final byte[] GOOD_PACK = modpack("26.3", "https://cdn.modrinth.com/data/pm/packmod.jar");
	private static final byte[] OLD_PACK = modpack("1.20.1", "https://cdn.modrinth.com/data/pm/packmod.jar");
	private static final byte[] FOREIGN_PACK = modpack("26.3", "https://github.com/someone/packmod.jar");
	private static final byte[] VELOCITY_JAR = zip(Map.of("default-velocity.toml", """
			config-version = "2.9"
			bind = "0.0.0.0:25565"
			online-mode = true
			player-info-forwarding-mode = "NONE"
			forwarding-secret-file = "forwarding.secret"
			[servers]
			lobby = "127.0.0.1:30066"
			try = [
			    "lobby"
			]
			[forced-hosts]
			"lobby.example.com" = [
			    "lobby"
			]
			[advanced]
			compression-threshold = 256
			""".getBytes(StandardCharsets.UTF_8)));

	private static String hex(String algorithm, byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/** Answers metadata and downloads from memory, and remembers what was asked for. */
	private static final class FakeFetcher implements Fetcher {
		final Map<String, String> texts = new HashMap<>();
		final Map<String, byte[]> files = new HashMap<>();
		final List<String> requests = new ArrayList<>();

		FakeFetcher() {
			texts.put(MojangMeta.MANIFEST, MANIFEST);
			texts.put("https://piston-meta.mojang.com/v1/packages/abc/26.3.json", """
					{"downloads":{"server":{"sha1":"%s","size":%d,"url":"https://piston-data.mojang.com/v1/objects/abc/server.jar"}},
					 "javaVersion":{"majorVersion":25}}""".formatted(hex("SHA-1", SERVER_BYTES), SERVER_BYTES.length));
			texts.put("https://fill.papermc.io/v3/projects/paper/versions/26.3/builds", """
					[{"id":249,"channel":"BETA","downloads":{"server:default":{"name":"x","checksums":{"sha256":"00"},"size":1,"url":"https://fill-data.papermc.io/v1/objects/beta/paper.jar"}}},
					 {"id":248,"channel":"STABLE","downloads":{"server:default":{"name":"paper-26.3-248.jar","checksums":{"sha256":"%s"},"size":%d,"url":"https://fill-data.papermc.io/v1/objects/stable/paper.jar"}}}]"""
					.formatted(hex("SHA-256", SERVER_BYTES), SERVER_BYTES.length));
			texts.put("https://meta.fabricmc.net/v2/versions/loader/26.3", """
					[{"loader":{"version":"0.21.0-beta.1","stable":false}},{"loader":{"version":"0.20.1","stable":true}}]""");
			texts.put("https://meta.fabricmc.net/v2/versions/installer", """
					[{"version":"1.1.2","stable":true}]""");
			files.put("https://piston-data.mojang.com/v1/objects/abc/server.jar", SERVER_BYTES);
			files.put("https://fill-data.papermc.io/v1/objects/stable/paper.jar", SERVER_BYTES);
			files.put("https://meta.fabricmc.net/v2/versions/loader/26.3/0.20.1/1.1.2/server/jar", SERVER_BYTES);
			files.put("https://cdn.modrinth.com/data/plug/plugin-1.0.jar", PLUGIN_BYTES);
			files.put("https://cdn.modrinth.com/data/lib/lib-2.0.jar", LIB_BYTES);
			files.put("https://cdn.modrinth.com/data/fabricmod/mod.jar", LIB_BYTES);
			files.put("https://cdn.modrinth.com/data/pm/packmod.jar", PACK_MOD_BYTES);
			files.put("https://cdn.modrinth.com/data/goodpack/good.mrpack", GOOD_PACK);
			files.put("https://cdn.modrinth.com/data/oldpack/old.mrpack", OLD_PACK);
			files.put("https://cdn.modrinth.com/data/foreignpack/foreign.mrpack", FOREIGN_PACK);
			files.put("https://cdn.modrinth.com/data/fabricproxy-lite/proxy.jar", PLUGIN_BYTES);
			texts.put("https://fill.papermc.io/v3/projects/velocity", """
					{"versions":{"4.0.0":["4.3.2-SNAPSHOT","4.3.1"],"3.0.0":["3.5.1"]}}""");
			texts.put("https://fill.papermc.io/v3/projects/velocity/versions/4.3.1/builds", """
					[{"id":45,"channel":"STABLE","downloads":{"server:default":{"name":"v","checksums":{"sha256":"%s"},"size":%d,"url":"https://fill-data.papermc.io/v1/objects/velocity/velocity.jar"}}}]"""
					.formatted(hex("SHA-256", VELOCITY_JAR), VELOCITY_JAR.length));
			files.put("https://fill-data.papermc.io/v1/objects/velocity/velocity.jar", VELOCITY_JAR);
		}

		@Override
		public String text(URI uri) throws PrepareException {
			requests.add(uri.toString());
			String text = texts.get(uri.toString());
			if (text == null) {
				throw new PrepareException(Failure.DOWNLOAD, "no fixture for " + uri);
			}
			return text;
		}

		@Override
		public void download(URI uri, Path target, Checksum expected, long maxBytes, LongConsumer progress) throws PrepareException {
			requests.add(uri.toString());
			byte[] data = files.get(uri.toString());
			if (data == null) {
				throw new PrepareException(Failure.DOWNLOAD, "no file for " + uri);
			}
			try {
				if (expected != null) {
					MessageDigest digest = expected.newDigest();
					digest.update(data);
					if (!expected.matches(digest)) {
						throw new PrepareException(Failure.DOWNLOAD, "integrity check failed");
					}
				}
				Files.createDirectories(target.getParent());
				Files.write(target, data);
				progress.accept(data.length);
			} catch (IOException e) {
				throw new PrepareException(Failure.DOWNLOAD, "io", e);
			}
		}
	}

	private static ProjectVersion make(String id, String project, List<String> loaders, String file, byte[] bytes,
			Channel channel, Dependency... dependencies) {
		return new ProjectVersion(id, project, id, id, channel, loaders, List.of("26.3"), "", List.of(
				new VersionFile("https://cdn.modrinth.com/data/" + project + "/" + file, file, hex("SHA-512", bytes),
						hex("SHA-1", bytes), bytes.length, true)), List.of(dependencies));
	}

	/** A catalog with a plugin that requires a library. */
	private static final class FakeCatalog implements ContentResolver.Catalog {
		final Map<String, List<ProjectVersion>> byProject = new HashMap<>();

		FakeCatalog() {
			byProject.put("plug", List.of(
					make("plug-beta", "plug", List.of("paper"), "plugin-1.1-beta.jar", PLUGIN_BYTES, Channel.BETA),
					make("plug-dp", "plug", List.of("datapack"), "plugin-1.0.zip", PLUGIN_BYTES, Channel.RELEASE),
					make("plug-1", "plug", List.of("paper", "spigot"), "plugin-1.0.jar", PLUGIN_BYTES, Channel.RELEASE,
							new Dependency("lib", null, Dependency.Kind.REQUIRED),
							new Dependency("opt", null, Dependency.Kind.OPTIONAL))));
			byProject.put("lib", List.of(make("lib-2", "lib", List.of("bukkit"), "lib-2.0.jar", LIB_BYTES, Channel.RELEASE)));
			byProject.put("fabricmod", List.of(make("fm-1", "fabricmod", List.of("fabric"), "mod.jar", LIB_BYTES, Channel.RELEASE)));
			byProject.put("goodpack", List.of(make("gp-1", "goodpack", List.of("fabric"), "good.mrpack", GOOD_PACK, Channel.RELEASE)));
			byProject.put("oldpack", List.of(make("op-1", "oldpack", List.of("fabric"), "old.mrpack", OLD_PACK, Channel.RELEASE)));
			byProject.put("foreignpack", List.of(make("fp-1", "foreignpack", List.of("fabric"), "foreign.mrpack", FOREIGN_PACK,
					Channel.RELEASE)));
			byProject.put("fabricproxy-lite", List.of(make("fpl-1", "fabricproxy-lite", List.of("fabric"), "proxy.jar",
					PLUGIN_BYTES, Channel.RELEASE)));
			byProject.put("enemy", List.of(make("enemy-1", "enemy", List.of("paper"), "enemy.jar", LIB_BYTES, Channel.RELEASE,
					new Dependency("plug", null, Dependency.Kind.INCOMPATIBLE))));
		}

		@Override
		public List<ProjectVersion> versions(String projectId, List<String> loaders, String minecraftVersion) {
			return byProject.getOrDefault(projectId, List.of()).stream()
					.filter(v -> v.loaders().stream().anyMatch(loaders::contains)).toList();
		}

		@Override
		public ProjectVersion version(String versionId) throws PrepareException {
			return byProject.values().stream().flatMap(List::stream).filter(v -> v.id().equals(versionId)).findFirst()
					.orElseThrow(() -> new PrepareException(Failure.CONTENT, "no version " + versionId));
		}
	}

	private static final ServerInstance.Progress QUIET = new ServerInstance.Progress() {
		@Override
		public void state(State state, int percent) {
		}

		@Override
		public void log(String line) {
		}

		@Override
		public void checkCancelled() {
		}
	};

	private LaunchRequest request(UUID rack, Platform platform, boolean eula, String... projects) {
		List<Wanted> disks = new ArrayList<>();
		for (String project : projects) {
			disks.add(new Wanted(project, project, null));
		}
		return new LaunchRequest(rack, platform, "26.3", 2048, 10, 25600, "127.0.0.1", false, eula, "A rack server", disks);
	}

	private Deployment deployment(FakeFetcher fetcher, LaunchRequest request, int java) {
		return new Deployment(root, fetcher, new ContentResolver(new FakeCatalog()), request, "/usr/bin/java", java);
	}

	@Test
	void paperServerIsLaidOutWithPluginsAndTheirDependencies() throws Exception {
		UUID rack = UUID.randomUUID();
		FakeFetcher fetcher = new FakeFetcher();

		ServerInstance.Prepared prepared = deployment(fetcher, request(rack, Platform.PAPER, true, "plug"), 25).prepare(QUIET);

		Path dir = prepared.directory();
		assertEquals(Deployment.instanceDir(root, rack), dir);
		assertEquals("server jar bytes", Files.readString(dir.resolve("server.jar")));
		assertTrue(Files.readString(dir.resolve("eula.txt")).contains("eula=true"));
		assertEquals("plugin bytes", Files.readString(dir.resolve("plugins/plugin-1.0.jar")), "the stable Paper build is preferred over the beta and the data pack");
		assertEquals("library bytes", Files.readString(dir.resolve("plugins/lib-2.0.jar")), "required dependencies are added");
		List<String> properties = Files.readAllLines(dir.resolve("server.properties"));
		assertTrue(properties.containsAll(List.of("server-port=25600", "max-players=10", "server-ip=127.0.0.1",
				"online-mode=false", "accepts-transfers=true", "motd=A rack server")), properties.toString());
		assertEquals(List.of("/usr/bin/java", "-Xms512M", "-Xmx2048M", "-Dserverworks.rack=" + rack, "-jar", "server.jar", "nogui"),
				prepared.command());
		assertTrue(fetcher.requests.contains("https://fill-data.papermc.io/v1/objects/stable/paper.jar"), "the stable build is chosen");
		assertFalse(fetcher.requests.contains("https://fill-data.papermc.io/v1/objects/beta/paper.jar"));
	}

	@Test
	void redeployingReusesTheCacheAndRemovesOnlyItsOwnOldFiles() throws Exception {
		UUID rack = UUID.randomUUID();
		FakeFetcher fetcher = new FakeFetcher();
		Path dir = deployment(fetcher, request(rack, Platform.PAPER, true, "plug"), 25).prepare(QUIET).directory();
		Files.writeString(dir.resolve("plugins/handmade.jar"), "the owner's own plugin");
		Files.writeString(dir.resolve("server.properties"), Files.readString(dir.resolve("server.properties")) + "difficulty=hard\n");
		long downloadsBefore = fetcher.requests.stream().filter(r -> r.endsWith(".jar")).count();

		deployment(fetcher, request(rack, Platform.PAPER, true), 25).prepare(QUIET);

		assertEquals(downloadsBefore, fetcher.requests.stream().filter(r -> r.endsWith(".jar")).count(), "nothing is downloaded twice");
		assertFalse(Files.exists(dir.resolve("plugins/plugin-1.0.jar")), "a removed disk's file goes away");
		assertFalse(Files.exists(dir.resolve("plugins/lib-2.0.jar")));
		assertTrue(Files.exists(dir.resolve("plugins/handmade.jar")), "files the owner added are never touched");
		assertTrue(Files.readAllLines(dir.resolve("server.properties")).contains("difficulty=hard"));
	}

	@Test
	void nothingHappensWithoutTheEula() {
		FakeFetcher fetcher = new FakeFetcher();
		PrepareException error = assertThrows(PrepareException.class,
				() -> deployment(fetcher, request(UUID.randomUUID(), Platform.PAPER, false, "plug"), 25).prepare(QUIET));
		assertEquals(Failure.EULA, error.failure());
		assertTrue(fetcher.requests.isEmpty(), "no network request may be made before the EULA is accepted");
		assertFalse(Files.exists(root.resolve("instances")));
	}

	@Test
	void problemsAreCaughtBeforeAnythingIsInstalled() {
		FakeFetcher fetcher = new FakeFetcher();
		assertEquals(Failure.CONTENT, assertThrows(PrepareException.class,
				() -> deployment(fetcher, request(UUID.randomUUID(), Platform.PAPER, true, "fabricmod"), 25).prepare(QUIET)).failure());
		assertEquals(Failure.CONTENT, assertThrows(PrepareException.class,
				() -> deployment(fetcher, request(UUID.randomUUID(), Platform.PAPER, true, "plug", "enemy"), 25).prepare(QUIET)).failure());
		assertEquals(Failure.LAUNCH, assertThrows(PrepareException.class,
				() -> deployment(fetcher, request(UUID.randomUUID(), Platform.PAPER, true), 21).prepare(QUIET)).failure());
		assertEquals(Failure.UNSUPPORTED, assertThrows(PrepareException.class,
				() -> deployment(fetcher, request(UUID.randomUUID(), Platform.WATERFALL, true), 25).prepare(QUIET)).failure());
		assertFalse(Files.exists(root.resolve("instances")));
	}

	@Test
	void aTamperedDownloadIsRejected() {
		FakeFetcher fetcher = new FakeFetcher();
		fetcher.files.put("https://piston-data.mojang.com/v1/objects/abc/server.jar", "something else".getBytes(StandardCharsets.UTF_8));
		PrepareException error = assertThrows(PrepareException.class,
				() -> deployment(fetcher, request(UUID.randomUUID(), Platform.VANILLA, true), 25).prepare(QUIET));
		assertEquals(Failure.DOWNLOAD, error.failure());
		assertFalse(Files.exists(root.resolve("instances")));
	}

	private LaunchRequest packRequest(String project) {
		return new LaunchRequest(UUID.randomUUID(), Platform.FABRIC, "26.3", 2048, 10, 25600, "127.0.0.1", false, true, "Packed",
				List.of(new Wanted(project, "Test Pack", null, dev.serverworks.catalog.model.SideSupport.REQUIRED, true)));
	}

	@Test
	void packCartridgesInstallTheServerHalfOfAModpack() throws Exception {
		FakeFetcher fetcher = new FakeFetcher();
		Path dir = deployment(fetcher, packRequest("goodpack"), 25).prepare(QUIET).directory();

		assertEquals("pack mod bytes", Files.readString(dir.resolve("mods/packmod.jar")));
		assertFalse(Files.exists(dir.resolve("mods/shaders.jar")), "client-only files stay out of the server");
		assertFalse(fetcher.requests.contains("https://cdn.modrinth.com/data/sh/shaders.jar"));
		assertEquals("from the pack", Files.readString(dir.resolve("config/pack.cfg")));
		assertEquals("server only", Files.readString(dir.resolve("server-only.txt")));
		assertFalse(Files.exists(dir.resolve(".pack")), "the pack file itself is never placed in the server");
		assertTrue(java.util.Arrays.equals(GOOD_PACK, Files.readAllBytes(dir.resolve(ClientPack.FILE_NAME))),
				"players get the whole modpack as their client pack");

		// The owner's edits to the pack's settings survive a restart with the same pack.
		Files.writeString(dir.resolve("config/pack.cfg"), "edited");
		deployment(fetcher, new LaunchRequest(packRequestId(dir), Platform.FABRIC, "26.3", 2048, 10, 25600, "127.0.0.1", false,
				true, "Packed", packRequest("goodpack").disks()), 25).prepare(QUIET);
		assertEquals("edited", Files.readString(dir.resolve("config/pack.cfg")));
	}

	private static UUID packRequestId(Path dir) {
		return UUID.fromString(dir.getFileName().toString());
	}

	@Test
	void modpackOverridesAreBoundedInCount(@org.junit.jupiter.api.io.TempDir Path scratch) throws Exception {
		Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
		for (int i = 0; i <= ModpackIndex.MAX_OVERRIDE_FILES; i++) {
			entries.put("overrides/f" + i + ".txt", new byte[0]);
		}
		Path pack = scratch.resolve("many.mrpack");
		Files.write(pack, zip(entries));
		Path dir = Files.createDirectories(scratch.resolve("server"));
		IOException error = assertThrows(IOException.class, () -> ModpackIndex.extractOverrides(pack, dir));
		assertTrue(error.getMessage().contains("too many"), error.getMessage());
	}

	@Test
	void forwardingSecretsAreReadableOnlyByTheirOwner() throws Exception {
		FakeFetcher fetcher = new FakeFetcher();
		ProxyRole.Proxy proxy = new ProxyRole.Proxy(Forwarding.MODERN, "secret-secret-secret-secret",
				List.of(new ProxyRole.Target("paper-1", 25601)));
		Path dir = deployment(fetcher, new LaunchRequest(UUID.randomUUID(), Platform.VELOCITY, "26.3", 1024, 10, 25600,
				"127.0.0.1", true, true, "Proxy", List.of(), null, proxy), 25).prepare(QUIET).directory();
		if (java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
			assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(
					Files.getPosixFilePermissions(dir.resolve("forwarding.secret"))));
		}
	}

	@Test
	void modpacksForTheWrongVersionOrFromOutsideModrinthAreRefused() {
		FakeFetcher fetcher = new FakeFetcher();
		PrepareException old = assertThrows(PrepareException.class,
				() -> deployment(fetcher, packRequest("oldpack"), 25).prepare(QUIET));
		assertEquals(Failure.CONTENT, old.failure());
		assertTrue(old.getMessage().contains("1.20.1"), old.getMessage());
		PrepareException foreign = assertThrows(PrepareException.class,
				() -> deployment(fetcher, packRequest("foreignpack"), 25).prepare(QUIET));
		assertEquals(Failure.CONTENT, foreign.failure());
		assertFalse(fetcher.requests.contains("https://github.com/someone/packmod.jar"));
	}

	@Test
	void velocityIsPointedAtTheLinkedServers() throws Exception {
		FakeFetcher fetcher = new FakeFetcher();
		ProxyRole.Proxy proxy = new ProxyRole.Proxy(Forwarding.MODERN, "s3cret-s3cret-s3cret-s3cret",
				List.of(new ProxyRole.Target("paper-1", 25601), new ProxyRole.Target("fabric-2", 25602)));
		LaunchRequest request = new LaunchRequest(UUID.randomUUID(), Platform.VELOCITY, "26.3", 1024, 10, 25600, "127.0.0.1",
				true, true, "Proxy", List.of(), null, proxy);
		Path dir = deployment(fetcher, request, 25).prepare(QUIET).directory();

		List<String> toml = Files.readAllLines(dir.resolve("velocity.toml"));
		assertTrue(toml.contains("bind = \"127.0.0.1:25600\""), toml.toString());
		assertTrue(toml.contains("player-info-forwarding-mode = \"MODERN\""), toml.toString());
		assertTrue(toml.contains("paper-1 = \"127.0.0.1:25601\""), toml.toString());
		assertTrue(toml.contains("try = [\"paper-1\", \"fabric-2\"]"), toml.toString());
		assertFalse(toml.stream().anyMatch(line -> line.contains("lobby")), "the example servers are gone: " + toml);
		assertTrue(toml.contains("compression-threshold = 256"), "unrelated settings are kept");
		assertEquals("s3cret-s3cret-s3cret-s3cret", Files.readString(dir.resolve("forwarding.secret")));
		assertFalse(Files.exists(dir.resolve("server.properties")), "proxies do not use server.properties");

		assertEquals(Failure.CONTENT, assertThrows(PrepareException.class, () -> deployment(fetcher, new LaunchRequest(
				UUID.randomUUID(), Platform.VELOCITY, "26.3", 1024, 10, 25600, "", true, true, "Proxy", List.of()), 25)
				.prepare(QUIET)).failure(), "a proxy with nothing behind it is refused");
	}

	@Test
	void serversBehindAProxyTrustItAndGoBackToNormalAfter() throws Exception {
		FakeFetcher fetcher = new FakeFetcher();
		UUID rack = UUID.randomUUID();
		ProxyRole.Backend role = new ProxyRole.Backend(Forwarding.MODERN, "abcdefabcdefabcdefabcdef");
		LaunchRequest proxied = new LaunchRequest(rack, Platform.PAPER, "26.3", 2048, 10, 25601, "", true, true, "Behind",
				List.of(), role, null);
		Path dir = deployment(fetcher, proxied, 25).prepare(QUIET).directory();
		List<String> properties = Files.readAllLines(dir.resolve("server.properties"));
		assertTrue(properties.containsAll(List.of("server-ip=127.0.0.1", "online-mode=false", "white-list=false")),
				properties.toString());
		List<String> paper = Files.readAllLines(dir.resolve("config/paper-global.yml"));
		assertTrue(paper.contains("    enabled: true") && paper.contains("    secret: 'abcdefabcdefabcdefabcdef'"), paper.toString());

		deployment(fetcher, request(rack, Platform.PAPER, true), 25).prepare(QUIET);
		properties = Files.readAllLines(dir.resolve("server.properties"));
		assertTrue(properties.containsAll(List.of("server-ip=127.0.0.1", "white-list=true")), properties.toString());
		assertTrue(Files.readAllLines(dir.resolve("config/paper-global.yml")).contains("    enabled: false"));

		LaunchRequest fabric = new LaunchRequest(UUID.randomUUID(), Platform.FABRIC, "26.3", 2048, 10, 25602, "", true, true,
				"Behind", List.of(), role, null);
		Path fabricDir = deployment(fetcher, fabric, 25).prepare(QUIET).directory();
		assertTrue(Files.exists(fabricDir.resolve("mods/proxy.jar")), "Fabric gets FabricProxy-Lite added for modern forwarding");
		assertTrue(Files.readAllLines(fabricDir.resolve("config/FabricProxy-Lite.toml"))
				.contains("secret = \"abcdefabcdefabcdefabcdef\""));
		byte[] clientPack = Files.readAllBytes(fabricDir.resolve(ClientPack.FILE_NAME));
		assertFalse(new String(clientPack, StandardCharsets.ISO_8859_1).contains("proxy.jar") && containsInIndex(clientPack, "proxy.jar"),
				"the proxy helper is server-only and never in the client pack");
	}

	private static boolean containsInIndex(byte[] pack, String text) throws IOException {
		try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(pack))) {
			while (zip.getNextEntry() != null) {
				if (new String(zip.readAllBytes(), StandardCharsets.UTF_8).contains(text)) {
					return true;
				}
			}
		}
		return false;
	}

	@Test
	void fabricServersGetAClientPackOfTheirMods() throws Exception {
		FakeFetcher fetcher = new FakeFetcher();
		Path dir = deployment(fetcher, request(UUID.randomUUID(), Platform.FABRIC, true, "fabricmod"), 25).prepare(QUIET).directory();
		Path pack = dir.resolve(ClientPack.FILE_NAME);
		assertTrue(Files.exists(pack));
		try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(pack.toFile())) {
			String index = new String(zip.getInputStream(zip.getEntry("modrinth.index.json")).readAllBytes(), StandardCharsets.UTF_8);
			var json = com.google.gson.JsonParser.parseString(index).getAsJsonObject();
			assertEquals(1, json.get("formatVersion").getAsInt());
			assertEquals("0.20.1", json.getAsJsonObject("dependencies").get("fabric-loader").getAsString());
			assertEquals("26.3", json.getAsJsonObject("dependencies").get("minecraft").getAsString());
			var file = json.getAsJsonArray("files").get(0).getAsJsonObject();
			assertEquals("mods/mod.jar", file.get("path").getAsString());
			assertEquals("https://cdn.modrinth.com/data/fabricmod/mod.jar", file.getAsJsonArray("downloads").get(0).getAsString());
			assertEquals(40, file.getAsJsonObject("hashes").get("sha1").getAsString().length());
		}

		Path paper = deployment(fetcher, request(UUID.randomUUID(), Platform.PAPER, true, "plug"), 25).prepare(QUIET).directory();
		assertFalse(Files.exists(paper.resolve(ClientPack.FILE_NAME)), "plugin platforms have no client pack");
	}

	@Test
	void vanillaAndFabricResolveTheirOwnDownloads() throws Exception {
		FakeFetcher fetcher = new FakeFetcher();
		assertEquals("Vanilla 26.3", Providers.get(Platform.VANILLA).plan(fetcher, "26.3").build());
		InstallPlan fabric = Providers.get(Platform.FABRIC).plan(fetcher, "26.3");
		assertEquals("https://meta.fabricmc.net/v2/versions/loader/26.3/0.20.1/1.1.2/server/jar", fabric.serverJar().url());
		assertEquals(25, fabric.javaVersion());
		assertEquals("Paper 26.3 build 248", Providers.get(Platform.PAPER).plan(fetcher, "26.3").build());
		assertEquals(Failure.UNSUPPORTED,
				assertThrows(PrepareException.class, () -> Providers.get(Platform.VANILLA).plan(fetcher, "1.0-nope")).failure());

		Path dir = deployment(fetcher, request(UUID.randomUUID(), Platform.FABRIC, true, "fabricmod"), 25).prepare(QUIET).directory();
		assertTrue(Files.exists(dir.resolve("mods/mod.jar")), "mods go in the mods folder on a mod platform");
	}
}
