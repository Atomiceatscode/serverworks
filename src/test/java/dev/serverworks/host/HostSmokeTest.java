package dev.serverworks.host;

import dev.serverworks.catalog.ModrinthClient;
import dev.serverworks.catalog.http.JdkHttpTransport;
import dev.serverworks.host.ServerInstance.State;
import dev.serverworks.rack.Platform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Installs and runs real servers from the real download services, then stops them.
 * Slow and needs the internet, so it only runs with {@code ./gradlew hostSmokeTest}.
 */
@Tag("network")
class HostSmokeTest {
	private static final Path ROOT = Path.of("build", "smoke").toAbsolutePath();
	private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
	private static final PortAllocator PORTS = new PortAllocator(25690, 25699);
	private static final List<ServerInstance> RUNNING = new ArrayList<>();

	@AfterAll
	static void cleanUp() {
		RUNNING.forEach(ServerInstance::killNow);
		EXECUTOR.shutdownNow();
	}

	static Stream<Arguments> platforms() {
		String only = System.getProperty("serverworks.smoke.only", "");
		return Stream.of(
				Arguments.of(Platform.VANILLA, List.of()),
				Arguments.of(Platform.PAPER, List.of("luckperms", "chunky")),
				Arguments.of(Platform.FABRIC, List.of("lithium", "ferrite-core")),
				Arguments.of(Platform.PURPUR, List.of("luckperms")),
				Arguments.of(Platform.FOLIA, List.of("chunky")),
				Arguments.of(Platform.SPONGE, List.of()),
				Arguments.of(Platform.QUILT, List.of("lithium")),
				Arguments.of(Platform.NEOFORGE, List.of("ferrite-core")),
				Arguments.of(Platform.FORGE, List.of()),
				Arguments.of(Platform.SPIGOT, List.of("luckperms")),
				Arguments.of(Platform.BUKKIT, List.of("chunky")))
				.filter(arguments -> only.isEmpty() || List.of(only.split(",")).contains(((Platform) arguments.get()[0]).id()))
				.collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(),
						list -> orPlaceholder(list.stream())));
	}

	@ParameterizedTest
	@MethodSource("platforms")
	void installsStartsAndStops(Platform platform, List<String> projects) throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(platform != null, "filtered out");
		ModrinthClient catalog = new ModrinthClient(new JdkHttpTransport(), "serverworks-smoke-test/0.1.0");
		List<ContentResolver.Wanted> disks = projects.stream().map(slug -> {
			var project = catalog.project(slug).join();
			return new ContentResolver.Wanted(project.id(), project.title(), null);
		}).toList();
		UUID rack = UUID.nameUUIDFromBytes(("smoke-" + platform.id()).getBytes());
		int port = PORTS.acquire();
		LaunchRequest request = new LaunchRequest(rack, platform, "26.3", 1536, 5, port, "127.0.0.1", false, true,
				"Smoke test", disks);
		Deployment deployment = new Deployment(ROOT, new HttpFetcher("serverworks-smoke-test/0.1.0"),
				new ContentResolver(new ModrinthCatalog(catalog)), request, HostRuntime.currentJava(), HostRuntime.currentJavaVersion());

		ServerInstance instance = new ServerInstance(rack, EXECUTOR);
		RUNNING.add(instance);
		long started = System.nanoTime();
		assertTrue(instance.start(port, deployment));
		await(instance, State.RUNNING, 60 * 45);
		System.out.printf("%s ready on port %d after %ds%n", platform.id(), port,
				TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started));

		// Some servers finish their own start-up a moment after saying so; the runtime pings every few seconds anyway.
		StatusPinger.Status status = null;
		for (int attempt = 0; attempt < 10 && status == null; attempt++) {
			try {
				status = StatusPinger.ping("127.0.0.1", port);
			} catch (java.io.IOException e) {
				System.out.println("ping attempt " + attempt + ": " + e);
				Thread.sleep(1000);
			}
		}
		if (status == null) {
			fail(platform.id() + " never answered a status request");
		}
		assertEquals(0, status.online(), "a fresh server has nobody on it");
		assertEquals(5, status.max(), "the rack's player cap must reach the server");

		Path dir = Deployment.instanceDir(ROOT, rack);
		assertEquals(platform.group() == Platform.Group.MOD, Files.exists(dir.resolve(ClientPack.FILE_NAME)),
				"only mod platforms get a client pack");
		String folder = platform.group() == Platform.Group.MOD ? "mods" : "plugins";
		for (String slug : projects) {
			try (Stream<Path> files = Files.list(dir.resolve(folder))) {
				assertTrue(files.findAny().isPresent(), "content must be installed in " + folder);
			}
		}
		List<String> console = instance.console().since(0, 1000).lines();
		if (platform == Platform.PAPER) {
			assertTrue(console.stream().anyMatch(l -> l.contains("LuckPerms")), "LuckPerms must load:\n" + String.join("\n", console));
		}
		if (platform == Platform.FABRIC) {
			assertTrue(console.stream().anyMatch(l -> l.toLowerCase().contains("lithium")), "Lithium must load:\n" + String.join("\n", console));
		}

		instance.stop();
		await(instance, State.STOPPED, 60);
		assertEquals(ServerInstance.Failure.NONE, instance.snapshot().failure());
		assertFalse(Files.exists(dir.resolve(ServerInstance.PID_FILE)));
		PORTS.release(port);
	}

	static Stream<Arguments> proxies() {
		String only = System.getProperty("serverworks.smoke.only", "");
		return orPlaceholder(Stream.of(Arguments.of(Platform.VELOCITY, Forwarding.MODERN),
						Arguments.of(Platform.BUNGEECORD, Forwarding.LEGACY))
				.filter(arguments -> only.isEmpty() || List.of(only.split(",")).contains(((Platform) arguments.get()[0]).id())));
	}

	/** A real modpack on a Pack Cartridge: its server mods are installed and the server starts with them. */
	@org.junit.jupiter.api.Test
	void packCartridgeRuns() throws Exception {
		String only = System.getProperty("serverworks.smoke.only", "");
		org.junit.jupiter.api.Assumptions.assumeTrue(only.isEmpty() || List.of(only.split(",")).contains("pack"));
		ModrinthClient catalog = new ModrinthClient(new JdkHttpTransport(), "serverworks-smoke-test/0.1.0");
		var project = catalog.project("vanilla-perfected").join();
		UUID rack = UUID.nameUUIDFromBytes("smoke-pack".getBytes());
		int port = PORTS.acquire();
		LaunchRequest request = new LaunchRequest(rack, Platform.FABRIC, "26.3", 3072, 5, port, "127.0.0.1", false, true,
				"Pack smoke test", List.of(new ContentResolver.Wanted(project.id(), project.title(), null,
						project.clientSide(), true)));
		ServerInstance instance = new ServerInstance(rack, EXECUTOR);
		RUNNING.add(instance);
		instance.start(port, new Deployment(ROOT, new HttpFetcher("serverworks-smoke-test/0.1.0"),
				new ContentResolver(new ModrinthCatalog(catalog)), request, HostRuntime.currentJava(),
				HostRuntime.currentJavaVersion()));
		await(instance, State.RUNNING, 900);

		Path dir = Deployment.instanceDir(ROOT, rack);
		long mods;
		try (Stream<Path> files = Files.list(dir.resolve("mods"))) {
			mods = files.count();
		}
		System.out.println("pack server running with " + mods + " mods");
		assertTrue(mods > 30, "the pack's server mods must be installed, found " + mods);
		assertTrue(Files.exists(dir.resolve("config")), "the pack's settings must be copied in");
		assertTrue(Files.exists(dir.resolve(ClientPack.FILE_NAME)));
		instance.stop();
		await(instance, State.STOPPED, 120);
		PORTS.release(port);
	}

	/** JUnit refuses an empty parameter list, so a filtered-out test gets one placeholder that it skips. */
	private static Stream<Arguments> orPlaceholder(Stream<Arguments> arguments) {
		List<Arguments> list = arguments.toList();
		return list.isEmpty() ? Stream.of(Arguments.of(null, null)) : list.stream();
	}

	/** A proxy in front of a Paper server: both start, and the proxy answers with its own player cap. */
	@ParameterizedTest
	@MethodSource("proxies")
	void proxyFrontsAServer(Platform proxyPlatform, Forwarding expected) throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(proxyPlatform != null, "filtered out");
		ModrinthClient catalog = new ModrinthClient(new JdkHttpTransport(), "serverworks-smoke-test/0.1.0");
		Forwarding mode = Forwarding.choose(proxyPlatform, List.of(Platform.PAPER));
		assertEquals(expected, mode);
		String secret = "smoke-test-secret-" + proxyPlatform.id();

		UUID backendRack = UUID.nameUUIDFromBytes(("smoke-backend-" + proxyPlatform.id()).getBytes());
		int backendPort = PORTS.acquire();
		LaunchRequest backendRequest = new LaunchRequest(backendRack, Platform.PAPER, "26.3", 1536, 5, backendPort, "", false,
				true, "Behind a proxy", List.of(), new ProxyRole.Backend(mode, secret), null);
		ServerInstance backend = new ServerInstance(backendRack, EXECUTOR);
		RUNNING.add(backend);
		backend.start(backendPort, new Deployment(ROOT, new HttpFetcher("serverworks-smoke-test/0.1.0"),
				new ContentResolver(new ModrinthCatalog(catalog)), backendRequest, HostRuntime.currentJava(),
				HostRuntime.currentJavaVersion()));

		UUID proxyRack = UUID.nameUUIDFromBytes(("smoke-proxy-" + proxyPlatform.id()).getBytes());
		int proxyPort = PORTS.acquire();
		LaunchRequest proxyRequest = new LaunchRequest(proxyRack, proxyPlatform, "26.3", 512, 7, proxyPort, "127.0.0.1", false,
				true, "Smoke proxy", List.of(), null,
				new ProxyRole.Proxy(mode, secret, List.of(new ProxyRole.Target("paper-1", backendPort))));
		ServerInstance proxy = new ServerInstance(proxyRack, EXECUTOR);
		RUNNING.add(proxy);
		proxy.start(proxyPort, new Deployment(ROOT, new HttpFetcher("serverworks-smoke-test/0.1.0"),
				new ContentResolver(new ModrinthCatalog(catalog)), proxyRequest, HostRuntime.currentJava(),
				HostRuntime.currentJavaVersion()));

		await(backend, State.RUNNING, 600);
		await(proxy, State.RUNNING, 600);
		System.out.printf("%s in front of paper: proxy on %d, server on %d%n", proxyPlatform.id(), proxyPort, backendPort);
		assertEquals(7, StatusPinger.ping("127.0.0.1", proxyPort).max(), "the proxy answers for the whole network");

		Path backendDir = Deployment.instanceDir(ROOT, backendRack);
		List<String> paper = Files.readAllLines(backendDir.resolve("config/paper-global.yml"));
		List<String> spigot = Files.readAllLines(backendDir.resolve("spigot.yml"));
		assertEquals(mode == Forwarding.MODERN, paper.contains("    enabled: true"), String.join("\n", paper));
		assertEquals(mode == Forwarding.LEGACY, spigot.contains("  bungeecord: true"), String.join("\n", spigot));

		proxy.stop();
		backend.stop();
		await(proxy, State.STOPPED, 60);
		await(backend, State.STOPPED, 60);
		PORTS.release(proxyPort);
		PORTS.release(backendPort);
	}

	private static void await(ServerInstance instance, State wanted, int seconds) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
		while (System.nanoTime() < deadline) {
			ServerInstance.Snapshot snapshot = instance.snapshot();
			if (snapshot.state() == wanted) {
				return;
			}
			if (snapshot.state() == State.FAILED || snapshot.state() == State.CRASHED) {
				break;
			}
			Thread.sleep(200);
		}
		List<String> console = instance.console().since(0, 1000).lines();
		fail("Expected " + wanted + " but got " + instance.snapshot() + "\n"
				+ String.join("\n", console.subList(Math.max(0, console.size() - 60), console.size())));
	}
}
