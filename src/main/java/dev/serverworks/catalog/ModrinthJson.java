package dev.serverworks.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.serverworks.catalog.model.ProjectSummary;
import dev.serverworks.catalog.model.ProjectType;
import dev.serverworks.catalog.model.ProjectVersion;
import dev.serverworks.catalog.model.ProjectVersion.Channel;
import dev.serverworks.catalog.model.ProjectVersion.Dependency;
import dev.serverworks.catalog.model.ProjectVersion.VersionFile;
import dev.serverworks.catalog.model.SideSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns Modrinth API v2 JSON into catalog records.
 *
 * <p>Parsing is lenient about missing or null fields, because the search and project
 * endpoints name the same things differently and optional fields come and go.
 */
public final class ModrinthJson {
	/**
	 * Loader tags Modrinth mixes into {@code categories}. Kept as a fixed set so that a
	 * search hit can be split into "runs on" and "is about" without an extra request.
	 */
	public static final Set<String> LOADER_TAGS = Set.of(
			"babric", "bta-babric", "bukkit", "bungeecord", "canvas", "datapack", "fabric", "folia",
			"forge", "geyser", "iris", "java-agent", "legacy-fabric", "liteloader", "minecraft",
			"modloader", "neoforge", "nilloader", "optifine", "ornithe", "paper", "purpur", "quilt",
			"rift", "spigot", "sponge", "vanilla", "velocity", "waterfall");

	private ModrinthJson() {
	}

	public static SearchResult searchResult(String json) {
		JsonObject root = object(json);
		List<ProjectSummary> hits = new ArrayList<>();
		for (JsonElement hit : array(root, "hits")) {
			if (hit.isJsonObject()) {
				hits.add(summary(hit.getAsJsonObject()));
			}
		}
		return new SearchResult(hits, intOr(root, "offset", 0), intOr(root, "limit", hits.size()),
				intOr(root, "total_hits", hits.size()));
	}

	public static ProjectSummary project(String json) {
		return summary(object(json));
	}

	public static List<ProjectSummary> projects(String json) {
		List<ProjectSummary> out = new ArrayList<>();
		for (JsonElement element : arrayRoot(json)) {
			if (element.isJsonObject()) {
				out.add(summary(element.getAsJsonObject()));
			}
		}
		return out;
	}

	public static ProjectVersion version(String json) {
		return version(object(json));
	}

	public static List<ProjectVersion> versions(String json) {
		List<ProjectVersion> out = new ArrayList<>();
		for (JsonElement element : arrayRoot(json)) {
			if (element.isJsonObject()) {
				out.add(version(element.getAsJsonObject()));
			}
		}
		return out;
	}

	/** Handles both a search hit ({@code project_id}, {@code versions}) and a full project ({@code id}, {@code game_versions}). */
	private static ProjectSummary summary(JsonObject o) {
		String id = firstString(o, "project_id", "id");
		if (id == null || id.isEmpty()) {
			throw new CatalogException(CatalogException.Kind.BAD_RESPONSE, "Project without an id");
		}
		List<String> mixed = strings(o, "categories");
		List<String> loaders = new ArrayList<>(strings(o, "loaders"));
		List<String> categories = new ArrayList<>();
		for (String tag : mixed) {
			if (LOADER_TAGS.contains(tag)) {
				if (!loaders.contains(tag)) {
					loaders.add(tag);
				}
			} else {
				categories.add(tag);
			}
		}
		List<String> gameVersions = o.has("game_versions") ? strings(o, "game_versions") : strings(o, "versions");
		String title = firstString(o, "title", "name");
		String slug = stringOr(o, "slug", id);
		return new ProjectSummary(
				id,
				slug,
				title == null ? slug : title,
				stringOr(o, "description", ""),
				stringOr(o, "author", ""),
				ProjectType.fromApi(stringOr(o, "project_type", null)),
				loaders,
				categories,
				gameVersions,
				longOr(o, "downloads", 0),
				longOr(o, o.has("follows") ? "follows" : "followers", 0),
				stringOr(o, "icon_url", null),
				SideSupport.fromApi(stringOr(o, "client_side", null)),
				SideSupport.fromApi(stringOr(o, "server_side", null)),
				intOr(o, "color", -1));
	}

