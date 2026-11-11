package dev.serverworks.catalog.model;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** One published version of a project, with the files to download and what it depends on. */
public record ProjectVersion(
		String id,
		String projectId,
		String name,
		String versionNumber,
		Channel channel,
		List<String> loaders,
		List<String> gameVersions,
		String datePublished,
		List<VersionFile> files,
		List<Dependency> dependencies) {

	public ProjectVersion {
		loaders = List.copyOf(loaders);
		gameVersions = List.copyOf(gameVersions);
		files = List.copyOf(files);
		dependencies = List.copyOf(dependencies);
	}

	/** The file Modrinth marks as primary, or the first file when none is marked. */
	public Optional<VersionFile> primaryFile() {
		for (VersionFile file : files) {
			if (file.primary()) {
				return Optional.of(file);
			}
		}
		return files.isEmpty() ? Optional.empty() : Optional.of(files.getFirst());
	}

	public enum Channel {
		RELEASE,
		BETA,
		ALPHA;

		public static Channel fromApi(String value) {
			if (value == null) {
				return RELEASE;
			}
			return switch (value.toLowerCase(Locale.ROOT)) {
				case "beta" -> BETA;
				case "alpha" -> ALPHA;
				default -> RELEASE;
			};
		}
	}

	/**
	 * A downloadable file.
	 *
	 * @param sha512 lowercase hex digest; every download is checked against it
	 */
	public record VersionFile(String url, String filename, String sha512, String sha1, long size, boolean primary) {
	}

	/**
	 * A link to another project.
	 *
	 * @param projectId may be null when only {@code versionId} is given
	 * @param versionId may be null when any compatible version will do
	 */
	public record Dependency(String projectId, String versionId, Kind kind) {
		public enum Kind {
			REQUIRED,
			OPTIONAL,
			INCOMPATIBLE,
			EMBEDDED;

			public static Kind fromApi(String value) {
				if (value == null) {
					return OPTIONAL;
				}
				return switch (value.toLowerCase(Locale.ROOT)) {
					case "required" -> REQUIRED;
					case "incompatible" -> INCOMPATIBLE;
					case "embedded" -> EMBEDDED;
					default -> OPTIONAL;
				};
			}
		}
	}
}
