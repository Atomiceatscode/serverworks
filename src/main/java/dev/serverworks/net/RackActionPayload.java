package dev.serverworks.net;

import dev.serverworks.Serverworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: a button on the Server Core screen was pressed. */
public record RackActionPayload(int containerId, Action action) implements CustomPacketPayload {
	public static final Type<RackActionPayload> TYPE = new Type<>(Serverworks.id("rack_action"));
	public static final StreamCodec<FriendlyByteBuf, RackActionPayload> STREAM_CODEC =
			CustomPacketPayload.codec(RackActionPayload::write, RackActionPayload::new);

	private RackActionPayload(FriendlyByteBuf in) {
		this(in.readVarInt(), in.readEnum(Action.class));
	}

	private void write(FriendlyByteBuf out) {
		out.writeVarInt(containerId);
		out.writeEnum(action);
	}

	@Override
	public Type<RackActionPayload> type() {
		return TYPE;
	}

	public enum Action {
		START,
		STOP,
		ACCEPT_EULA,
		EXPORT_PACK
	}
}
