package dev.serverworks.catalog;

import dev.serverworks.catalog.http.HttpTransport;
import dev.serverworks.catalog.http.HttpTransport.Response;
import dev.serverworks.catalog.http.RateGate;
import dev.serverworks.catalog.http.TtlCache;
import dev.serverworks.catalog.model.ProjectSummary;
import dev.serverworks.catalog.model.ProjectVersion;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Asynchronous, read-only client for the Modrinth API v2.
 *
 * <p>Every call returns immediately with a future, so it is safe to use from the game
 * thread. Futures fail with {@link CatalogException}. Responses are cached briefly,
 * identical requests in flight are shared, and the published rate limit is respected.
 */
public final class ModrinthClient {
	public static final URI DEFAULT_BASE = URI.create("https://api.modrinth.com/v2/");

	private static final Pattern ID_OR_SLUG = Pattern.compile("[A-Za-z0-9!@$()`.+,_\"-]{1,64}");
	private static final int MAX_RATE_LIMIT_RETRIES = 2;
	private static final int CACHE_ENTRIES = 256;
	private static final long CACHE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(5);
	/** Modrinth accepts long id lists, but URLs have practical limits. */
	private static final int PROJECTS_PER_REQUEST = 100;

	private final URI base;
	private final HttpTransport transport;
	private final String userAgent;
	private final LongSupplier clock;
	private final Delayer delayer;
	private final RateGate gate = new RateGate();
	private final TtlCache<String> cache = new TtlCache<>(CACHE_ENTRIES, CACHE_TTL_MILLIS);
	private final Map<String, CompletableFuture<String>> inFlight = new ConcurrentHashMap<>();

	/**
	 * @param userAgent identifies this mod to Modrinth, as their API terms require,
	 *                  for example {@code serverworks/0.1.0}
	 */
	public ModrinthClient(HttpTransport transport, String userAgent) {
		this(DEFAULT_BASE, transport, userAgent, System::currentTimeMillis, Delayer.REAL);
	}

	public ModrinthClient(URI base, HttpTransport transport, String userAgent, LongSupplier clock, Delayer delayer) {
		if (userAgent == null || userAgent.isBlank()) {
			throw new IllegalArgumentException("A User-Agent is required by the Modrinth API");
		}
		this.base = base;
		this.transport = transport;
		this.userAgent = userAgent;
		this.clock = clock;
		this.delayer = delayer;
	}

	/**
	 * Searches the catalog. An author filter is first looked up, because Modrinth only
	 * matches the username's exact capitalisation and players will not always type it.
	 * Fails with {@link CatalogException.Kind#NOT_FOUND} when no such user exists.
	 */
	public CompletableFuture<SearchResult> search(SearchQuery query) {
		if (query.author().isEmpty()) {
			return searchExactly(query);
		}
		return get("user/" + pathSegment(query.author()), Map.of())
				.thenApply(ModrinthJson::username)
				.thenCompose(username -> searchExactly(query.withAuthor(username)));
	}

	private CompletableFuture<SearchResult> searchExactly(SearchQuery query) {
		Map<String, String> params = new LinkedHashMap<>();
		if (!query.text().isEmpty()) {
			params.put("query", query.text());
		}
		String facets = query.facets();
		if (!facets.isEmpty()) {
			params.put("facets", facets);
		}
		params.put("index", query.sort().apiName());
		params.put("offset", Integer.toString(query.offset()));
		params.put("limit", Integer.toString(query.limit()));
		return get("search", params).thenApply(ModrinthJson::searchResult);
	}

	/** The projects in a public Modrinth collection, in the collection's order. */
	public CompletableFuture<List<ProjectSummary>> collection(String collectionId) {
		// Collections are only in version 3 of the API, next to version 2 on the same host.
		return get("../v3/collection/" + pathSegment(collectionId), Map.of())
				.thenApply(ModrinthJson::collectionProjects)
				.thenCompose(ids -> projects(ids).thenApply(found -> {
					Map<String, ProjectSummary> byId = new LinkedHashMap<>();
					found.forEach(project -> byId.put(project.id(), project));
					return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
				}));
	}

	public CompletableFuture<ProjectSummary> project(String idOrSlug) {
		return get("project/" + pathSegment(idOrSlug), Map.of()).thenApply(ModrinthJson::project);
	}

