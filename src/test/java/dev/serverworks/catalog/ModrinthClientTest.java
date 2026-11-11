package dev.serverworks.catalog;

import dev.serverworks.catalog.http.HttpTransport;
import dev.serverworks.catalog.model.ProjectSummary;
import dev.serverworks.catalog.model.ProjectType;
import dev.serverworks.catalog.model.ProjectVersion;
import dev.serverworks.catalog.model.SideSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModrinthClientTest {
	private static final Map<String, String> PLENTY = Map.of("x-ratelimit-remaining", "299", "x-ratelimit-reset", "60");

	@Test
	void searchSendsFacetsAndParsesHits() {
		FakeTransport transport = new FakeTransport().respond(200, fixture("search_lithium.json"), PLENTY);
		ModrinthClient client = client(transport);

		SearchResult result = client.search(SearchQuery.of("lithium")
				.withTypes(List.of(ProjectType.MOD))
				.withLoaders(List.of("fabric", "quilt"))
				.withGameVersions(List.of("26.3"))
				.withServerCapable(true)
				.withSort(SearchQuery.Sort.DOWNLOADS)
				.withPage(0, 3)).join();

		String sent = URLDecoder.decode(transport.requests.getFirst().toString(), StandardCharsets.UTF_8);
		assertTrue(sent.startsWith("https://api.modrinth.com/v2/search?query=lithium"), sent);
		assertTrue(sent.contains("facets=[[\"project_type:mod\"],[\"categories:fabric\",\"categories:quilt\"],"
				+ "[\"versions:26.3\"],[\"server_side:required\",\"server_side:optional\"]]"), sent);
		assertTrue(sent.contains("index=downloads&offset=0&limit=3"), sent);
		assertEquals("serverworks-test/1.0", transport.headers.getFirst().get("User-Agent"));

		ProjectSummary lithium = result.hits().getFirst();
		assertEquals("gvQqBUqZ", lithium.id());
		assertEquals("lithium", lithium.slug());
		assertEquals("Lithium", lithium.title());
		assertEquals(ProjectType.MOD, lithium.type());
		assertTrue(lithium.loaders().containsAll(List.of("fabric", "neoforge", "quilt")), lithium.loaders().toString());
		assertEquals(List.of("optimization"), lithium.categories());
		assertTrue(lithium.gameVersions().contains("26.3"));
		assertEquals(SideSupport.OPTIONAL, lithium.clientSide());
		assertTrue(lithium.serverSideOnly());
		assertTrue(lithium.iconUrl().startsWith("https://cdn.modrinth.com/"));
		assertTrue(lithium.downloads() > 1_000_000);
		assertEquals(result.totalHits() > result.hits().size(), result.hasMore());
	}

	@Test
	void projectEndpointUsesItsOwnFieldNames() {
		FakeTransport transport = new FakeTransport().respond(200, fixture("project_lithium.json"), PLENTY);

		ProjectSummary lithium = client(transport).project("lithium").join();

		assertEquals("https://api.modrinth.com/v2/project/lithium", transport.requests.getFirst().toString());
		assertEquals("gvQqBUqZ", lithium.id());
		assertEquals("Lithium", lithium.title());
		assertTrue(lithium.loaders().contains("fabric"));
		assertFalse(lithium.categories().contains("fabric"));
		assertFalse(lithium.gameVersions().isEmpty());
		assertTrue(lithium.follows() > 0);
	}

	@Test
	void versionsCarryVerifiableFiles() {
		FakeTransport transport = new FakeTransport().respond(200, fixture("versions_lithium.json"), PLENTY);

		List<ProjectVersion> versions = client(transport).versions("lithium", List.of("fabric"), List.of("26.3")).join();

		String sent = URLDecoder.decode(transport.requests.getFirst().toString(), StandardCharsets.UTF_8);
		assertEquals("https://api.modrinth.com/v2/project/lithium/version?loaders=[\"fabric\"]&game_versions=[\"26.3\"]", sent);
		ProjectVersion newest = versions.getFirst();
		assertEquals("gvQqBUqZ", newest.projectId());
		assertEquals(ProjectVersion.Channel.RELEASE, newest.channel());
		ProjectVersion.VersionFile file = newest.primaryFile().orElseThrow();
		assertTrue(file.primary());
		assertEquals(128, file.sha512().length());
		assertTrue(file.url().startsWith("https://cdn.modrinth.com/"));
		assertTrue(file.filename().endsWith(".jar"));
		assertTrue(file.size() > 0);
	}

	@Test
	void filesWithoutAHashAreDropped() {
		String json = """
				[{"id":"v1","project_id":"p1","files":[
				  {"url":"https://cdn.modrinth.com/a.jar","filename":"a.jar","hashes":{"sha1":"abc"},"primary":true},
				  {"url":"https://cdn.modrinth.com/b.jar","filename":"b.jar","hashes":{"sha512":"AB12"}}],
				 "dependencies":[{"project_id":"dep","version_id":null,"dependency_type":"required"},
				                 {"project_id":null,"version_id":null,"dependency_type":"optional"}]}]""";

		ProjectVersion version = ModrinthJson.versions(json).getFirst();

		assertEquals(1, version.files().size());
		assertEquals("b.jar", version.primaryFile().orElseThrow().filename());
		assertEquals("ab12", version.files().getFirst().sha512());
		assertEquals(1, version.dependencies().size());
		assertEquals(ProjectVersion.Dependency.Kind.REQUIRED, version.dependencies().getFirst().kind());
	}

	@Test
	void repeatedRequestsAreServedFromCacheUntilItExpires() {
		FakeTransport transport = new FakeTransport()
				.respond(200, fixture("project_lithium.json"), PLENTY)
				.respond(200, fixture("project_lithium.json"), PLENTY);
		long[] now = {1_000};
		ModrinthClient client = new ModrinthClient(ModrinthClient.DEFAULT_BASE, transport, "serverworks-test/1.0",
				() -> now[0], new RecordingDelayer());

		client.project("lithium").join();
		client.project("lithium").join();
		assertEquals(1, transport.requests.size());

		now[0] += 6 * 60 * 1000;
		client.project("lithium").join();
		assertEquals(2, transport.requests.size());
	}

	@Test
	void identicalRequestsInFlightShareOneCall() {
		FakeTransport transport = new FakeTransport();
		CompletableFuture<HttpTransport.Response> pending = new CompletableFuture<>();
		transport.queue.add(pending);
		ModrinthClient client = client(transport);

		CompletableFuture<ProjectSummary> first = client.project("lithium");
		CompletableFuture<ProjectSummary> second = client.project("lithium");
		pending.complete(new HttpTransport.Response(200, fixture("project_lithium.json"), PLENTY));

		assertEquals("Lithium", first.join().title());
		assertEquals("Lithium", second.join().title());
		assertEquals(1, transport.requests.size());
	}

	@Test
	void rateLimitRejectionWaitsForResetThenRetries() {
		FakeTransport transport = new FakeTransport()
				.respond(429, "", Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", "7"))
				.respond(200, fixture("project_lithium.json"), PLENTY);
		RecordingDelayer delayer = new RecordingDelayer();
		long[] now = {0};
		delayer.onDelay = millis -> now[0] += millis;
		ModrinthClient client = new ModrinthClient(ModrinthClient.DEFAULT_BASE, transport, "serverworks-test/1.0",
				() -> now[0], delayer);

		assertEquals("Lithium", client.project("lithium").join().title());
		assertEquals(List.of(7_000L), delayer.delays);
		assertEquals(2, transport.requests.size());
	}

	@Test
	void lowRemainingBudgetHoldsTheNextRequest() {
		FakeTransport transport = new FakeTransport()
				.respond(200, fixture("project_lithium.json"), Map.of("x-ratelimit-remaining", "3", "x-ratelimit-reset", "12"))
				.respond(200, fixture("versions_lithium.json"), PLENTY);
		RecordingDelayer delayer = new RecordingDelayer();
		long[] now = {0};
		delayer.onDelay = millis -> now[0] += millis;
		ModrinthClient client = new ModrinthClient(ModrinthClient.DEFAULT_BASE, transport, "serverworks-test/1.0",
				() -> now[0], delayer);

		client.project("lithium").join();
		assertTrue(delayer.delays.isEmpty());
		client.versions("lithium", List.of(), List.of()).join();
		assertEquals(List.of(12_000L), delayer.delays);
	}

	@Test
	void persistentRateLimitingGivesUp() {
		Map<String, String> limited = Map.of("x-ratelimit-reset", "1");
		FakeTransport transport = new FakeTransport().respond(429, "", limited).respond(429, "", limited).respond(429, "", limited);
		long[] now = {0};
		RecordingDelayer delayer = new RecordingDelayer();
		delayer.onDelay = millis -> now[0] += millis;
		ModrinthClient client = new ModrinthClient(ModrinthClient.DEFAULT_BASE, transport, "serverworks-test/1.0",
				() -> now[0], delayer);

		assertEquals(CatalogException.Kind.RATE_LIMITED, failure(client.project("lithium")).kind());
		assertEquals(3, transport.requests.size());
	}

	@Test
	void errorsAreClassified() {
		assertEquals(CatalogException.Kind.NOT_FOUND,
				failure(client(new FakeTransport().respond(404, "", PLENTY)).project("nope")).kind());
		assertEquals(CatalogException.Kind.BAD_RESPONSE,
				failure(client(new FakeTransport().respond(503, "", PLENTY)).project("lithium")).kind());
		assertEquals(CatalogException.Kind.BAD_RESPONSE,
				failure(client(new FakeTransport().respond(200, "<html>", PLENTY)).project("lithium")).kind());

		FakeTransport offline = new FakeTransport();
		offline.queue.add(CompletableFuture.failedFuture(new IOException("no route")));
		assertEquals(CatalogException.Kind.NETWORK, failure(client(offline).project("lithium")).kind());
	}

	@Test
	void hostileIdsNeverReachTheNetwork() {
		FakeTransport transport = new FakeTransport();
		ModrinthClient client = client(transport);

		for (String bad : List.of("../user/me", "a/b", "x?y=1", "http://evil.example", "", "a b", "..")) {
			CatalogException error = assertThrows(CatalogException.class, () -> client.project(bad), bad);
			assertEquals(CatalogException.Kind.BLOCKED, error.kind());
		}
		assertTrue(transport.requests.isEmpty());
	}

	@Test
	void projectsDeduplicatesAndSkipsTheNetworkWhenEmpty() {
		FakeTransport transport = new FakeTransport().respond(200, "[" + fixture("project_lithium.json") + "]", PLENTY);
		ModrinthClient client = client(transport);

		assertTrue(client.projects(List.of()).join().isEmpty());
		assertTrue(transport.requests.isEmpty());

		List<ProjectSummary> found = client.projects(List.of("gvQqBUqZ", "gvQqBUqZ")).join();
		assertEquals(1, found.size());
		String sent = URLDecoder.decode(transport.requests.getFirst().toString(), StandardCharsets.UTF_8);
		assertEquals("https://api.modrinth.com/v2/projects?ids=[\"gvQqBUqZ\"]", sent);
	}

	@Test
	void facetsAreEmptyWithoutFilters() {
		assertEquals("", SearchQuery.of("anything").facets());
		assertEquals("[[\"categories:adventure\"],[\"categories:magic\"]]",
				SearchQuery.of("").withCategories(List.of("adventure", "magic")).facets());
		assertThrows(IllegalArgumentException.class, () -> SearchQuery.of("x").withPage(0, 101));
	}

	@Test
	void typedAuthorsAreParsedOutOfTheSearchText() {
		SearchQuery query = SearchQuery.parse("@JellySquid3 lithium");
		assertEquals("JellySquid3", query.author());
		assertEquals("lithium", query.text());
		assertEquals("jellysquid3", SearchQuery.parse("author:jellysquid3").author());
		assertEquals("", SearchQuery.parse("author:jellysquid3").text());
		// Not a username, so it stays ordinary text.
		SearchQuery odd = SearchQuery.parse("@a/b sodium");
		assertEquals("", odd.author());
		assertEquals("@a/b sodium", odd.text());
		assertEquals("", SearchQuery.parse("plain words").author());
		assertTrue(SearchQuery.of("x").withAuthor("Someone").facets().contains("[\"author:Someone\"]"));
		assertThrows(IllegalArgumentException.class, () -> SearchQuery.of("x").withAuthor("bad name"));
	}

	@Test
	void authorSearchesUseTheUsernamesExactCapitalisation() {
		FakeTransport transport = new FakeTransport()
				.respond(200, "{\"id\":\"TEZXhE2U\",\"username\":\"jellysquid3\"}", PLENTY)
				.respond(200, fixture("search_lithium.json"), PLENTY);
		SearchResult result = client(transport).search(SearchQuery.parse("@JellySquid3")).join();
		assertEquals("https://api.modrinth.com/v2/user/JellySquid3", transport.requests.getFirst().toString());
		String sent = URLDecoder.decode(transport.requests.get(1).toString(), StandardCharsets.UTF_8);
		assertTrue(sent.contains("[\"author:jellysquid3\"]"), sent);
		assertFalse(result.hits().isEmpty());
	}

	@Test
	void unknownAuthorsFailAsNotFound() {
		FakeTransport transport = new FakeTransport().respond(404, "", PLENTY);
		assertEquals(CatalogException.Kind.NOT_FOUND, failure(client(transport).search(SearchQuery.parse("@nobody_here"))).kind());
		assertEquals(1, transport.requests.size(), "no search is sent for an author that does not exist");
	}

	@Test
	void collectionsAreReadFromTextOrLinks() {
		assertEquals(java.util.Optional.of("AbC123"), SearchQuery.collectionId("collection:AbC123"));
		assertEquals(java.util.Optional.of("AbC123"), SearchQuery.collectionId("https://modrinth.com/collection/AbC123"));
		assertEquals(java.util.Optional.of("AbC123"), SearchQuery.collectionId("modrinth.com/collection/AbC123/"));
		assertTrue(SearchQuery.collectionId("lithium").isEmpty());
		assertTrue(SearchQuery.collectionId("https://evil.example/collection/AbC123").isEmpty());
	}

	@Test
	void collectionsListTheirProjectsInOrder() {
		FakeTransport transport = new FakeTransport()
				.respond(200, "{\"id\":\"AbC123\",\"projects\":[\"gvQqBUqZ\"]}", PLENTY)
				.respond(200, "[" + fixture("project_lithium.json") + "]", PLENTY);
		List<ProjectSummary> projects = client(transport).collection("AbC123").join();
		assertEquals("https://api.modrinth.com/v3/collection/AbC123", transport.requests.getFirst().toString());
		assertEquals(List.of("gvQqBUqZ"), projects.stream().map(ProjectSummary::id).toList());
	}

	private static ModrinthClient client(FakeTransport transport) {
		return new ModrinthClient(ModrinthClient.DEFAULT_BASE, transport, "serverworks-test/1.0", () -> 0L,
				new RecordingDelayer());
	}

	private static CatalogException failure(CompletableFuture<?> future) {
		CompletionException wrapper = assertThrows(CompletionException.class, future::join);
		return assertInstanceOf(CatalogException.class, wrapper.getCause());
	}

	private static String fixture(String name) {
		try (InputStream in = ModrinthClientTest.class.getResourceAsStream("/fixtures/" + name)) {
			if (in == null) {
				throw new IllegalStateException("Missing fixture " + name);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static final class FakeTransport implements HttpTransport {
		final List<URI> requests = new ArrayList<>();
		final List<Map<String, String>> headers = new ArrayList<>();
		final Deque<CompletableFuture<Response>> queue = new ArrayDeque<>();

		FakeTransport respond(int status, String body, Map<String, String> responseHeaders) {
			queue.add(CompletableFuture.completedFuture(new Response(status, body, responseHeaders)));
			return this;
		}

		@Override
		public CompletableFuture<Response> get(URI uri, Map<String, String> requestHeaders) {
			requests.add(uri);
			headers.add(requestHeaders);
			CompletableFuture<Response> next = queue.poll();
			if (next == null) {
				throw new AssertionError("Unexpected request: " + uri);
			}
			return next;
		}
	}

	/** Runs delayed work immediately and remembers how long each wait would have been. */
	private static final class RecordingDelayer implements ModrinthClient.Delayer {
		final List<Long> delays = new ArrayList<>();
		java.util.function.LongConsumer onDelay = millis -> { };

		@Override
		public <T> CompletableFuture<T> after(long millis, Supplier<CompletableFuture<T>> task) {
			delays.add(millis);
			onDelay.accept(millis);
			return task.get();
		}
	}
}
