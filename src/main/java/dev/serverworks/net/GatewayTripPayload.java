package dev.serverworks.net;

import dev.serverworks.Serverworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: "go to the rack server on this port". The client connects by itself,
 * to the same address it reached this world on, because a world in singleplayer cannot
 * hand its player to another server.
 *
 * @param name   what to call the destination on screen
 * @param ticket in one-port mode, the ticket to show the main server, which then passes the
 *               connection to the rack; empty when the client goes to the port itself
 */
public record GatewayTripPayload(int port, String name, String ticket) implements CustomPacketPayload {
	public GatewayTripPayload(int port, String name) {
		this(port, name, "");
	}

	public static final Type<GatewayTripPayload> TYPE = new Type<>(Serverworks.id("gateway_trip"));
	public static final StreamCodec<FriendlyByteBuf, GatewayTripPayload> STREAM_CODEC =
			CustomPacketPayload.codec(GatewayTripPayload::write, GatewayTripPayload::new);

	private GatewayTripPayload(FriendlyByteBuf in) {
		this(in.readVarInt(), in.readUtf(64), in.readUtf(64));
	}

	private void write(FriendlyByteBuf out) {
		out.writeVarInt(port);
		out.writeUtf(name, 64);
		out.writeUtf(ticket, 64);
	}

	@Override
	public Type<GatewayTripPayload> type() {
		return TYPE;
	}
}
