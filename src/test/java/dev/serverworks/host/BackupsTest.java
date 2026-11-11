package dev.serverworks.host;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupsTest {
	@Test
	void backsUpTheWorldBeingPlayedAndRestoresIt(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("server.properties"), "level-name=world-0123abcd\n");
		Files.createDirectories(dir.resolve("world-0123abcd/region"));
		Files.writeString(dir.resolve("world-0123abcd/level.dat"), "first");
		Files.writeString(dir.resolve("world-0123abcd/session.lock"), "lock");
		Files.createDirectories(dir.resolve("world-0123abcd_nether"));
		Files.writeString(dir.resolve("world-0123abcd_nether/level.dat"), "nether");
		Files.createDirectories(dir.resolve("world"));
		Files.writeString(dir.resolve("world/level.dat"), "another world");
		assertEquals("world-0123abcd", Backups.levelName(dir));

		Backups.Backup made = Backups.backUp(dir, null, 10).join();
		assertTrue(made.name().startsWith("world-0123abcd-"), made.name());
		assertEquals(List.of(made.name()), Backups.list(dir).stream().map(Backups.Backup::name).toList());

		Files.writeString(dir.resolve("world-0123abcd/level.dat"), "changed");
		Files.writeString(dir.resolve("world-0123abcd/extra.dat"), "new file");
		Backups.restore(dir, made.name());
		assertEquals("first", Files.readString(dir.resolve("world-0123abcd/level.dat")));
		assertEquals("nether", Files.readString(dir.resolve("world-0123abcd_nether/level.dat")));
		assertFalse(Files.exists(dir.resolve("world-0123abcd/extra.dat")), "the restored world is exactly the backup");
		assertFalse(Files.exists(dir.resolve("world-0123abcd/session.lock")), "locks are not backed up");
		assertEquals("changed", Files.readString(dir.resolve("backups/before-restore/world-0123abcd/level.dat")),
				"the replaced world is kept");
		assertEquals("another world", Files.readString(dir.resolve("world/level.dat")), "other worlds are untouched");
	}

	@Test
	void keepsOnlyTheNewest(@TempDir Path dir) throws Exception {
		Files.createDirectories(dir.resolve("backups"));
		for (int i = 0; i < 5; i++) {
			Path file = dir.resolve("backups/world-2026010" + i + "-000000.zip");
			Files.writeString(file, "x");
			Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(1_000_000L * (i + 1)));
		}
		Backups.prune(dir, 2);
		assertEquals(List.of("world-20260104-000000.zip", "world-20260103-000000.zip"),
				Backups.list(dir).stream().map(Backups.Backup::name).toList());
	}

	@Test
	void refusesNamesAndEntriesThatLeaveTheWorld(@TempDir Path dir) throws Exception {
		assertThrows(java.io.IOException.class, () -> Backups.restore(dir, "../evil-20260101-000000.zip"));
		Files.createDirectories(dir.resolve("backups"));
		Path zip = dir.resolve("backups/world-20260101-000000.zip");
		try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream entries = new ZipOutputStream(out)) {
			entries.putNextEntry(new ZipEntry("world/level.dat"));
			entries.write("ok".getBytes());
			entries.putNextEntry(new ZipEntry("world/../server.properties"));
			entries.write("online-mode=false".getBytes());
			entries.putNextEntry(new ZipEntry("plugins/evil.jar"));
			entries.write("bad".getBytes());
		}
		Backups.restore(dir, "world-20260101-000000.zip");
		assertEquals("ok", Files.readString(dir.resolve("world/level.dat")));
		assertFalse(Files.exists(dir.resolve("server.properties")));
		assertFalse(Files.exists(dir.resolve("plugins/evil.jar")));
	}

	@Test
	void worldPlansMoveDataPacksWithTheWorld() {
		WorldPlan plan = new WorldPlan("world-0123abcd", "12345", "minecraft:flat", null);
		assertEquals("world-0123abcd/datapacks", plan.relocate("world/datapacks"));
		assertEquals("plugins", plan.relocate("plugins"));
		assertEquals("world/datapacks", WorldPlan.DEFAULT.relocate("world/datapacks"));
		assertThrows(IllegalArgumentException.class, () -> new WorldPlan("../world", "", "minecraft:normal", null));
		assertThrows(IllegalArgumentException.class, () -> new WorldPlan("world", "", "minecraft:debug_all", null));
	}
}
