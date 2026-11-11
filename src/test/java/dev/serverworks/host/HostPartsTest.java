package dev.serverworks.host;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostPartsTest {
	@Test
	void propertiesKeepOwnerEditsAndForceRackSettings() {
		List<String> existing = List.of(
				"#Minecraft server properties",
				"difficulty=hard",
				"server-port=25565",
				"motd=My own words",
				"server-port=1",
				"  pvp = false");
		Map<String, String> forced = new LinkedHashMap<>();
		forced.put("server-port", "25600");
		forced.put("max-players", "10");
		Map<String, String> defaults = new LinkedHashMap<>();
		defaults.put("motd", "Default");
		defaults.put("view-distance", "8");

		List<String> merged = ServerProperties.merge(existing, forced, defaults);

		assertEquals(List.of(
				"#Minecraft server properties",
				"difficulty=hard",
				"server-port=25600",
				"motd=My own words",
				"  pvp = false",
				"view-distance=8",
				"max-players=10"), merged);
	}

	@Test
	void propertyValuesCannotAddLines() {
		List<String> merged = ServerProperties.merge(List.of(), Map.of("motd", "hi\nop-permission-level=4"), Map.of());
		assertEquals(List.of("motd=hi op-permission-level=4"), merged);
	}

	@Test
	void portsAreNotHandedOutTwiceOrWhileBusy() throws Exception {
		PortAllocator ports = new PortAllocator(25680, 25683);
		int first = ports.acquire();
		int second = ports.acquire();
		assertNotEquals(first, second);
		ports.release(first);
		try (ServerSocket busy = new ServerSocket(first)) {
			assertNotEquals(first, ports.acquire(), "a port something else is listening on must be skipped");
		}
		PortAllocator tiny = new PortAllocator(25690, 25690);
		assertEquals(25690, tiny.acquire());
		assertEquals(-1, tiny.acquire());
	}

	@Test
	void consoleKeepsTheNewestLinesAndTracksSequence() {
		ConsoleBuffer console = new ConsoleBuffer(3);
		for (int i = 0; i < 5; i++) {
			console.add("line " + i);
		}
		assertEquals(5, console.nextSequence());
		ConsoleBuffer.Page all = console.since(0, 10);
		assertEquals(2, all.firstSequence());
		assertEquals(List.of("line 2", "line 3", "line 4"), all.lines());
		assertEquals(List.of("line 4"), console.since(4, 10).lines());
		assertTrue(console.since(5, 10).lines().isEmpty());
		assertEquals(List.of("line 2"), console.since(0, 1).lines());
	}

	@Test
	void consoleLinesAreMadeSafeToDisplay() {
		assertEquals("[INFO] ok", ConsoleBuffer.sanitize("\u001B[0;32m[INFO]\u001B[m ok\u0007"));
		assertEquals("a  b", ConsoleBuffer.sanitize("a\tb"));
		assertEquals("ared", ConsoleBuffer.sanitize("§ared").replace("a", "a"));
		assertEquals(ConsoleBuffer.MAX_LINE_LENGTH, ConsoleBuffer.sanitize("x".repeat(5000)).length());
	}

	@Test
	void onlyKnownHttpsHostsAreAllowed() {
		assertTrue(HostAllowlist.allows(URI.create("https://piston-data.mojang.com/v1/objects/abc/server.jar")));
		assertTrue(HostAllowlist.allows(URI.create("https://fill-data.papermc.io/v1/objects/abc/paper.jar")));
		assertTrue(HostAllowlist.allows(URI.create("https://cdn.modrinth.com/data/x/versions/y/z.jar")));
		assertFalse(HostAllowlist.allows(URI.create("http://piston-data.mojang.com/server.jar")));
		assertFalse(HostAllowlist.allows(URI.create("https://piston-data.mojang.com.evil.example/server.jar")));
		assertFalse(HostAllowlist.allows(URI.create("https://evil.example/server.jar")));
		assertFalse(HostAllowlist.allows(URI.create("https://user@fill.papermc.io/x")));
		assertFalse(HostAllowlist.allows(URI.create("https://fill.papermc.io:8443/x")));
		assertFalse(HostAllowlist.allows(URI.create("file:///etc/passwd")));
	}
}
