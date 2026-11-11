package dev.serverworks.host;

import dev.serverworks.host.ServerInstance.Failure;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.function.LongConsumer;

/** {@link Fetcher} over HTTPS. Only allowlisted hosts, and redirects are never followed. */
public final class HttpFetcher implements Fetcher {
	private static final long MAX_TEXT_BYTES = 16L * 1024 * 1024;

	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();
	private final String userAgent;

	public HttpFetcher(String userAgent) {
		this.userAgent = userAgent;
	}

	@Override
	public String text(URI uri) throws PrepareException {
		HttpResponse<byte[]> response = send(uri,
				HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), MAX_TEXT_BYTES), Duration.ofSeconds(30));
		return new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
	}

	@Override
	public void download(URI uri, Path target, @Nullable Checksum expected, long maxBytes, LongConsumer progress)
			throws PrepareException {
		Path partial = target.resolveSibling(target.getFileName() + ".part");
		try {
			Files.createDirectories(target.getParent());
			HttpResponse<InputStream> response = send(uri, HttpResponse.BodyHandlers.ofInputStream(), Duration.ofMinutes(15));
			MessageDigest digest = expected == null ? null : expected.newDigest();
			long total = 0;
			byte[] buffer = new byte[1 << 16];
			try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(partial)) {
				int read;
				while ((read = in.read(buffer)) > 0) {
					total += read;
					if (total > maxBytes) {
						throw new PrepareException(Failure.DOWNLOAD, "Download is larger than allowed: " + uri.getPath());
					}
					out.write(buffer, 0, read);
					if (digest != null) {
						digest.update(buffer, 0, read);
					}
					progress.accept(read);
				}
			}
			if (expected != null && !expected.matches(digest)) {
				throw new PrepareException(Failure.DOWNLOAD, "Downloaded file failed its integrity check: " + uri.getPath());
			}
			Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new PrepareException(Failure.DOWNLOAD, "Download failed: " + uri.getHost(), e);
		} finally {
			try {
				Files.deleteIfExists(partial);
			} catch (IOException ignored) {
				// A leftover partial file is harmless; it is overwritten next time.
			}
		}
	}

	private <T> HttpResponse<T> send(URI uri, HttpResponse.BodyHandler<T> handler, Duration timeout) throws PrepareException {
		if (!HostAllowlist.allows(uri)) {
			throw new PrepareException(Failure.DOWNLOAD, "Refusing to contact " + uri.getHost());
		}
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).header("User-Agent", userAgent).GET().build();
		try {
			HttpResponse<T> response = client.send(request, handler);
			if (response.statusCode() != 200) {
				if (response.body() instanceof InputStream stream) {
					stream.close();
				}
				throw new PrepareException(Failure.DOWNLOAD, uri.getHost() + " answered with HTTP " + response.statusCode());
			}
			return response;
		} catch (IOException e) {
			throw new PrepareException(Failure.DOWNLOAD, "Could not reach " + uri.getHost(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new PrepareException(Failure.DOWNLOAD, "Interrupted");
		}
	}
}
