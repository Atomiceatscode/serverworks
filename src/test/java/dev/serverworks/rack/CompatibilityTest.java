package dev.serverworks.rack;

import dev.serverworks.catalog.model.ProjectType;
import dev.serverworks.catalog.model.SideSupport;
import dev.serverworks.item.DiskLabel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibilityTest {
	private static DiskLabel disk(String title, SideSupport client) {
		return new DiskLabel(title.toLowerCase(), title.toLowerCase(), title, "", ProjectType.MOD, List.of("fabric"),
				Optional.empty(), -1, client, SideSupport.REQUIRED, Optional.empty());
	}

	@Test
	void serverSideContentLetsEveryoneIn() {
		assertTrue(Compatibility.check(List.of()).open());
		assertTrue(Compatibility.check(List.of(disk("Lithium", SideSupport.OPTIONAL), disk("Chunky", SideSupport.UNSUPPORTED),
				disk("Mystery", SideSupport.UNKNOWN))).open());
	}

	@Test
	void clientRequiredContentIsNamed() {
		Compatibility.Result result = Compatibility.check(List.of(disk("Lithium", SideSupport.OPTIONAL),
				disk("Create", SideSupport.REQUIRED), disk("Create", SideSupport.REQUIRED), disk("Sodium", SideSupport.REQUIRED)));
		assertFalse(result.open());
		assertEquals(List.of("Create", "Sodium"), result.clientMods());
	}
}
