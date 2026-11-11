package dev.serverworks.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogHostsTest {
	@Test
	void acceptsOnlyPlainHttpsCdnAddresses() {
		assertTrue(CatalogHosts.isCdnUrl("https://cdn.modrinth.com/data/gvQqBUqZ/icon_96.webp"));
		assertTrue(CatalogHosts.isCdnUrl("https://cdn.modrinth.com/data/x/versions/y/lithium-fabric-0.26.3%2Bmc26.3.jar"));

		for (String bad : new String[] {
				null,
				"",
				"http://cdn.modrinth.com/data/icon.webp",
				"https://cdn.modrinth.com.evil.example/icon.webp",
				"https://evil.example/cdn.modrinth.com/icon.webp",
				"https://user@cdn.modrinth.com/icon.webp",
				"https://cdn.modrinth.com:8443/icon.webp",
				"https://cdn.modrinth.com/data/../../secret",
				"file:///etc/passwd",
				"https://cdn.modrinth.com/a b",
				"https://cdn.modrinth.com/" + "a".repeat(600)}) {
			assertFalse(CatalogHosts.isCdnUrl(bad), String.valueOf(bad));
		}
	}
}
