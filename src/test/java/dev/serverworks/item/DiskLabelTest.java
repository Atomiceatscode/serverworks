package dev.serverworks.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.serverworks.catalog.model.ProjectSummary;
import dev.serverworks.catalog.model.ProjectType;
import dev.serverworks.catalog.model.SideSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiskLabelTest {
	private static ProjectSummary project(String icon, SideSupport client) {
		return new ProjectSummary("gvQqBUqZ", "lithium", "Lithium", "Fast", "jellysquid3", ProjectType.MOD,
				List.of("fabric", "quilt"), List.of("optimization"), List.of("26.3"), 100, 10, icon, client,
				SideSupport.OPTIONAL, 0xEAEAF3);
	}

	@Test
	void survivesSavingAndLoading() {
		DiskLabel label = DiskLabel.of(project("https://cdn.modrinth.com/data/gvQqBUqZ/icon_96.webp", SideSupport.OPTIONAL));

		JsonElement saved = DiskLabel.CODEC.encodeStart(JsonOps.INSTANCE, label).getOrThrow();
		DiskLabel loaded = DiskLabel.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();

		assertEquals(label, loaded);
		assertEquals("Lithium", loaded.title());
		assertEquals(ProjectType.MOD, loaded.type());
		assertEquals(List.of("fabric", "quilt"), loaded.loaders());
		assertTrue(loaded.serverSideOnly());
	}

	@Test
	void dropsIconsThatAreNotOnTheCatalogCdn() {
		assertEquals(Optional.empty(), DiskLabel.of(project("https://evil.example/icon.png", SideSupport.OPTIONAL)).iconUrl());
		assertEquals(Optional.empty(), DiskLabel.of(project(null, SideSupport.OPTIONAL)).iconUrl());

		// A hand-edited or malicious item must not smuggle one in either.
		JsonElement forged = JsonParser.parseString(
				"{\"project\":\"abc\",\"slug\":\"abc\",\"title\":\"Abc\",\"icon\":\"http://127.0.0.1:8080/x.png\"}");
		assertEquals(Optional.empty(), DiskLabel.CODEC.parse(JsonOps.INSTANCE, forged).getOrThrow().iconUrl());
	}

	@Test
	void rejectsOversizedOrMissingFields() {
		assertTrue(DiskLabel.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
				"{\"project\":\"" + "x".repeat(65) + "\",\"slug\":\"a\",\"title\":\"A\"}")).isError());
		assertTrue(DiskLabel.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"slug\":\"a\",\"title\":\"A\"}")).isError());
	}

	@Test
	void flagsProjectsPlayersMustInstall() {
		assertFalse(DiskLabel.of(project(null, SideSupport.REQUIRED)).serverSideOnly());
	}

	@Test
	void clipsOverlongTitles() {
		ProjectSummary longTitle = new ProjectSummary("id", "slug", "T".repeat(300), "", "a".repeat(100), ProjectType.PLUGIN,
				List.of(), List.of(), List.of(), 0, 0, null, SideSupport.UNKNOWN, SideSupport.UNKNOWN, -1);
		DiskLabel label = DiskLabel.of(longTitle);
		assertEquals(128, label.title().length());
		assertEquals(64, label.author().length());
		assertFalse(DiskLabel.CODEC.encodeStart(JsonOps.INSTANCE, label).isError());
	}
}
