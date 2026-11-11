package dev.serverworks.host;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Every child server this game process owns.
 *
 * <p>It belongs to the process, not to a world, so a server keeps running when its rack's
 * chunk unloads. A shutdown hook makes sure no child outlives the game.
 */
public final class InstanceManager {

	private final Path root;
	private final Map<UUID, ServerInstance> instances = new ConcurrentHashMap<>();
	private final Map<UUID, Integer> ports = new ConcurrentHashMap<>();
	private final PortAllocator portAllocator;
	private final AtomicInteger threadIds = new AtomicInteger();
	private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
		Thread thread = new Thread(runnable, "Serverworks-Host-" + threadIds.incrementAndGet());
		thread.setDaemon(true);
		return thread;
	});

	public InstanceManager(Path root, int firstPort, int lastPort) {
		this.root = root;
		this.portAllocator = new PortAllocator(firstPort, lastPort);
		Runtime.getRuntime().addShutdownHook(new Thread(this::killAll, "Serverworks-Shutdown"));
	}

	public Path root() {
		return root;
	}

	public ServerInstance get(UUID rackId) {
		return instances.computeIfAbsent(rackId, id -> new ServerInstance(id, executor));
	}

	public java.util.Collection<ServerInstance> all() {
		return instances.values();
	}

	/** The instance for a rack if one has ever been used this session. */
	public ServerInstance peek(UUID rackId) {
		return instances.get(rackId);
	}

	public int activeCount() {
		int count = 0;
		for (ServerInstance instance : instances.values()) {
			if (instance.snapshot().state().active()) {
				count++;
			}
		}
		return count;
	}

	/** The port a rack keeps for the whole session, or -1 when the range is exhausted. */
	public int portFor(UUID rackId) {
		Integer existing = ports.get(rackId);
		if (existing != null) {
			return existing;
		}
		int port = portAllocator.acquire();
		if (port > 0) {
			ports.put(rackId, port);
		}
		return port;
	}

	/** Asks every server to stop cleanly, without waiting. */
	public void stopAll() {
		instances.values().forEach(ServerInstance::stop);
	}

	/** Ends every server now, waiting only briefly for each. Runs at game shutdown. */
	public void killAll() {
		List<Thread> threads = new ArrayList<>();
		for (ServerInstance instance : instances.values()) {
			Thread thread = new Thread(instance::killNow, "Serverworks-Kill");
			thread.start();
			threads.add(thread);
		}
		for (Thread thread : threads) {
			try {
				thread.join(12_000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}
}
