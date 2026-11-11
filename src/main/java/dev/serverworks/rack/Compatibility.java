package dev.serverworks.rack;

import dev.serverworks.catalog.model.SideSupport;
import dev.serverworks.item.DiskLabel;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether a player can step through a rack's Gateway with the game they already have.
 *
 * <p>Rack servers run the same Minecraft version as the world they are built in, so the
 * only thing that can stop a player is a disk whose project must also be installed on the
 * player's side.
 */
public final class Compatibility {
	private Compatibility() {
	}

	/** @param clientMods titles of the disks that players must install themselves; empty means anyone can join */
	public record Result(List<String> clientMods) {
		public Result {
			clientMods = List.copyOf(clientMods);
		}

		public boolean open() {
			return clientMods.isEmpty();
		}
	}

	public static Result check(List<DiskLabel> disks) {
		List<String> needed = new ArrayList<>();
		for (DiskLabel disk : disks) {
			if (disk.clientSide() == SideSupport.REQUIRED && !needed.contains(disk.title())) {
				needed.add(disk.title());
			}
		}
		return new Result(needed);
	}
}
