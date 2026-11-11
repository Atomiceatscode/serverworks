package dev.serverworks.net;

import dev.serverworks.Serverworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: put this backup's world back on the rack whose Server Core is open. */
public record RestoreBackupPayload(int containerId, String name) implements CustomPacketPayload {
	public static final Type<RestoreBackupPayload> TYPE = new Type<>(Serverworks.id("restore_backup"));
	public static final StreamCodec<FriendlyByteBuf, RestoreBackupPayload> STREAM_CODEC =
			CustomPacketPayload.codec(RestoreBackupPayload::write, RestoreBackupPayload::new);

	private RestoreBackupPayload(FriendlyByteBuf in) {
		this(in.readVarInt(), in.readUtf(BackupsPayload.MAX_NAME));
	}

	private void write(FriendlyByteBuf out) {
		out.writeVarInt(containerId);
		out.writeUtf(name, BackupsPayload.MAX_NAME);
	}

	@Override
	public Type<RestoreBackupPayload> type() {
		return TYPE;
	}
}
