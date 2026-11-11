package dev.serverworks.host;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Process-wide state of the server runtime: where its files live, the running servers,
 * and whether this machine's owner has agreed to the Minecraft EULA.
 */
public final class HostRuntime {
	/** How many rack servers one game may run at once. */
	public static int maxRunning() {
		return dev.serverworks.ServerworksConfig.get().maxRunning;
	}
	private static final String EULA_FILE = "eula-accepted.txt";
	/** How long a server may sit empty before it goes to sleep. The system property overrides the config, for tests. */
	public static long idleMinutes() {
		return Long.getLong("serverworks.idleMinutes", dev.serverworks.ServerworksConfig.get().idleMinutes);
	}
	private static final int PING_SECONDS = 5;

	private final Path root;
	private final InstanceManager instances;
	private final Fetcher fetcher;
	private volatile boolean eulaAccepted;
	private final Map<UUID, IdleTracker> idle = new ConcurrentHashMap<>();
	private final Map<UUID, ProxyRole.Backend> links = new ConcurrentHashMap<>();
	private final ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Serverworks-Monitor");
		thread.setDaemon(true);
		return thread;
	});

	public HostRuntime(Path root, String userAgent) {
		this.root = root;
		dev.serverworks.ServerworksConfig config = dev.serverworks.ServerworksConfig.get();
		this.instances = new InstanceManager(root, config.firstPort, config.lastPort);
		this.fetcher = new HttpFetcher(userAgent);
		this.eulaAccepted = Files.exists(root.resolve(EULA_FILE));
		monitor.scheduleWithFixedDelay(this::checkServers, PING_SECONDS, PING_SECONDS, TimeUnit.SECONDS);
	}

	/** Asks every running server how many players it has, and puts long-empty ones to sleep. */
	private void checkServers() {
		long now = System.currentTimeMillis();
		for (ServerInstance instance : instances.all()) {
			ServerInstance.Snapshot snapshot = instance.snapshot();
			IdleTracker tracker = idle.computeIfAbsent(instance.rackId(),
					id -> new IdleTracker(TimeUnit.MINUTES.toMillis(Math.max(1, idleMinutes()))));
			if (snapshot.state() != ServerInstance.State.RUNNING) {
				tracker.reset();
				continue;
			}
			try {
				StatusPinger.Status status = StatusPinger.ping("127.0.0.1", snapshot.port());
				instance.reportPlayers(status.online(), status.max());
				// Servers behind a proxy stay up even when empty: the proxy may send someone at any moment.
				boolean mayHibernate = idleMinutes() > 0 && !links.containsKey(instance.rackId());
				if (mayHibernate && tracker.observe(status.online(), now)) {
					tracker.reset();
					instance.hibernate();
				}
			} catch (IOException e) {
				// Busy or briefly unreachable: try again next round, and never sleep on a failed ping.
				tracker.observe(-1, now);
			}
		}
	}

	public Path root() {
		return root;
	}

	public InstanceManager instances() {
		return instances;
	}

	public Fetcher fetcher() {
		return fetcher;
	}

	public boolean eulaAccepted() {
		return eulaAccepted;
	}

	/** Records that the named player agreed to the EULA for every rack on this machine. */
	public void acceptEula(String acceptedBy) throws IOException {
		Files.createDirectories(root);
		String clean = acceptedBy.replaceAll("[^A-Za-z0-9_ .-]", "");
		Files.writeString(root.resolve(EULA_FILE), String.format(Locale.ROOT,
				"The Minecraft EULA (https://aka.ms/MinecraftEULA) was accepted in game by %s at %s.%n", clean, Instant.now()),
				StandardCharsets.UTF_8);
		eulaAccepted = true;
	}

	/** How a rack's server was last started behind a proxy, or null when it stands alone. */
	public ProxyRole.@org.jspecify.annotations.Nullable Backend linkOf(UUID rackId) {
		return links.get(rackId);
	}

	public void setLink(UUID rackId, ProxyRole.@org.jspecify.annotations.Nullable Backend link) {
		if (link == null) {
			links.remove(rackId);
		} else {
			links.put(rackId, link);
		}
	}

	/** The secret a proxy shares with the servers behind it. Made once per proxy rack and kept on disk. */
	public String secretFor(UUID proxyRackId) throws IOException {
		Path file = root.resolve("secrets").resolve(proxyRackId + ".txt");
		if (Files.exists(file)) {
			String existing = Files.readString(file, StandardCharsets.UTF_8).strip();
			if (existing.length() >= 24) {
				return existing;
			}
		}
		byte[] bytes = new byte[24];
		new java.security.SecureRandom().nextBytes(bytes);
		String secret = java.util.HexFormat.of().formatHex(bytes);
		Files.createDirectories(file.getParent());
		Files.writeString(file, secret, StandardCharsets.UTF_8);
		ownerOnly(file);
		return secret;
	}

	/** Makes a file readable only by the user running the game, where the file system supports it. */
	public static void ownerOnly(Path file) {
		try {
			Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
		} catch (UnsupportedOperationException | IOException ignored) {
			// Windows and some file systems have no such permissions; the file stays as created.
		}
	}

	/** The Java executable running this game, which child servers reuse. */
	public static String currentJava() {
		boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
		return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString();
	}

	public static int currentJavaVersion() {
		return Runtime.version().feature();
	}
}
