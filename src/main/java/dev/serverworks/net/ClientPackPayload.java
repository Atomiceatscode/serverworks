package dev.serverworks.net;

import dev.serverworks.Serverworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: a rack's client pack, for the player to save and import into a launcher. */
public record ClientPackPayload(String fileName, byte[] data) implements CustomPacketPayload {
	public static final int MAX_BYTES = 512 * 1024;
	public static final Type<ClientPackPayload> TYPE = new Type<>(Serverworks.id("client_pack"));
	public static final StreamCodec<FriendlyByteBuf, ClientPackPayload> STREAM_CODEC =
			CustomPacketPayload.codec(ClientPackPayload::write, ClientPackPayload::new);

	private ClientPackPayload(FriendlyByteBuf in) {
		this(in.readUtf(64), in.readByteArray(MAX_BYTES));
	}

	private void write(FriendlyByteBuf out) {
		out.writeUtf(fileName, 64);
		out.writeByteArray(data);
	}

	@Override
	public Type<ClientPackPayload> type() {
		return TYPE;
	}
}
