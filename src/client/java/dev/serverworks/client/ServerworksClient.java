package dev.serverworks.client;

import dev.serverworks.Serverworks;
import dev.serverworks.client.render.DiskLabelRenderer;
import dev.serverworks.client.render.StatusDisplayRenderer;
import dev.serverworks.client.trip.TripButtons;
import dev.serverworks.client.trip.Trips;
import dev.serverworks.client.screen.DiskWriterScreen;
import dev.serverworks.client.screen.DriveBayScreen;
import dev.serverworks.client.screen.ServerCoreScreen;
import dev.serverworks.net.ClientPackPayload;
import dev.serverworks.net.GatewayTripPayload;
import dev.serverworks.net.InstanceStatusPayload;
import dev.serverworks.net.RackStatusPayload;
import dev.serverworks.net.SelectResultPayload;
import dev.serverworks.registry.ModBlockEntities;
import dev.serverworks.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.special.SpecialModelRenderers;

public final class ServerworksClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SpecialModelRenderers.ID_MAPPER.put(Serverworks.id("disk_label"), DiskLabelRenderer.Unbaked.MAP_CODEC);
		MenuScreens.register(ModMenus.DISK_WRITER, DiskWriterScreen::new);
		MenuScreens.register(ModMenus.DRIVE_BAY, DriveBayScreen::new);
		MenuScreens.register(ModMenus.SERVER_CORE, ServerCoreScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(RackStatusPayload.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof ServerCoreScreen screen
					&& screen.getMenu().containerId == payload.containerId()) {
				screen.setStatus(payload.status());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(InstanceStatusPayload.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof ServerCoreScreen screen
					&& screen.getMenu().containerId == payload.containerId()) {
				screen.setInstance(payload);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(SelectResultPayload.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof DiskWriterScreen screen) {
				screen.onSelectResult(payload);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(GatewayTripPayload.TYPE, (payload, context) -> Trips.go(payload));
		ClientPlayNetworking.registerGlobalReceiver(ClientPackPayload.TYPE, (payload, context) -> ClientPacks.save(payload));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
			Trips.onJoin();
			dev.serverworks.client.trip.ReturnKey.announceArrival(client);
		}));
		TripButtons.register();
		dev.serverworks.client.trip.ReturnKey.register();
		dev.serverworks.ClientHooks.openManual = () -> net.minecraft.client.Minecraft.getInstance().gui
				.setScreen(new dev.serverworks.client.screen.ManualScreen());
		dev.serverworks.client.sound.RackHum.register();
		BlockEntityRenderers.register(ModBlockEntities.STATUS_DISPLAY, StatusDisplayRenderer::new);
		Serverworks.LOGGER.info("Serverworks client initialized");
	}
}