	private static ProjectVersion version(JsonObject o) {
		String id = stringOr(o, "id", null);
		String projectId = stringOr(o, "project_id", null);
		if (id == null || projectId == null) {
			throw new CatalogException(CatalogException.Kind.BAD_RESPONSE, "Version without an id or project");
		}
		List<VersionFile> files = new ArrayList<>();
		for (JsonElement element : array(o, "files")) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject f = element.getAsJsonObject();
			JsonObject hashes = f.has("hashes") && f.get("hashes").isJsonObject() ? f.getAsJsonObject("hashes") : new JsonObject();
			String url = stringOr(f, "url", null);
			String sha512 = stringOr(hashes, "sha512", null);
			if (url == null || sha512 == null) {
				// Unverifiable files are never offered for download.
				continue;
			}
			files.add(new VersionFile(url, stringOr(f, "filename", ""), sha512.toLowerCase(Locale.ROOT),
					stringOr(hashes, "sha1", null), longOr(f, "size", -1), boolOr(f, "primary", false)));
		}
		List<Dependency> dependencies = new ArrayList<>();
		for (JsonElement element : array(o, "dependencies")) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject d = element.getAsJsonObject();
			String depProject = stringOr(d, "project_id", null);
			String depVersion = stringOr(d, "version_id", null);
			if (depProject == null && depVersion == null) {
				continue;
			}
			dependencies.add(new Dependency(depProject, depVersion,
					Dependency.Kind.fromApi(stringOr(d, "dependency_type", null))));
		}
		return new ProjectVersion(
				id,
				projectId,
				stringOr(o, "name", ""),
				stringOr(o, "version_number", ""),
				Channel.fromApi(stringOr(o, "version_type", null)),
				strings(o, "loaders"),
				strings(o, "game_versions"),
				stringOr(o, "date_published", ""),
				files,
				dependencies);
	}

	private static JsonObject object(String json) {
		JsonElement element = parse(json);
		if (!element.isJsonObject()) {
			throw new CatalogException(CatalogException.Kind.BAD_RESPONSE, "Expected a JSON object");
		}
		return element.getAsJsonObject();
	}

	private static JsonArray arrayRoot(String json) {
		JsonElement element = parse(json);
		if (!element.isJsonArray()) {
			throw new CatalogException(CatalogException.Kind.BAD_RESPONSE, "Expected a JSON array");
		}
		return element.getAsJsonArray();
	}

	private static JsonElement parse(String json) {
		try {
			return JsonParser.parseString(json);
		} catch (JsonParseException e) {
			throw new CatalogException(CatalogException.Kind.BAD_RESPONSE, "Catalog sent unreadable JSON", e);
		}
	}

	private static JsonArray array(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
	}

	private static List<String> strings(JsonObject o, String key) {
		List<String> out = new ArrayList<>();
		for (JsonElement e : array(o, key)) {
			if (e.isJsonPrimitive()) {
				out.add(e.getAsString());
			}
		}
		return out;
	}

	private static String firstString(JsonObject o, String... keys) {
		for (String key : keys) {
			String value = stringOr(o, key, null);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private static String stringOr(JsonObject o, String key, String fallback) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
	}

	private static long longOr(JsonObject o, String key, long fallback) {
		JsonElement e = o.get(key);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsLong() : fallback;
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	private static int intOr(JsonObject o, String key, int fallback) {
		return (int) longOr(o, key, fallback);
	}

	private static boolean boolOr(JsonObject o, String key, boolean fallback) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : fallback;
	}
}
