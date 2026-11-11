package dev.serverworks.host;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.serverworks.host.ServerInstance.Failure;

/** Mojang's published version metadata: where the vanilla server is and which Java it needs. */
public final class MojangMeta {
	static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

	private MojangMeta() {
	}

	public record ServerInfo(String url, String sha1, long size, int javaVersion) {
	}

	public static ServerInfo server(Fetcher fetcher, String minecraftVersion) throws PrepareException {
		JsonObject manifest = object(fetcher.text(HostAllowlist.require(MANIFEST)));
		String versionUrl = null;
		for (JsonElement element : manifest.getAsJsonArray("versions")) {
			JsonObject version = element.getAsJsonObject();
			if (minecraftVersion.equals(version.get("id").getAsString())) {
				versionUrl = version.get("url").getAsString();
				break;
			}
		}
		if (versionUrl == null) {
			throw new PrepareException(Failure.UNSUPPORTED, "Minecraft " + minecraftVersion + " does not exist");
		}
		JsonObject version = object(fetcher.text(HostAllowlist.require(versionUrl)));
		JsonObject downloads = version.getAsJsonObject("downloads");
		if (downloads == null || !downloads.has("server")) {
			throw new PrepareException(Failure.UNSUPPORTED, "Minecraft " + minecraftVersion + " has no server download");
		}
		JsonObject server = downloads.getAsJsonObject("server");
		int java = version.has("javaVersion") ? version.getAsJsonObject("javaVersion").get("majorVersion").getAsInt() : 8;
		return new ServerInfo(server.get("url").getAsString(), server.get("sha1").getAsString(),
				server.get("size").getAsLong(), java);
	}

	static JsonObject object(String json) throws PrepareException {
		return parse(json).getAsJsonObject();
	}

	static JsonElement parse(String json) throws PrepareException {
		try {
			JsonElement element = JsonParser.parseString(json);
			if (element.isJsonNull()) {
				throw new JsonParseException("empty");
			}
			return element;
		} catch (JsonParseException | IllegalStateException e) {
			throw new PrepareException(Failure.DOWNLOAD, "Version metadata could not be read", e);
		}
	}
}
