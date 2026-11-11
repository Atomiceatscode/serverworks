package dev.serverworks.host;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

/**
 * Adds players to a rack server's {@code whitelist.json} with their exact name and id.
 *
 * <p>The vanilla {@code whitelist add} command looks the name up with Mojang first and, on an
 * offline server, falls back to an id made from the lower-cased name, which never matches
 * the player who then joins. Writing the entry ourselves and reloading avoids both the
 * lookup and that mismatch.
 */
public final class Whitelist {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private Whitelist() {
	}

	/** The id an offline-mode server gives a player: derived from the exact name, case included. */
	public static UUID offlineId(String name) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
	}

	/** Whether the server in {@code directory} checks accounts with Mojang. Defaults to true, like the game. */
	public static boolean onlineMode(Path directory) {
		Path file = directory.resolve("server.properties");
		if (!Files.isRegularFile(file)) {
			return true;
		}
		Properties properties = new Properties();
		try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			properties.load(reader);
		} catch (IOException e) {
			return true;
		}
		return !"false".equals(properties.getProperty("online-mode", "true").strip().toLowerCase(Locale.ROOT));
	}

	/**
	 * Makes sure {@code name} is on the whitelist with the id this server will see. Entries for
	 * the same name in other letter cases are replaced, other entries are kept.
	 *
	 * @param accountId the player's account id, used when the server is in online mode
	 */
	public static void admit(Path directory, String name, UUID accountId) throws IOException {
		UUID id = onlineMode(directory) ? accountId : offlineId(name);
		Path file = directory.resolve("whitelist.json");
		JsonArray entries = new JsonArray();
		if (Files.isRegularFile(file)) {
			try {
				JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
				if (parsed.isJsonArray()) {
					entries = parsed.getAsJsonArray();
				}
			} catch (RuntimeException e) {
				// A damaged list is replaced rather than letting nobody in.
			}
		}
		JsonArray kept = new JsonArray();
		for (JsonElement entry : entries) {
			if (entry.isJsonObject()) {
				JsonObject object = entry.getAsJsonObject();
				String entryName = object.has("name") ? object.get("name").getAsString() : "";
				String entryId = object.has("uuid") ? object.get("uuid").getAsString() : "";
				if (entryName.equalsIgnoreCase(name) || entryId.equals(id.toString())) {
					continue;
				}
			}
			kept.add(entry);
		}
		JsonObject added = new JsonObject();
		added.addProperty("uuid", id.toString());
		added.addProperty("name", name);
		kept.add(added);
		Path temporary = directory.resolve("whitelist.json.serverworks");
		Files.writeString(temporary, GSON.toJson(kept), StandardCharsets.UTF_8);
		Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
