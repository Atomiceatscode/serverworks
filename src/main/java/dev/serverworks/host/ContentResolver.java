package dev.serverworks.host;

import dev.serverworks.catalog.CatalogHosts;
import dev.serverworks.catalog.model.ProjectVersion;
import dev.serverworks.catalog.model.ProjectVersion.Channel;
import dev.serverworks.catalog.model.ProjectVersion.Dependency;
import dev.serverworks.catalog.model.ProjectVersion.VersionFile;
import dev.serverworks.catalog.model.SideSupport;
import dev.serverworks.host.ServerInstance.Failure;
import dev.serverworks.rack.Platform;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the disks in a rack into the exact files to install: the newest version of each
 * project that runs on the rack's platform and Minecraft version, plus everything those
 * versions require.
 */
public final class ContentResolver {
	private static final int MAX_PROJECTS = 400;

	private final Catalog catalog;

	public ContentResolver(Catalog catalog) {
		this.catalog = catalog;
	}

	/** Blocking access to the catalog. */
	public interface Catalog {
		/** Versions of a project for these loaders and this Minecraft version, newest first. */
		List<ProjectVersion> versions(String projectId, List<String> loaders, String minecraftVersion) throws PrepareException;

		ProjectVersion version(String versionId) throws PrepareException;
	}

	/**
	 * @param pinnedVersion a specific version id to use, or null for the newest compatible one
	 * @param clientSide    whether players need it too, which decides whether it goes in the client pack
	 */
	public record Wanted(String projectId, String title, @Nullable String pinnedVersion, SideSupport clientSide,
			boolean modpack) {
		public Wanted(String projectId, String title, @Nullable String pinnedVersion) {
			this(projectId, title, pinnedVersion, SideSupport.UNKNOWN, false);
		}

		public Wanted(String projectId, String title, @Nullable String pinnedVersion, SideSupport clientSide) {
			this(projectId, title, pinnedVersion, clientSide, false);
		}
	}

	/** Where a modpack's own file is listed before it is unpacked. Never placed in a server folder. */
	public static final String PACK_DIRECTORY = ".pack";

	public List<ContentFile> resolve(Platform platform, String minecraftVersion, List<Wanted> disks) throws PrepareException {
		Map<String, ProjectVersion> chosen = new LinkedHashMap<>();
		Map<String, String> titles = new HashMap<>();
		Map<String, Boolean> automatic = new HashMap<>();
		Map<String, SideSupport> sides = new HashMap<>();
		Map<String, Boolean> packs = new HashMap<>();
		Deque<Wanted> queue = new ArrayDeque<>(disks);
		for (Wanted disk : disks) {
			automatic.put(disk.projectId(), false);
		}

		while (!queue.isEmpty()) {
			Wanted wanted = queue.poll();
			if (chosen.containsKey(wanted.projectId())) {
				continue;
			}
			if (chosen.size() >= MAX_PROJECTS) {
				throw new PrepareException(Failure.CONTENT, "Too many projects to install");
			}
			ProjectVersion version = pick(platform, minecraftVersion, wanted);
			chosen.put(wanted.projectId(), version);
			titles.put(wanted.projectId(), wanted.title());
			sides.put(wanted.projectId(), wanted.clientSide());
			packs.put(wanted.projectId(), wanted.modpack());
			if (wanted.modpack()) {
				// A modpack lists its own files; they are read from it once it is downloaded.
				continue;
			}
			automatic.putIfAbsent(wanted.projectId(), true);

			for (Dependency dependency : version.dependencies()) {
				if (dependency.kind() != Dependency.Kind.REQUIRED) {
					continue;
				}
				String projectId = dependency.projectId();
				if (projectId == null && dependency.versionId() != null) {
					projectId = catalog.version(dependency.versionId()).projectId();
				}
				if (projectId != null && !chosen.containsKey(projectId)) {
					queue.add(new Wanted(projectId, wanted.title() + " dependency", null));
				}
			}
		}

		for (Map.Entry<String, ProjectVersion> entry : chosen.entrySet()) {
			for (Dependency dependency : entry.getValue().dependencies()) {
				if (dependency.kind() == Dependency.Kind.INCOMPATIBLE && dependency.projectId() != null
						&& chosen.containsKey(dependency.projectId())) {
					throw new PrepareException(Failure.CONTENT, titles.get(entry.getKey()) + " cannot be used together with "
							+ titles.get(dependency.projectId()));
				}
			}
		}

		List<ContentFile> files = new ArrayList<>();
		for (Map.Entry<String, ProjectVersion> entry : chosen.entrySet()) {
			files.add(file(platform, entry.getKey(), entry.getValue(), titles.get(entry.getKey()), automatic.get(entry.getKey()),
					sides.get(entry.getKey()), packs.getOrDefault(entry.getKey(), false)));
		}
		return files;
	}

