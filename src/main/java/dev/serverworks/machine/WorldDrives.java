package dev.serverworks.machine;

import dev.serverworks.host.WorldPlan;
import dev.serverworks.item.WorldDrive;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

/** Turns World Drives into the world a rack's server plays, and guards copying saved worlds. */
public final class WorldDrives {
	private WorldDrives() {
	}

	/**
	 * Only the owner of a singleplayer game may copy one of its saves onto a drive: the saves
	 * are on their computer. Dedicated servers have no saves to offer.
	 */
	public static boolean canImport(ServerPlayer player, String saveName) {
		MinecraftServer server = player.level().getServer();
		return !server.isDedicatedServer() && server.isSingleplayerOwner(player.nameAndId())
				&& WorldDrive.safeSaveName(saveName) && savePath(server, saveName) != null;
	}

	/** The folder of a singleplayer save next to the world being played, if it exists. */
	static @Nullable Path savePath(MinecraftServer server, String saveName) {
		if (server.isDedicatedServer() || !WorldDrive.safeSaveName(saveName)) {
			return null;
		}
		Path current = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
		Path saves = current.getParent();
		if (saves == null) {
			return null;
		}
		Path save = saves.resolve(saveName).normalize();
		if (!save.startsWith(saves) || save.equals(current) || !Files.isRegularFile(save.resolve("level.dat"))) {
			return null;
		}
		return save;
	}

	/** The world a rack plays with this drive, or the default world without one. */
	static WorldPlan plan(MinecraftServer server, @Nullable WorldDrive drive) {
		if (drive == null) {
			return WorldPlan.DEFAULT;
		}
		Path importFrom = drive.importSave().map(name -> savePath(server, name)).orElse(null);
		return new WorldPlan(drive.levelName(), drive.seed(), drive.type().levelType(), importFrom);
	}
}
