package dev.serverworks.item;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSettingsTest {
	@Test
	void cardsBecomePropertiesAndGameRules() {
		ServerSettings settings = new ServerSettings(ServerSettings.Difficulty.HARD, ServerSettings.GameMode.CREATIVE, false,
				false, true, true, 99, 1, 16, "Hello\nop-permission-level=4 §cred");
		assertEquals("hard", settings.properties().get("difficulty"));
		assertEquals("creative", settings.properties().get("gamemode"));
		assertEquals("32", settings.properties().get("view-distance"), "distances are clamped");
		assertEquals("2", settings.properties().get("simulation-distance"));
		String motd = settings.properties().get("motd");
		assertFalse(motd.contains("\n") || motd.contains("§"), motd);
		assertTrue(settings.gameRuleCommands().contains("gamerule pvp false"));
		assertTrue(settings.gameRuleCommands().contains("gamerule keep_inventory true"));
	}

	@Test
	void driveNamesAndSeedsAreCleaned() {
		WorldDrive drive = new WorldDrive(UUID.fromString("0123abcd-0000-0000-0000-000000000000"), "My §4World",
				"seed;rm -rf /", WorldDrive.WorldType.FLAT, Optional.of("../other"));
		assertEquals("world-0123abcd", drive.levelName());
		assertFalse(drive.name().contains("§"));
		assertEquals("seedrm -rf", drive.seed());
		assertTrue(drive.importSave().isEmpty(), "a save name that climbs out of the saves folder is dropped");
		assertEquals("minecraft:flat", drive.type().levelType());
	}
}
