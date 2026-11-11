package dev.serverworks.client;

import com.mojang.blaze3d.Blaze3D;
import dev.serverworks.Serverworks;
import dev.serverworks.client.screen.ServerCoreScreen;
import dev.serverworks.net.ClientPackPayload;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saves a rack's client pack where the player can find it, and shows them the folder. */
public final class ClientPacks {
	private ClientPacks() {
	}

	public static Path folder() {
		return FabricLoader.getInstance().getGameDir().resolve(Serverworks.MOD_ID).resolve("client-packs");
	}

	public static void save(ClientPackPayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		String name = payload.fileName().replaceAll("[^A-Za-z0-9._-]", "_");
		if (!name.endsWith(".mrpack")) {
			name += ".mrpack";
		}
		Path target = folder().resolve(name);
		Component message;
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, payload.data());
			message = Component.translatable("serverworks.pack.saved", name);
			Blaze3D.openPath(target.getParent());
		} catch (IOException e) {
			Serverworks.LOGGER.error("Could not save client pack", e);
			message = Component.translatable("serverworks.pack.failed");
		}
		if (minecraft.gui.screen() instanceof ServerCoreScreen screen) {
			screen.showNotice(message);
		}
	}
}
