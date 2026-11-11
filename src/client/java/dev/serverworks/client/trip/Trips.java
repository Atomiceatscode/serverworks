package dev.serverworks.client.trip;

import dev.serverworks.Serverworks;
import dev.serverworks.net.GatewayTripPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * Taking the player to a rack server and back.
 *
 * <p>The game leaves the world it is in (saving it, in singleplayer), connects to the rack
 * server, and remembers where it came from so the pause menu can offer the way back.
 */
public final class Trips {
	/**
	 * Where a trip started.
	 *
	 * @param levelId the singleplayer world's folder name, or null when it was a multiplayer server
	 * @param server  the multiplayer server, or null in singleplayer
	 */
	public record Origin(@Nullable String levelId, @Nullable ServerData server, String name) {
	}

	private static @Nullable Origin origin;
	private static @Nullable String destination;

	private Trips() {
	}

	public static @Nullable Origin origin() {
		return origin;
	}

	/** True while connected to the rack server a Gateway sent this player to. */
	public static boolean onTrip() {
		ServerData current = Minecraft.getInstance().getCurrentServer();
		return origin != null && current != null && current.ip.equals(destination);
	}

	public static void go(GatewayTripPayload trip) {
		if (trip.port() < 1 || trip.port() > 65535) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		String host;
		Origin from;
		IntegratedServer local = minecraft.getSingleplayerServer();
		if (local != null) {
			String levelId = local.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
			from = new Origin(levelId, null, local.getWorldData().getLevelName());
			// Rack servers in singleplayer only listen on this computer.
			host = "127.0.0.1";
		} else {
			ServerData current = minecraft.getCurrentServer();
			if (current == null) {
				return;
			}
			host = ServerAddress.parseString(current.ip).getHost();
			from = new Origin(null, current, current.name);
		}
		origin = from;
		destination = host + ":" + trip.port();
		ServerData target = new ServerData(trip.name(), destination, ServerData.Type.OTHER);
		Serverworks.LOGGER.info("Travelling from {} to the rack server at {}", from.name(), destination);
		minecraft.disconnectFromWorld(Component.translatable("serverworks.trip.leaving"));
		ConnectScreen.startConnecting(new TitleScreen(), minecraft, new ServerAddress(host, trip.port()), target, false, null);
	}

	/** Leaves the rack server and goes back to the world or server the trip started from. */
	public static void goBack() {
		Origin from = origin;
		if (from == null) {
			return;
		}
		origin = null;
		destination = null;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			minecraft.disconnectFromWorld(Component.translatable("serverworks.trip.returning"));
		}
		if (from.levelId() != null) {
			minecraft.createWorldOpenFlows().openWorld(from.levelId(), () -> minecraft.gui.setScreen(new TitleScreen()));
		} else if (from.server() != null) {
			ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(from.server().ip), from.server(),
					false, null);
		}
	}

	/** Called whenever the player enters a world; anywhere other than the destination ends the trip. */
	public static void onJoin() {
		if (origin != null && !onTrip()) {
			origin = null;
			destination = null;
		}
	}
}
