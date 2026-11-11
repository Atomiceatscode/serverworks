package dev.serverworks.client;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Create World screen's "Serverworks Showcase" switch. Turning it on picks the settings
 * the showcase is made for; the showcase itself is built when the player first joins.
 */
public final class ShowcaseWorldOption {
	private static final Map<WorldCreationUiState, Boolean> CHOSEN = Collections.synchronizedMap(new WeakHashMap<>());

	private ShowcaseWorldOption() {
	}

	public static boolean isOn(WorldCreationUiState state) {
		return CHOSEN.getOrDefault(state, false);
	}

	public static void set(WorldCreationUiState state, boolean on) {
		CHOSEN.put(state, on);
		if (on) {
			state.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
			state.setAllowCommands(true);
			for (WorldCreationUiState.WorldTypeEntry type : state.getNormalPresetList()) {
				if (type.preset() != null && type.preset().is(WorldPresets.FLAT)) {
					state.setWorldType(type);
				}
			}
		} else {
			state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
			state.setAllowCommands(false);
			for (WorldCreationUiState.WorldTypeEntry type : state.getNormalPresetList()) {
				if (type.preset() != null && type.preset().is(WorldPresets.NORMAL)) {
					state.setWorldType(type);
				}
			}
		}
	}
}
