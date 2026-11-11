package dev.serverworks.machine;

import dev.serverworks.Serverworks;
import dev.serverworks.catalog.model.ProjectType;
import dev.serverworks.catalog.model.ProjectVersion;
import dev.serverworks.host.ContentResolver;
import dev.serverworks.host.Deployment;
import dev.serverworks.host.ModrinthCatalog;
import dev.serverworks.host.PrepareException;
import dev.serverworks.item.DiskLabel;
import dev.serverworks.net.DiskUpdatesPayload.Update;
import dev.serverworks.rack.Platform;
import net.minecraft.SharedConstants;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Finds disks whose project has a newer compatible version than the one the rack uses:
 * the pinned version, or for unpinned disks the version installed at the last start.
 * Answers are cached for a while per rack, because each check asks the catalog once per disk.
 */
public final class DiskUpdates {
	private static final long CACHE_MILLIS = TimeUnit.MINUTES.toMillis(10);
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Serverworks-Updates");
		thread.setDaemon(true);
		return thread;
	});
	private static final Map<UUID, Cached> CACHE = new ConcurrentHashMap<>();

	private record Cached(int key, long at, CompletableFuture<List<Update>> result) {
	}

	private DiskUpdates() {
	}

	/** Starts (or reuses) a check for this rack. Call on the server thread; the answer arrives later. */
	public static CompletableFuture<List<Update>> check(UUID rackId, Platform platform, List<DiskLabel> disks) {
		int key = Objects.hash(platform, disks);
		long now = System.currentTimeMillis();
		Cached cached = CACHE.get(rackId);
		if (cached != null && cached.key() == key && now - cached.at() < CACHE_MILLIS) {
			return cached.result();
		}
		Path instanceDir = Deployment.instanceDir(Serverworks.host().root(), rackId);
		List<DiskLabel> snapshot = List.copyOf(disks);
		String minecraft = SharedConstants.getCurrentVersion().name();
		CompletableFuture<List<Update>> result = CompletableFuture.supplyAsync(
				() -> find(platform, minecraft, snapshot, Deployment.installedVersions(instanceDir)), EXECUTOR);
		CACHE.put(rackId, new Cached(key, now, result));
		return result;
	}

	/** Forgets a rack's answer, after its disks were updated. */
	public static void forget(UUID rackId) {
		CACHE.remove(rackId);
	}

	static List<Update> find(Platform platform, String minecraft, List<DiskLabel> disks,
			Map<String, Deployment.Installed> installed) {
		ContentResolver resolver = new ContentResolver(new ModrinthCatalog(Serverworks.catalog()));
		List<Update> updates = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (DiskLabel disk : disks) {
			if (disk.type() == ProjectType.MODPACK || !seen.add(disk.projectId())) {
				continue;
			}
			ProjectVersion newest;
			try {
				newest = resolver.newest(platform, minecraft, disk.projectId());
			} catch (PrepareException | RuntimeException e) {
				continue;
			}
			if (disk.pinnedVersion().isPresent()) {
				if (!disk.pinnedVersion().get().equals(newest.id())) {
					updates.add(new Update(disk.projectId(), disk.pinnedName().orElse("?"), newest.versionNumber(), true));
				}
				continue;
			}
			Deployment.Installed current = installed.get(disk.projectId());
			if (current != null && !current.versionId().equals(newest.id())) {
				updates.add(new Update(disk.projectId(), current.versionNumber(), newest.versionNumber(), false));
			}
		}
		return updates;
	}

	/** The newest version id for a pinned disk's update, looked up again when the update is applied. */
	static ProjectVersion newest(Platform platform, String projectId) throws PrepareException {
		return new ContentResolver(new ModrinthCatalog(Serverworks.catalog()))
				.newest(platform, SharedConstants.getCurrentVersion().name(), projectId);
	}

	static ExecutorService executor() {
		return EXECUTOR;
	}
}
