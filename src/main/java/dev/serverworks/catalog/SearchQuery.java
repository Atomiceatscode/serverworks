package dev.serverworks.catalog;

import dev.serverworks.catalog.model.ProjectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A catalog search. Empty lists mean "no restriction" for that filter.
 *
 * @param loaders        match projects supporting any of these loader tags
 * @param gameVersions   match projects supporting any of these Minecraft versions
 * @param categories     match projects in all of these content categories
 * @param serverCapable  when true, hide projects that cannot run on a server at all
 * @param author         only projects by this Modrinth username, or empty for anyone
 */
public record SearchQuery(
		String text,
		List<ProjectType> types,
		List<String> loaders,
		List<String> gameVersions,
		List<String> categories,
		boolean serverCapable,
		Sort sort,
		int offset,
		int limit,
		String author) {

	public static final int MAX_LIMIT = 100;
	/** Modrinth usernames: letters, digits, underscores and hyphens. */
	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_-]{1,39}");

	public SearchQuery {
		text = text == null ? "" : text.strip();
		author = author == null ? "" : author.strip();
		if (!author.isEmpty() && !USERNAME.matcher(author).matches()) {
			throw new IllegalArgumentException("Not a Modrinth username: " + author);
		}
		types = List.copyOf(types);
		loaders = List.copyOf(loaders);
		gameVersions = List.copyOf(gameVersions);
		categories = List.copyOf(categories);
		if (offset < 0) {
			throw new IllegalArgumentException("offset must not be negative");
		}
		if (limit < 1 || limit > MAX_LIMIT) {
			throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
		}
	}

	public static SearchQuery of(String text) {
		return new SearchQuery(text, List.of(), List.of(), List.of(), List.of(), false, Sort.RELEVANCE, 0, 20, "");
	}

	/**
	 * Reads what a player typed into the search box. A word starting with {@code @}, or
	 * {@code author:}, names an author; the other words are searched for as usual. For
	 * example {@code @jellysquid3 lithium}. Anything that is not a valid username stays text.
	 */
	public static SearchQuery parse(String input) {
		String author = "";
		List<String> words = new ArrayList<>();
		for (String word : (input == null ? "" : input).strip().split("\\s+")) {
			String name = word.startsWith("@") ? word.substring(1)
					: word.regionMatches(true, 0, "author:", 0, 7) ? word.substring(7) : null;
			if (name != null && author.isEmpty() && USERNAME.matcher(name).matches()) {
				author = name;
			} else if (!word.isEmpty()) {
				words.add(word);
			}
		}
		return of(String.join(" ", words)).withAuthor(author);
	}

	private static final Pattern COLLECTION = Pattern.compile(
			"(?:collection:|(?:https?://)?(?:www\\.)?modrinth\\.com/collection/)([A-Za-z0-9]{1,16})/?");

	/** The collection a player typed or pasted, as {@code collection:ID} or a modrinth.com link, if any. */
	public static java.util.Optional<String> collectionId(String input) {
		var matcher = COLLECTION.matcher(input == null ? "" : input.strip());
		return matcher.matches() ? java.util.Optional.of(matcher.group(1)) : java.util.Optional.empty();
	}

	public SearchQuery withAuthor(String value) {
		return new SearchQuery(text, types, loaders, gameVersions, categories, serverCapable, sort, offset, limit, value);
	}

	public SearchQuery withTypes(List<ProjectType> value) {
		return new SearchQuery(text, value, loaders, gameVersions, categories, serverCapable, sort, offset, limit, author);
	}

	public SearchQuery withLoaders(List<String> value) {
		return new SearchQuery(text, types, value, gameVersions, categories, serverCapable, sort, offset, limit, author);
	}

	public SearchQuery withGameVersions(List<String> value) {
		return new SearchQuery(text, types, loaders, value, categories, serverCapable, sort, offset, limit, author);
	}

	public SearchQuery withCategories(List<String> value) {
		return new SearchQuery(text, types, loaders, gameVersions, value, serverCapable, sort, offset, limit, author);
	}

	public SearchQuery withServerCapable(boolean value) {
		return new SearchQuery(text, types, loaders, gameVersions, categories, value, sort, offset, limit, author);
	}

	public SearchQuery withSort(Sort value) {
		return new SearchQuery(text, types, loaders, gameVersions, categories, serverCapable, value, offset, limit, author);
	}

	public SearchQuery withPage(int newOffset, int newLimit) {
		return new SearchQuery(text, types, loaders, gameVersions, categories, serverCapable, sort, newOffset, newLimit, author);
	}

	/**
	 * Modrinth facets: a JSON array of OR-groups that are ANDed together.
	 * Returns an empty string when there is nothing to filter on.
	 */
	public String facets() {
		List<List<String>> groups = new ArrayList<>();
		addGroup(groups, "project_type", types.stream().map(ProjectType::apiName).toList());
		addGroup(groups, "categories", loaders);
		addGroup(groups, "versions", gameVersions);
		for (String category : categories) {
			addGroup(groups, "categories", List.of(category));
		}
		if (serverCapable) {
			groups.add(List.of("server_side:required", "server_side:optional"));
		}
		if (!author.isEmpty()) {
			groups.add(List.of("author:" + author));
		}
		if (groups.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder("[");
		for (int g = 0; g < groups.size(); g++) {
			if (g > 0) {
				out.append(',');
			}
			out.append('[');
			List<String> group = groups.get(g);
			for (int i = 0; i < group.size(); i++) {
				if (i > 0) {
					out.append(',');
				}
				out.append('"').append(escape(group.get(i))).append('"');
			}
			out.append(']');
		}
		return out.append(']').toString();
	}

	private static void addGroup(List<List<String>> groups, String key, List<String> values) {
		if (values.isEmpty()) {
			return;
		}
		groups.add(values.stream().map(v -> key + ":" + v).toList());
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	public enum Sort {
		RELEVANCE,
		DOWNLOADS,
		FOLLOWS,
		NEWEST,
		UPDATED;

		public String apiName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}
}
