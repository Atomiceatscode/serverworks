package dev.serverworks.client.icon;

import com.mojang.blaze3d.platform.NativeImage;
import dev.serverworks.Serverworks;
import dev.serverworks.catalog.CatalogHosts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Catalog project icons as game textures.
 *
 * <p>Ask for an icon by URL every frame you want to draw it. The first request starts a
 * background download and returns null; once the texture exists the same call returns
 * its id. Icons that have not been asked for in a while are released.
 *
 * <p>All public methods must be called on the render thread.
 */
public final class IconCache {
	public static final IconCache INSTANCE = new IconCache();

	/** Icons are stored at this size: twice an inventory slot, which is as large as they are ever drawn. */
	public static final int ICON_SIZE = 32;
	private static final int MAX_ICONS = 512;
	private static final long MAX_DOWNLOAD_BYTES = 2L * 1024 * 1024;

	private final Map<String, Entry> entries = new LinkedHashMap<>(64, 0.75f, true);
	private final ExecutorService workers = Executors.newFixedThreadPool(3, runnable -> {
		Thread thread = new Thread(runnable, "Serverworks-Icons-" + THREAD_IDS.incrementAndGet());
		thread.setDaemon(true);
		return thread;
	});
	private static final AtomicInteger THREAD_IDS = new AtomicInteger();
	private @Nullable HttpClient http;

	private IconCache() {
	}

	/** The texture for an icon URL, or null while it is loading, failed, or the URL is not allowed. */
	public @Nullable Identifier get(@Nullable String url) {
		if (url == null) {
			return null;
		}
		Entry entry = entries.get(url);
		if (entry != null) {
			return entry.texture;
		}
		if (!CatalogHosts.isCdnUrl(url)) {
			return null;
		}
		Entry created = new Entry();
		entries.put(url, created);
		evictOldest();
		workers.execute(() -> load(url, created));
		return null;
	}

	private void load(String url, Entry entry) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(15))
					.header("User-Agent", Serverworks.userAgent())
					.GET()
					.build();
			HttpResponse<byte[]> response = http().send(request,
					HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), MAX_DOWNLOAD_BYTES));
			if (response.statusCode() != 200) {
				throw new java.io.IOException("HTTP " + response.statusCode());
			}
			int[] pixels = IconDecoder.decode(response.body(), ICON_SIZE);
			Minecraft.getInstance().execute(() -> publish(url, entry, pixels));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception | LinkageError e) {
			// A missing icon is cosmetic: the placeholder stays, and it is not retried this session.
			Serverworks.LOGGER.debug("Could not load icon {}: {}", url, e.toString());
		}
	}

	private void publish(String url, Entry entry, int[] pixels) {
		if (entries.get(url) != entry) {
			return; // Evicted while downloading.
		}
		NativeImage image = new NativeImage(ICON_SIZE, ICON_SIZE, false);
		for (int y = 0; y < ICON_SIZE; y++) {
			for (int x = 0; x < ICON_SIZE; x++) {
				image.setPixel(x, y, pixels[y * ICON_SIZE + x]);
			}
		}
		Identifier id = Serverworks.id("icon/" + digest(url));
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Serverworks icon", image));
		entry.texture = id;
	}

	private void evictOldest() {
		Iterator<Map.Entry<String, Entry>> eldest = entries.entrySet().iterator();
		while (entries.size() > MAX_ICONS && eldest.hasNext()) {
			Entry entry = eldest.next().getValue();
			eldest.remove();
			if (entry.texture != null) {
				Minecraft.getInstance().getTextureManager().release(entry.texture);
			}
		}
	}

	private synchronized HttpClient http() {
		if (http == null) {
			http = HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(10))
					.followRedirects(HttpClient.Redirect.NEVER)
					.build();
		}
		return http;
	}

	private static String digest(String url) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-1").digest(url.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash, 0, 12);
		} catch (NoSuchAlgorithmException e) {
			return Integer.toHexString(url.hashCode());
		}
	}

	private static final class Entry {
		@Nullable Identifier texture;
	}
}
