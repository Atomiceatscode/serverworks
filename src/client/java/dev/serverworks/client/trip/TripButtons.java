package dev.serverworks.client.trip;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

/** Adds "Return to ..." to the pause menu on a rack server, and to the screen shown if it drops you. */
public final class TripButtons {
	public static final int WIDTH = 204;

	private TripButtons() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			Trips.Origin origin = Trips.origin();
			if (origin == null) {
				return;
			}
			Component label = Component.translatable("serverworks.trip.return", origin.name());
			if (screen instanceof PauseScreen && Trips.onTrip()) {
				Screens.getWidgets(screen).add(Button.builder(label, button -> Trips.goBack())
						.bounds(width / 2 - WIDTH / 2, 8, WIDTH, 20).build());
			} else if (screen instanceof DisconnectedScreen) {
				Screens.getWidgets(screen).add(Button.builder(label, button -> Trips.goBack())
						.bounds(width / 2 - WIDTH / 2, height - 32, WIDTH, 20).build());
			}
		});
	}
}
