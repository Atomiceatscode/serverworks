package dev.serverworks.host;

import dev.serverworks.rack.Platform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyPartsTest {
	@Test
	void forwardingIsTheBestEveryoneUnderstands() {
		assertEquals(Forwarding.MODERN, Forwarding.choose(Platform.VELOCITY, List.of(Platform.PAPER, Platform.FABRIC)));
		assertEquals(Forwarding.LEGACY, Forwarding.choose(Platform.VELOCITY, List.of(Platform.PAPER, Platform.SPIGOT)));
		assertEquals(Forwarding.NONE, Forwarding.choose(Platform.VELOCITY, List.of(Platform.PAPER, Platform.VANILLA)));
		assertEquals(Forwarding.LEGACY, Forwarding.choose(Platform.BUNGEECORD, List.of(Platform.PAPER, Platform.PURPUR)));
		assertEquals(Forwarding.NONE, Forwarding.choose(Platform.BUNGEECORD, List.of(Platform.FABRIC)));
		assertFalse(Forwarding.canBeBackend(Platform.VELOCITY));
		assertTrue(Forwarding.canBeBackend(Platform.NEOFORGE));
	}

	@Test
	void tomlTopLevelKeysAreSetBeforeTheFirstTable() {
		List<String> lines = List.of("# comment", "bind = \"0.0.0.0:25565\"", "online-mode = true", "[servers]", "online-mode = 1");
		List<String> out = ConfigEdits.tomlSetTopLevel(lines, "online-mode", "false");
		assertEquals(List.of("# comment", "bind = \"0.0.0.0:25565\"", "online-mode = false", "[servers]", "online-mode = 1"), out);
		out = ConfigEdits.tomlSetTopLevel(out, "new-key", "3");
		assertEquals("new-key = 3", out.get(3));
		assertEquals("[servers]", out.get(4));
	}

	@Test
	void tomlTablesAreReplacedWholeAndOthersKept() {
		List<String> lines = List.of("a = 1", "[servers]", "lobby = \"x\"", "try = [", "  \"lobby\"", "]",
				"[forced-hosts]", "\"lobby.example.com\" = [", "  \"lobby\"", "]", "[advanced]", "c = 2");
		List<String> out = ConfigEdits.tomlReplaceTable(lines, "servers", List.of("paper-1 = \"127.0.0.1:25600\"", "try = [\"paper-1\"]"));
		out = ConfigEdits.tomlReplaceTable(out, "forced-hosts", List.of());
		assertEquals(List.of("a = 1", "[servers]", "paper-1 = \"127.0.0.1:25600\"", "try = [\"paper-1\"]", "[forced-hosts]",
				"[advanced]", "c = 2"), out);
		assertEquals(List.of("[query]", "x = 1"), ConfigEdits.tomlReplaceTable(List.of(), "query", List.of("x = 1")));
	}

	@Test
	void yamlValuesAreSetInPlaceOrAddedUnderTheirParent() {
		List<String> lines = List.of(
				"_version: 31",
				"proxies:",
				"  bungee-cord:",
				"    online-mode: true",
				"  velocity:",
				"    enabled: false",
				"    online-mode: false",
				"    secret: ''",
				"scoreboards:",
				"  track-plugin-scoreboards: false");
		List<String> out = ConfigEdits.yamlSet(lines, List.of("proxies", "velocity", "enabled"), "true");
		out = ConfigEdits.yamlSet(out, List.of("proxies", "velocity", "secret"), "'abc'");
		assertEquals("    enabled: true", out.get(5));
		assertEquals("    online-mode: false", out.get(6), "the same key under another parent is untouched");
		assertEquals("    secret: 'abc'", out.get(7));
		assertEquals("    online-mode: true", out.get(3));

		List<String> added = ConfigEdits.yamlSet(lines, List.of("proxies", "velocity", "max-players"), "5");
		assertEquals("    max-players: 5", added.get(8));
		assertEquals("scoreboards:", added.get(9));

		List<String> fresh = ConfigEdits.yamlSet(List.of(), List.of("settings", "bungeecord"), "true");
		assertEquals(List.of("settings:", "  bungeecord: true"), fresh);
		List<String> sibling = ConfigEdits.yamlSet(List.of("settings:", "  debug: false", "world-settings:", "  default: {}"),
				List.of("settings", "bungeecord"), "true");
		assertEquals(List.of("settings:", "  debug: false", "  bungeecord: true", "world-settings:", "  default: {}"), sibling);
	}

	@Test
	void modpackPathsMustStayInsideTheServer() {
		for (String ok : List.of("mods/a.jar", "resourcepacks/\u00a74Drodi's Illagers [v5.3.2].zip", "config/sub/x.toml", "a b/c")) {
			assertTrue(ModpackIndex.safe(ok), ok);
		}
		for (String bad : List.of("", "/etc/passwd", "../x", "mods/../../x", "mods//x", "C:/x", "mods\\x", "mods/x?",
				"mods/.", "mods/x.", "mods/\u0000", "mods/a ")) {
			assertFalse(ModpackIndex.safe(bad), bad);
		}
	}

	@Test
	void proxyServerNamesAreSafe() {
		assertEquals("paper-1", ProxyConfigs.serverName("Paper", 1));
		assertEquals("my-cool-rack-2", ProxyConfigs.serverName("My Cool Rack!", 2));
		assertEquals("server-3", ProxyConfigs.serverName("!!!", 3));
	}
}
