package dev.serverworks.catalog;

import java.net.URI;
import java.net.URISyntaxException;

/** The only places catalog content may be fetched from. */
public final class CatalogHosts {
	public static final String CDN_HOST = "cdn.modrinth.com";
	private static final int MAX_URL_LENGTH = 512;

	private CatalogHosts() {
	}

	/**
	 * True when the URL is a plain HTTPS address on Modrinth's CDN. Icon and file URLs
	 * travel inside items and packets, so they are checked again wherever they are used.
	 */
	public static boolean isCdnUrl(String url) {
		if (url == null || url.isEmpty() || url.length() > MAX_URL_LENGTH) {
			return false;
		}
		try {
			URI uri = new URI(url);
			return "https".equals(uri.getScheme())
					&& CDN_HOST.equals(uri.getHost())
					&& uri.getPort() == -1
					&& uri.getUserInfo() == null
					&& uri.getRawPath() != null
					&& !uri.getRawPath().contains("..");
		} catch (URISyntaxException e) {
			return false;
		}
	}
}
