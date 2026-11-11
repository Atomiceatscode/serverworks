package dev.serverworks.host;

import dev.serverworks.catalog.model.SideSupport;
import org.jspecify.annotations.Nullable;

/**
 * One file to install on a server.
 *
 * @param directory  where it goes, relative to the server folder: {@code mods},
 *                   {@code plugins} or {@code world/datapacks}
 * @param project    the title of the project it belongs to, for messages
 * @param automatic  true when no disk asked for it and it was added as a dependency
 * @param clientSide whether players need it too; unknown for dependencies
 * @param projectId  the catalog project, or null for files a modpack lists
 * @param versionId  the catalog version installed, or null for files a modpack lists
 * @param versionNumber that version's name as its author wrote it, for the Server Core
 */
public record ContentFile(String url, String fileName, String sha512, @Nullable String sha1, long size, String directory,
		String project, boolean automatic, SideSupport clientSide, @Nullable String projectId, @Nullable String versionId,
		@Nullable String versionNumber) {

	/** A file that is not a catalog version of its own, such as one a modpack lists. */
	public ContentFile(String url, String fileName, String sha512, @Nullable String sha1, long size, String directory,
			String project, boolean automatic, SideSupport clientSide) {
		this(url, fileName, sha512, sha1, size, directory, project, automatic, clientSide, null, null, null);
	}
}
