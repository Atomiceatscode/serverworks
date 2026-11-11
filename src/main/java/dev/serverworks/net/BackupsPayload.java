package dev.serverworks.net;

import dev.serverworks.Serverworks;
import dev.serverworks.host.Backups;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Server to client: the rack's backups, newest first, and whether one is being made. */
public record BackupsPayload(int containerId, List<Backups.Backup> backups, boolean busy) implements CustomPacketPayload {
	public static final Type<BackupsPayload> TYPE = new Type<>(Serverworks.id("backups"));
	public static final StreamCodec<FriendlyByteBuf, BackupsPayload> STREAM_CODEC =
			CustomPacketPayload.codec(BackupsPayload::write, BackupsPayload::new);
	public static final int MAX_SHOWN = 64;
	static final int MAX_NAME = 64;

	public BackupsPayload {
		backups = List.copyOf(backups.subList(0, Math.min(MAX_SHOWN, backups.size())));
	}

	private BackupsPayload(FriendlyByteBuf in) {
		this(in.readVarInt(), read(in), in.readBoolean());
	}

	private static List<Backups.Backup> read(FriendlyByteBuf in) {
		int count = in.readVarInt();
		if (count < 0 || count > MAX_SHOWN) {
			throw new IllegalArgumentException("Unreasonable backup count " + count);
		}
		List<Backups.Backup> out = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			out.add(new Backups.Backup(in.readUtf(MAX_NAME), in.readVarLong(), in.readVarLong()));
		}
		return out;
	}

	private void write(FriendlyByteBuf out) {
		out.writeVarInt(containerId);
		out.writeVarInt(backups.size());
		for (Backups.Backup backup : backups) {
			out.writeUtf(backup.name(), MAX_NAME);
			out.writeVarLong(backup.size());
			out.writeVarLong(backup.modified());
		}
		out.writeBoolean(busy);
	}

	@Override
	public Type<BackupsPayload> type() {
		return TYPE;
	}
}
