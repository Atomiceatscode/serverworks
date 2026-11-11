package dev.serverworks.host;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.serverworks.catalog.model.SideSupport;
import dev.serverworks.rack.Platform;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a Modrinth modpack file ({@code .mrpack}) holding the mods a player needs to join
 * a rack server on a mod platform. Launchers such as the Modrinth App and Prism import it
 * directly. It lists download addresses and hashes only, so it is tiny.
 */
public final class ClientPack {
	public static final String FILE_NAME = "client-pack.mrpack";

	private ClientPack() {
	}

	/** The key launchers use for this platform's loader, or null when it has no client side. */
	static @Nullable String loaderKey(Platform platform) {
		return switch (platform) {
			case FABRIC -> "fabric-loader";
			case QUILT -> "quilt-loader";
			case NEOFORGE -> "neoforge";
			case FORGE -> "forge";
			default -> null;
		};
	}

	/** Returns the pack's bytes, or null when this platform has no client pack. */
	public static byte @Nullable [] build(Platform platform, String minecraftVersion, @Nullable String loaderVersion,
			String name, List<ContentFile> content) throws IOException {
		String loaderKey = loaderKey(platform);
		if (loaderKey == null || loaderVersion == null) {
			return null;
		}
		JsonObject index = new JsonObject();
		index.addProperty("formatVersion", 1);
		index.addProperty("game", "minecraft");
		index.addProperty("versionId", "serverworks-" + minecraftVersion);
		index.addProperty("name", name);
		index.addProperty("summary", "Mods for joining a Serverworks rack server");
		JsonArray files = new JsonArray();
		for (ContentFile file : content) {
			if (!file.directory().equals("mods") || file.sha1() == null || file.clientSide() == SideSupport.UNSUPPORTED) {
				continue;
			}
			JsonObject entry = new JsonObject();
			entry.addProperty("path", "mods/" + file.fileName());
			JsonObject hashes = new JsonObject();
			hashes.addProperty("sha1", file.sha1());
			hashes.addProperty("sha512", file.sha512());
			entry.add("hashes", hashes);
			JsonObject env = new JsonObject();
			env.addProperty("client", file.clientSide() == SideSupport.OPTIONAL ? "optional" : "required");
			env.addProperty("server", "required");
			entry.add("env", env);
			JsonArray downloads = new JsonArray();
			downloads.add(file.url());
			entry.add("downloads", downloads);
			entry.addProperty("fileSize", file.size());
			files.add(entry);
		}
		index.add("files", files);
		JsonObject dependencies = new JsonObject();
		dependencies.addProperty("minecraft", minecraftVersion);
		dependencies.addProperty(loaderKey, loaderVersion);
		index.add("dependencies", dependencies);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("modrinth.index.json"));
			zip.write(new GsonBuilder().setPrettyPrinting().create().toJson(index).getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		return bytes.toByteArray();
	}
}
