package dev.serverworks.catalog.http;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** The one seam between the catalog and the network, so tests can answer requests themselves. */
public interface HttpTransport {
	/**
	 * Sends a GET. The future fails only when no HTTP response arrived at all;
	 * error statuses come back as ordinary responses.
	 */
	CompletableFuture<Response> get(URI uri, Map<String, String> headers);

	/** @param headers response headers with lower-case names; the first value of each */
	record Response(int status, String body, Map<String, String> headers) {
		public Response {
			headers = Map.copyOf(headers);
		}
	}
}
