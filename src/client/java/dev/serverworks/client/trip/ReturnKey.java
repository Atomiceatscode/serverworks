package dev.serverworks.client.trip;

import com.mojang.blaze3d.platform.InputConstants;
import dev.serverworks.Serverworks;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The "Return from rack server" key (B by default, changeable in Controls), and the message
 * shown on arrival that says how to get back.
 */
public final class ReturnKey {
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Serverworks.MOD_ID, "serverworks"));
	public static final KeyMapping KEY = new KeyMapping("key.serverworks.return", InputConstants.KEY_B, CATEGORY);

	private ReturnKey() {
	}

	public static void register() {
		KeyMappingHelper.registerKeyMapping(KEY);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (KEY.consumeClick()) {
				if (client.gui.screen() == null && Trips.onTrip()) {
					Trips.goBack();
					return;
				}
			}
		});
	}

	/** Tells a player who just arrived through a Gateway how to go back. */
	public static void announceArrival(Minecraft client) {
		Trips.Origin origin = Trips.origin();
		if (origin == null || client.player == null || !Trips.onTrip()) {
			return;
		}
		Component key = KEY.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.AQUA);
		client.player.sendSystemMessage(Component.translatable("serverworks.trip.arrived",
				Component.literal(origin.name()).withStyle(ChatFormatting.AQUA), key).withStyle(ChatFormatting.GRAY));
	}
}
