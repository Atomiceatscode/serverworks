package dev.serverworks.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.serverworks.Serverworks;
import dev.serverworks.catalog.model.ProjectSummary;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Projects starred in the Disk Writer, kept on this computer in {@code config/serverworks-favourites.json}. */
public final class Favourites {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int MAX = 500;
	private static List<ProjectSummary> projects;

	private Favourites() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(Serverworks.MOD_ID + "-favourites.json");
	}

	private static synchronized List<ProjectSummary> projects() {
		if (projects == null) {
			projects = new ArrayList<>();
			Path file = file();
			if (Files.isRegularFile(file)) {
				try {
					List<ProjectSummary> read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
							new TypeToken<List<ProjectSummary>>() { }.getType());
					if (read != null) {
						read.stream().filter(p -> p != null && p.id() != null && p.title() != null).limit(MAX).forEach(projects::add);
					}
				} catch (IOException | RuntimeException e) {
					Serverworks.LOGGER.warn("Could not read the Disk Writer favourites", e);
				}
			}
		}
		return projects;
	}

	public static synchronized boolean contains(String projectId) {
		return projects().stream().anyMatch(p -> p.id().equals(projectId));
	}

	/** Stars or unstars a project. Returns true when it is now a favourite. */
	public static synchronized boolean toggle(ProjectSummary project) {
		List<ProjectSummary> all = projects();
		boolean removed = all.removeIf(p -> p.id().equals(project.id()));
		if (!removed && all.size() < MAX) {
			all.add(0, project);
		}
		save();
		return !removed;
	}

	/** Favourites whose title or author contains the text, most recently starred first. */
	public static synchronized List<ProjectSummary> matching(String text) {
		String needle = text.strip().toLowerCase(Locale.ROOT);
		return projects().stream().filter(p -> needle.isEmpty() || p.title().toLowerCase(Locale.ROOT).contains(needle)
				|| (p.author() != null && p.author().toLowerCase(Locale.ROOT).contains(needle))).toList();
	}

	private static void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(projects), StandardCharsets.UTF_8);
		} catch (IOException e) {
			Serverworks.LOGGER.warn("Could not save the Disk Writer favourites", e);
		}
	}
}
