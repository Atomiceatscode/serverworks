package dev.serverworks.host;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.serverworks.catalog.CatalogHosts;
import dev.serverworks.catalog.model.SideSupport;
import dev.serverworks.host.ServerInstance.Failure;
import dev.serverworks.rack.Platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads a Modrinth modpack ({@code .mrpack}): the files a server needs from it, and its
 * {@code overrides} folders. Every path is checked before anything is written.
 */
final class ModpackIndex {
	private static final long MAX_INDEX_BYTES = 8L * 1024 * 1024;
	private static final long MAX_OVERRIDE_BYTES = 256L * 1024 * 1024;
	static final int MAX_OVERRIDE_FILES = 20_000;

	private ModpackIndex() {
	}

	/** The server-side files listed by a pack, after checking it suits this platform and Minecraft version. */
	static List<ContentFile> serverFiles(Path mrpack, String packTitle, Platform platform, String minecraftVersion)
			throws PrepareException {
		JsonObject index;
		try (ZipFile zip = new ZipFile(mrpack.toFile())) {
			ZipEntry entry = zip.getEntry("modrinth.index.json");
			if (entry == null || entry.getSize() > MAX_INDEX_BYTES) {
				throw new PrepareException(Failure.CONTENT, packTitle + " is not a valid modpack");
			}
			try (InputStream in = zip.getInputStream(entry)) {
				index = JsonParser.parseString(new String(in.readNBytes((int) MAX_INDEX_BYTES), StandardCharsets.UTF_8))
						.getAsJsonObject();
			}
		} catch (IOException | RuntimeException e) {
			throw new PrepareException(Failure.CONTENT, packTitle + " could not be read", e);
		}

		JsonObject dependencies = index.getAsJsonObject("dependencies");
		String packMinecraft = dependencies == null || !dependencies.has("minecraft") ? "" : dependencies.get("minecraft").getAsString();
		if (!packMinecraft.equals(minecraftVersion)) {
			throw new PrepareException(Failure.CONTENT, packTitle + " is made for Minecraft " + packMinecraft
					+ ", but this world runs " + minecraftVersion);
		}
		if (!loaderFits(dependencies, platform)) {
			throw new PrepareException(Failure.CONTENT, packTitle + " needs a different platform than " + platform.id());
		}

		List<ContentFile> files = new ArrayList<>();
		for (JsonElement element : index.getAsJsonArray("files")) {
			JsonObject file = element.getAsJsonObject();
			JsonObject env = file.getAsJsonObject("env");
			if (env != null && "unsupported".equals(text(env, "server"))) {
				continue;
			}
			String path = text(file, "path");
			if (!safe(path) || path.indexOf('/') < 0) {
				throw new PrepareException(Failure.CONTENT, packTitle + " has a file at an unsafe location: " + path);
			}
			String url = file.getAsJsonArray("downloads").isEmpty() ? "" : file.getAsJsonArray("downloads").get(0).getAsString();
			if (!CatalogHosts.isCdnUrl(url)) {
				throw new PrepareException(Failure.CONTENT, packTitle + " downloads " + path
						+ " from outside Modrinth, which racks do not allow");
			}
			JsonObject hashes = file.getAsJsonObject("hashes");
			String sha512 = hashes == null ? "" : text(hashes, "sha512").toLowerCase(Locale.ROOT);
			if (sha512.length() != 128) {
				throw new PrepareException(Failure.CONTENT, packTitle + " lists " + path + " without a SHA-512 hash");
			}
			int slash = path.lastIndexOf('/');
			SideSupport client = env == null ? SideSupport.UNKNOWN : SideSupport.fromApi(text(env, "client"));
			files.add(new ContentFile(url, path.substring(slash + 1), sha512, hashes.has("sha1") ? text(hashes, "sha1") : null,
					file.has("fileSize") ? file.get("fileSize").getAsLong() : -1, path.substring(0, slash), packTitle, true, client));
		}
		return files;
	}

	/** Copies the pack's overrides, then its server-only overrides, into the server folder. */
	static void extractOverrides(Path mrpack, Path dir) throws IOException {
		long written = 0;
		int files = 0;
		try (ZipFile zip = new ZipFile(mrpack.toFile())) {
			for (String prefix : List.of("overrides/", "server-overrides/")) {
				Enumeration<? extends ZipEntry> entries = zip.entries();
				while (entries.hasMoreElements()) {
					ZipEntry entry = entries.nextElement();
					String name = entry.getName();
					if (entry.isDirectory() || !name.startsWith(prefix) || name.length() == prefix.length()) {
						continue;
					}
					if (++files > MAX_OVERRIDE_FILES) {
						throw new IOException("The modpack has too many override files");
					}
					String relative = name.substring(prefix.length());
					Path target = dir.resolve(relative).normalize();
					if (!safe(relative) || !target.startsWith(dir)) {
						throw new IOException("The modpack has an override at an unsafe location: " + relative);
					}
					Files.createDirectories(target.getParent());
					try (InputStream in = zip.getInputStream(entry)) {
						byte[] data = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, MAX_OVERRIDE_BYTES - written + 1));
						written += data.length;
						if (written > MAX_OVERRIDE_BYTES) {
							throw new IOException("The modpack's overrides are too large");
						}
						Files.write(target, data);
					}
				}
			}
		}
	}

	private static boolean loaderFits(JsonObject dependencies, Platform platform) {
		for (Map.Entry<String, JsonElement> entry : dependencies.entrySet()) {
			boolean fits = switch (entry.getKey()) {
				case "minecraft" -> true;
				case "fabric-loader" -> platform == Platform.FABRIC || platform == Platform.QUILT;
				case "quilt-loader" -> platform == Platform.QUILT;
				case "neoforge" -> platform == Platform.NEOFORGE;
				case "forge" -> platform == Platform.FORGE;
				default -> false;
			};
			if (!fits) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A relative path that stays inside the server folder on every operating system: no
	 * empty, "." or ".." segments, no leading slash or drive, and none of the characters
	 * Windows forbids. Other characters, such as the colour codes some packs put in file
	 * names, are harmless and allowed.
	 */
	static boolean safe(String path) {
		if (path == null || path.isEmpty() || path.length() > 400 || path.startsWith("/")) {
			return false;
		}
		for (int i = 0; i < path.length(); i++) {
			char c = path.charAt(i);
			if (c < 0x20 || c == 0x7F || "\\:*?\"<>|".indexOf(c) >= 0) {
				return false;
			}
		}
		for (String segment : path.split("/", -1)) {
			if (segment.isEmpty() || segment.equals(".") || segment.equals("..") || segment.endsWith(" ") || segment.endsWith(".")) {
				return false;
			}
		}
		return true;
	}

	private static String text(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element == null || element.isJsonNull() ? "" : element.getAsString();
	}
}