	/** The version a rack on this platform would install for an unpinned disk of this project. */
	public ProjectVersion newest(Platform platform, String minecraftVersion, String projectId) throws PrepareException {
		return pick(platform, minecraftVersion, new Wanted(projectId, projectId, null));
	}

	private ProjectVersion pick(Platform platform, String minecraftVersion, Wanted wanted) throws PrepareException {
		if (wanted.pinnedVersion() != null) {
			ProjectVersion pinned = catalog.version(wanted.pinnedVersion());
			if (!pinned.projectId().equals(wanted.projectId()) || preferredLoader(platform, pinned) == null
					|| !pinned.gameVersions().contains(minecraftVersion)) {
				throw new PrepareException(Failure.CONTENT, "The version pinned on the " + wanted.title()
						+ " disk does not run on " + platform.id() + " " + minecraftVersion);
			}
			return pinned;
		}
		List<ProjectVersion> versions = catalog.versions(wanted.projectId(), platform.acceptedLoaders(), minecraftVersion);
		// Prefer the platform's own kind of build over a fallback (a Paper plugin over a
		// data pack of the same project), and a release over a beta of that kind.
		for (String loader : platform.acceptedLoaders()) {
			ProjectVersion newest = null;
			for (ProjectVersion version : versions) {
				if (!version.loaders().contains(loader) || version.primaryFile().isEmpty()) {
					continue;
				}
				if (version.channel() == Channel.RELEASE) {
					return version;
				}
				if (newest == null) {
					newest = version;
				}
			}
			if (newest != null) {
				return newest;
			}
		}
		throw new PrepareException(Failure.CONTENT, wanted.title() + " has no version for " + platform.id() + " "
				+ minecraftVersion);
	}

	private static @Nullable String preferredLoader(Platform platform, ProjectVersion version) {
		for (String loader : platform.acceptedLoaders()) {
			if (version.loaders().contains(loader)) {
				return loader;
			}
		}
		return null;
	}

	private static ContentFile file(Platform platform, String projectId, ProjectVersion version, String title, boolean automatic,
			SideSupport clientSide, boolean modpack) throws PrepareException {
		VersionFile file = version.primaryFile().orElseThrow();
		String name = file.filename();
		boolean safeName = name.matches("[A-Za-z0-9][A-Za-z0-9 ._+()\\[\\]-]{0,150}") && !name.contains("..")
				&& (modpack ? name.endsWith(".mrpack") : name.endsWith(".jar") || name.endsWith(".zip"));
		if (!safeName || !CatalogHosts.isCdnUrl(file.url()) || file.sha512().length() != 128) {
			throw new PrepareException(Failure.CONTENT, title + " has a file this rack will not install");
		}
		if (modpack) {
			return new ContentFile(file.url(), name, file.sha512(), file.sha1(), file.size(), PACK_DIRECTORY, title, false,
					clientSide, projectId, version.id(), version.versionNumber());
		}
		String loader = preferredLoader(platform, version);
		String directory;
		if ("datapack".equals(loader)) {
			directory = "world/datapacks";
		} else {
			directory = platform.group() == Platform.Group.MOD ? "mods" : "plugins";
		}
		return new ContentFile(file.url(), name, file.sha512(), file.sha1(), file.size(), directory, title, automatic,
				clientSide, projectId, version.id(), version.versionNumber());
	}
}
