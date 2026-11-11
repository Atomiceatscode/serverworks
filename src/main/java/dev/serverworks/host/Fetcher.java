package dev.serverworks.host;

import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;
import java.util.function.LongConsumer;

/** Network access for the server runtime, behind an interface so tests need no network. */
public interface Fetcher {
	/** Reads a small text document, such as version metadata. */
	String text(URI uri) throws PrepareException;

	/**
	 * Downloads a file. The file only appears at {@code target} once it is complete and,
	 * when a checksum is given, verified.
	 *
	 * @param expected the digest the file must have, or null when the source publishes none
	 * @param maxBytes refuse anything larger
	 * @param progress told how many more bytes have arrived, many times
	 */
	void download(URI uri, Path target, @Nullable Checksum expected, long maxBytes, LongConsumer progress)
			throws PrepareException;
}