	/** Looks up many projects by id. Unknown ids are simply missing from the result. */
	public CompletableFuture<List<ProjectSummary>> projects(Collection<String> ids) {
		List<String> distinct = ids.stream().distinct().toList();
		if (distinct.isEmpty()) {
			return CompletableFuture.completedFuture(List.of());
		}
		List<CompletableFuture<List<ProjectSummary>>> pages = new ArrayList<>();
		for (int from = 0; from < distinct.size(); from += PROJECTS_PER_REQUEST) {
			List<String> page = distinct.subList(from, Math.min(distinct.size(), from + PROJECTS_PER_REQUEST));
			pages.add(get("projects", Map.of("ids", jsonArray(page))).thenApply(ModrinthJson::projects));
		}
		return CompletableFuture.allOf(pages.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
			List<ProjectSummary> all = new ArrayList<>();
			pages.forEach(page -> all.addAll(page.join()));
			return all;
		});
	}

	/**
	 * Versions of a project, newest first, optionally narrowed to loaders and Minecraft versions.
	 * Empty filters mean "any".
	 */
	public CompletableFuture<List<ProjectVersion>> versions(String projectIdOrSlug, Collection<String> loaders,
			Collection<String> gameVersions) {
		Map<String, String> params = new LinkedHashMap<>();
		if (!loaders.isEmpty()) {
			params.put("loaders", jsonArray(loaders));
		}
		if (!gameVersions.isEmpty()) {
			params.put("game_versions", jsonArray(gameVersions));
		}
		return get("project/" + pathSegment(projectIdOrSlug) + "/version", params).thenApply(ModrinthJson::versions);
	}

	public CompletableFuture<ProjectVersion> version(String versionId) {
		return get("version/" + pathSegment(versionId), Map.of()).thenApply(ModrinthJson::version);
	}

	private CompletableFuture<String> get(String path, Map<String, String> params) {
		URI uri = uri(path, params);
		String key = uri.toString();
		String cached = cache.get(key, clock.getAsLong());
		if (cached != null) {
			return CompletableFuture.completedFuture(cached);
		}
		// computeIfAbsent shares one request between identical concurrent callers.
		CompletableFuture<String> shared = inFlight.computeIfAbsent(key, k -> send(uri, 0));
		shared.whenComplete((body, error) -> inFlight.remove(key, shared));
		// Hand each caller its own future so one cancelling cannot cancel the others.
		return shared.thenApply(body -> body);
	}

	private CompletableFuture<String> send(URI uri, int attempt) {
		long wait = gate.delayMillis(clock.getAsLong());
		if (wait > 0) {
			return delayer.after(wait, () -> send(uri, attempt));
		}
		Map<String, String> headers = Map.of("User-Agent", userAgent, "Accept", "application/json");
		return transport.get(uri, headers)
				.exceptionallyCompose(error -> CompletableFuture.failedFuture(new CatalogException(
						CatalogException.Kind.NETWORK, "Could not reach the catalog", unwrap(error))))
				.thenCompose(response -> handle(uri, attempt, response));
	}

	private CompletableFuture<String> handle(URI uri, int attempt, Response response) {
		long now = clock.getAsLong();
		int status = response.status();
		if (status == 429) {
			long wait = gate.onRejected(response.headers(), now);
			if (attempt >= MAX_RATE_LIMIT_RETRIES) {
				return CompletableFuture.failedFuture(new CatalogException(CatalogException.Kind.RATE_LIMITED,
						"The catalog is rate limiting requests; try again in a moment"));
			}
			return delayer.after(wait, () -> send(uri, attempt + 1));
		}
		gate.onResponse(response.headers(), now);
		if (status == 404) {
			return CompletableFuture.failedFuture(
					new CatalogException(CatalogException.Kind.NOT_FOUND, "Not found in the catalog"));
		}
		if (status < 200 || status >= 300) {
			return CompletableFuture.failedFuture(new CatalogException(CatalogException.Kind.BAD_RESPONSE,
					"The catalog answered with HTTP " + status));
		}
		String body = response.body() == null ? "" : response.body();
		cache.put(uri.toString(), body, now);
		return CompletableFuture.completedFuture(body);
	}

	private URI uri(String path, Map<String, String> params) {
		StringBuilder out = new StringBuilder(path);
		char separator = '?';
		for (Map.Entry<String, String> param : params.entrySet()) {
			out.append(separator)
					.append(URLEncoder.encode(param.getKey(), StandardCharsets.UTF_8))
					.append('=')
					.append(URLEncoder.encode(param.getValue(), StandardCharsets.UTF_8));
			separator = '&';
		}
		URI uri = base.resolve(out.toString());
		// Defence in depth: nothing built here may ever point away from the configured catalog.
		if (!base.getScheme().equals(uri.getScheme()) || !base.getHost().equals(uri.getHost())
				|| base.getPort() != uri.getPort()) {
			throw new CatalogException(CatalogException.Kind.BLOCKED, "Refusing a request outside the catalog");
		}
		return uri;
	}

	/** Ids and slugs come from disks and packets, so they are validated before entering a URL. */
	private static String pathSegment(String idOrSlug) {
		if (idOrSlug == null || !ID_OR_SLUG.matcher(idOrSlug).matches() || idOrSlug.contains("..")) {
			throw new CatalogException(CatalogException.Kind.BLOCKED, "Not a valid project or version id");
		}
		return URLEncoder.encode(idOrSlug, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static String jsonArray(Collection<String> values) {
		StringBuilder out = new StringBuilder("[");
		boolean first = true;
		for (String value : values) {
			if (!first) {
				out.append(',');
			}
			first = false;
			out.append('"').append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
		}
		return out.append(']').toString();
	}

	private static Throwable unwrap(Throwable error) {
		return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
	}

	/** Runs a task later. Replaced in tests so that waits take no real time. */
	@FunctionalInterface
	public interface Delayer {
		Delayer REAL = new Delayer() {
			@Override
			public <T> CompletableFuture<T> after(long millis, Supplier<CompletableFuture<T>> task) {
				return CompletableFuture
						.supplyAsync(task, CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS))
						.thenCompose(future -> future);
			}
		};

		<T> CompletableFuture<T> after(long millis, Supplier<CompletableFuture<T>> task);
	}
}
