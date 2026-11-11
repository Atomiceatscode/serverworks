package dev.serverworks.net;

import dev.serverworks.Serverworks;
import dev.serverworks.machine.RackStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the current state of the rack whose Server Core screen is open. */
public record RackStatusPayload(int containerId, RackStatus status) implements CustomPacketPayload {
	public static final Type<RackStatusPayload> TYPE = new Type<>(Serverworks.id("rack_status"));
	public static final StreamCodec<FriendlyByteBuf, RackStatusPayload> STREAM_CODEC =
			CustomPacketPayload.codec(RackStatusPayload::write, RackStatusPayload::new);

	private RackStatusPayload(FriendlyByteBuf in) {
		this(in.readVarInt(), RackStatus.read(in));
	}

	private void write(FriendlyByteBuf out) {
		out.writeVarInt(containerId);
		status.write(out);
	}

	@Override
	public Type<RackStatusPayload> type() {
		return TYPE;
	}
}
