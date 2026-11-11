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
 */
public record ContentFile(String url, String fileName, String sha512, @Nullable String sha1, long size, String directory,
		String project, boolean automatic, SideSupport clientSide) {
}
