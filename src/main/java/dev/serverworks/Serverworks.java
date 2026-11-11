package dev.serverworks;

import dev.serverworks.catalog.ModrinthClient;
import dev.serverworks.catalog.http.JdkHttpTransport;
import dev.serverworks.host.HostRuntime;
import dev.serverworks.net.ServerworksNetworking;
import dev.serverworks.registry.ModBlockEntities;
import dev.serverworks.registry.ModBlocks;
import dev.serverworks.registry.ModComponents;
import dev.serverworks.registry.ModItems;
import dev.serverworks.registry.ModMenus;
import dev.serverworks.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Serverworks implements ModInitializer {
	public static final String MOD_ID = "serverworks";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static volatile ModrinthClient catalog;
	private static volatile HostRuntime host;

	@Override
	public void onInitialize() {
		ModComponents.init();
		ModSounds.init();
		ModBlocks.init();
		ModItems.init();
		ModBlockEntities.init();
		ModMenus.init();
		ServerworksNetworking.init();
		dev.serverworks.machine.WrenchPreviews.register();
		// A dedicated server takes its rack servers down with it. In singleplayer they are left
		// running, because a player who walks through a Gateway leaves the world for the child.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (server.isDedicatedServer() && host != null) {
				host.instances().killAll();
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register(
				(handler, server) -> ServerworksNetworking.forget(handler.getPlayer().getUUID()));
		LOGGER.info("Serverworks initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/** The shared catalog client. Created on first use so that merely loading the mod makes no connections. */
	public static ModrinthClient catalog() {
		ModrinthClient client = catalog;
		if (client == null) {
			synchronized (Serverworks.class) {
				client = catalog;
				if (client == null) {
					client = new ModrinthClient(new JdkHttpTransport(), userAgent());
					catalog = client;
				}
			}
		}
		return client;
	}

	/** The server runtime. Created on first use so that merely loading the mod touches no files. */
	public static HostRuntime host() {
		HostRuntime runtime = host;
		if (runtime == null) {
			synchronized (Serverworks.class) {
				runtime = host;
				if (runtime == null) {
					runtime = new HostRuntime(FabricLoader.getInstance().getGameDir().resolve(MOD_ID), userAgent());
					host = runtime;
				}
			}
		}
		return runtime;
	}

	public static String userAgent() {
		String version = FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
		return "serverworks/" + version + " (Minecraft mod)";
	}
}
