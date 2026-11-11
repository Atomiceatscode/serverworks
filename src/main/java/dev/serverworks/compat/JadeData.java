package dev.serverworks.compat;

import dev.serverworks.Serverworks;
import dev.serverworks.host.ServerInstance;
import dev.serverworks.machine.RackCores;
import dev.serverworks.machine.ServerCoreBlockEntity;
import dev.serverworks.rack.RackScan;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/** Sends the looked-at rack's server state to Jade's tooltip. Only used when Jade is installed. */
public final class JadeData implements IServerDataProvider<BlockAccessor> {
	public static final Identifier UID = Serverworks.id("rack");

	@Override
	public void appendServerData(CompoundTag data, BlockAccessor accessor) {
		if (!(accessor.getLevel() instanceof ServerLevel level)) {
			return;
		}
		ServerCoreBlockEntity core = accessor.getBlockEntity() instanceof ServerCoreBlockEntity here ? here
				: RackCores.owning(level, accessor.getPosition());
		if (core == null) {
			return;
		}
		RackScan scan = core.lastScan();
		if (scan == null || !scan.formed()) {
			return;
		}
		data.putString("platform", scan.structure().platform().id());
		ServerInstance instance = Serverworks.host().instances().peek(core.rackId());
		ServerInstance.Snapshot snapshot = instance == null ? ServerInstance.Snapshot.IDLE : instance.snapshot();
		data.putString("state", snapshot.state().name());
		data.putInt("progress", snapshot.progress());
		data.putInt("players", snapshot.playersOnline());
		data.putInt("max", scan.structure().maxPlayers());
		data.putBoolean("asleep", snapshot.hibernated());
	}

	@Override
	public Identifier getUid() {
		return UID;
	}
}
