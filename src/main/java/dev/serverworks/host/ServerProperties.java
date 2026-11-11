package dev.serverworks.host;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Edits a {@code server.properties} file without disturbing what the owner put there.
 *
 * <p>Forced settings are always written, because the rack decides them (the port, the
 * player cap). Defaults are written only when the key is missing, so they can be changed
 * by hand afterwards. Every other line, including comments, is kept as it was.
 */
public final class ServerProperties {
	private ServerProperties() {
	}

	public static void apply(Path file, Map<String, String> forced, Map<String, String> defaults) throws IOException {
		List<String> lines = Files.exists(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : new ArrayList<>();
		Files.write(file, merge(lines, forced, defaults), StandardCharsets.UTF_8);
	}

	static List<String> merge(List<String> existing, Map<String, String> forced, Map<String, String> defaults) {
		Map<String, String> pendingForced = new LinkedHashMap<>(forced);
		Map<String, String> pendingDefaults = new LinkedHashMap<>(defaults);
		List<String> out = new ArrayList<>();
		for (String line : existing) {
			String key = keyOf(line);
			if (key != null && forced.containsKey(key)) {
				// Written once, even if the file repeats the key.
				if (pendingForced.containsKey(key)) {
					out.add(key + "=" + escape(pendingForced.remove(key)));
				}
				continue;
			}
			if (key != null) {
				pendingDefaults.remove(key);
			}
			out.add(line);
		}
		pendingDefaults.forEach((key, value) -> {
			if (!forced.containsKey(key)) {
				out.add(key + "=" + escape(value));
			}
		});
		pendingForced.forEach((key, value) -> out.add(key + "=" + escape(value)));
		return out;
	}

	private static String keyOf(String line) {
		String trimmed = line.stripLeading();
		if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
			return null;
		}
		int separator = trimmed.indexOf('=');
		return separator <= 0 ? null : trimmed.substring(0, separator).strip();
	}

	/** Values are single-line; anything that could start a new entry is flattened. */
	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\n", " ").replace("\r", " ");
	}
}
