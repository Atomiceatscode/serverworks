package dev.serverworks.host;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhitelistTest {
	@Test
	void offlineIdMatchesWhatAServerAssigns() {
		// Paper logged this id for Player987 joining an offline-mode rack.
		assertEquals(UUID.fromString("6ac40561-f0e1-3533-ae6f-ce769a09b7f1"), Whitelist.offlineId("Player987"));
		// The vanilla command whitelisted this one for "Player996", from the lower-cased name.
		assertEquals(UUID.fromString("f33febb8-e528-3a6a-bc54-67436c412020"), Whitelist.offlineId("player996"));
	}

	@Test
	void offlineServersGetTheIdFromTheExactName(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("server.properties"), "online-mode=false\nwhite-list=true\n");
		Files.writeString(dir.resolve("whitelist.json"),
				"[{\"uuid\":\"f33febb8-e528-3a6a-bc54-67436c412020\",\"name\":\"player996\"},"
						+ "{\"uuid\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"Friend\"}]");
		Whitelist.admit(dir, "Player996", UUID.randomUUID());
		String list = Files.readString(dir.resolve("whitelist.json"));
		assertTrue(list.contains(Whitelist.offlineId("Player996").toString()), list);
		assertTrue(list.contains("\"Player996\""), list);
		assertFalse(list.contains("f33febb8"), "the wrong lower-case entry is replaced: " + list);
		assertTrue(list.contains("Friend"), "other players stay: " + list);
	}

	@Test
	void onlineServersGetTheAccountId(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("server.properties"), "online-mode=true\n");
		UUID account = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
		Whitelist.admit(dir, "Notch", account);
		Whitelist.admit(dir, "Notch", account);
		String list = Files.readString(dir.resolve("whitelist.json"));
		assertTrue(list.contains(account.toString()), list);
		assertEquals(list.indexOf("Notch"), list.lastIndexOf("Notch"), "no duplicates: " + list);
	}

	@Test
	void missingPropertiesMeansOnline(@TempDir Path dir) {
		assertTrue(Whitelist.onlineMode(dir));
	}
}
