package dev.serverworks.net;

import dev.serverworks.Serverworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: a line typed into a rack's console. */
public record ConsoleCommandPayload(int containerId, String command) implements CustomPacketPayload {
	public static final int MAX_LENGTH = 256;
	public static final Type<ConsoleCommandPayload> TYPE = new Type<>(Serverworks.id("console_command"));
	public static final StreamCodec<FriendlyByteBuf, ConsoleCommandPayload> STREAM_CODEC =
			CustomPacketPayload.codec(ConsoleCommandPayload::write, ConsoleCommandPayload::new);

	private ConsoleCommandPayload(FriendlyByteBuf in) {
		this(in.readVarInt(), in.readUtf(MAX_LENGTH));
	}

	private void write(FriendlyByteBuf out) {
		out.writeVarInt(containerId);
		out.writeUtf(command, MAX_LENGTH);
	}

	@Override
	public Type<ConsoleCommandPayload> type() {
		return TYPE;
	}
}
