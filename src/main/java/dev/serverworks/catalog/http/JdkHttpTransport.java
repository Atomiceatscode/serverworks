package dev.serverworks.catalog.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** {@link HttpTransport} on the JDK's built-in client. Never follows redirects to other hosts. */
public final class JdkHttpTransport implements HttpTransport {
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

	private final HttpClient client;

	public JdkHttpTransport() {
		this.client = HttpClient.newBuilder()
				.connectTimeout(CONNECT_TIMEOUT)
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
	}

	@Override
	public CompletableFuture<Response> get(URI uri, Map<String, String> headers) {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).GET();
		headers.forEach(request::header);
		return client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
			Map<String, String> flat = new HashMap<>();
			response.headers().map().forEach((name, values) -> {
				if (!values.isEmpty()) {
					flat.put(name.toLowerCase(Locale.ROOT), values.getFirst());
				}
			});
			return new Response(response.statusCode(), response.body(), flat);
		});
	}
}
