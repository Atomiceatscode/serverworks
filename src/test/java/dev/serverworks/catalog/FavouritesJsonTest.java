package dev.serverworks.catalog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.serverworks.catalog.model.ProjectSummary;
import dev.serverworks.catalog.model.ProjectType;
import dev.serverworks.catalog.model.SideSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Disk Writer keeps favourites as JSON made from project records; they must come back the same. */
class FavouritesJsonTest {
	@Test
	void projectsSurviveARoundTrip() {
		ProjectSummary lithium = new ProjectSummary("gvQqBUqZ", "lithium", "Lithium", "Fast", "jellysquid3", ProjectType.MOD,
				List.of("fabric", "quilt"), List.of("optimization"), List.of("26.3"), 142_700_000L, 5000L,
				"https://cdn.modrinth.com/data/gvQqBUqZ/icon.png", SideSupport.OPTIONAL, SideSupport.REQUIRED, 0x88CC44);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(List.of(lithium));
		List<ProjectSummary> back = gson.fromJson(json, new TypeToken<List<ProjectSummary>>() { }.getType());
		assertEquals(List.of(lithium), back);
	}
}
