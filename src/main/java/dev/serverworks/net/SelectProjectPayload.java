package dev.serverworks.net;

import dev.serverworks.Serverworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: "write this project onto the disk in the Disk Writer I have open".
 * An empty {@code versionId} means the newest compatible version; anything else pins the disk to it.
 */
public record SelectProjectPayload(int containerId, String projectId, String versionId) implements CustomPacketPayload {
	public static final int MAX_ID_LENGTH = 64;
	public static final Type<SelectProjectPayload> TYPE = new Type<>(Serverworks.id("select_project"));
	public static final StreamCodec<FriendlyByteBuf, SelectProjectPayload> STREAM_CODEC =
			CustomPacketPayload.codec(SelectProjectPayload::write, SelectProjectPayload::new);

	private SelectProjectPayload(FriendlyByteBuf in) {
		this(in.readVarInt(), in.readUtf(MAX_ID_LENGTH), in.readUtf(MAX_ID_LENGTH));
	}

	private void write(FriendlyByteBuf out) {
		out.writeVarInt(containerId);
		out.writeUtf(projectId, MAX_ID_LENGTH);
		out.writeUtf(versionId, MAX_ID_LENGTH);
	}

	@Override
	public Type<SelectProjectPayload> type() {
		return TYPE;
	}
}
