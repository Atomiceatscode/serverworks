package dev.serverworks.catalog.model;

import java.util.List;

/**
 * The catalog's view of one project: enough to list it, draw it and write it to a disk.
 *
 * @param type         Modrinth's headline type. Projects published for several loaders report
 *                     {@code mod} even when they also ship as a plugin or datapack, so where a
 *                     file is installed must be decided from the chosen version's loaders
 * @param loaders      loader tags the project supports (fabric, paper, ...), without content categories
 * @param categories   content categories (optimization, adventure, ...), without loader tags
 * @param iconUrl      may be null when the project has no icon
 * @param color        packed RGB accent colour chosen by Modrinth, or -1 when absent
 */
public record ProjectSummary(
		String id,
		String slug,
		String title,
		String description,
		String author,
		ProjectType type,
		List<String> loaders,
		List<String> categories,
		List<String> gameVersions,
		long downloads,
		long follows,
		String iconUrl,
		SideSupport clientSide,
		SideSupport serverSide,
		int color) {

	public ProjectSummary {
		loaders = List.copyOf(loaders);
		categories = List.copyOf(categories);
		gameVersions = List.copyOf(gameVersions);
	}

	/** True when a player can join a server running this without installing it themselves. */
	public boolean serverSideOnly() {
		return clientSide != SideSupport.REQUIRED;
	}
}
