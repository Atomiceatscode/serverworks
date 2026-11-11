package dev.serverworks.net;

import dev.serverworks.Serverworks;
import dev.serverworks.host.ConsoleBuffer;
import dev.serverworks.host.ServerInstance.Failure;
import dev.serverworks.host.ServerInstance.State;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: what a rack's server is doing, and the console lines the screen has
 * not seen yet.
 *
 * @param notice       a translation key for the last refused action, or empty
 * @param consoleFrom  the sequence number of the first line in {@code consoleLines}
 */
public record InstanceStatusPayload(
		int containerId,
		State state,
		int progress,
		Failure failure,
		String detail,
		int port,
		int playersOnline,
		boolean hibernated,
		boolean packAvailable,
		boolean eulaAccepted,
		boolean canControl,
		String notice,
		long consoleFrom,
		List<String> consoleLines) implements CustomPacketPayload {

	public static final int MAX_LINES = 200;
	private static final int MAX_DETAIL = 300;
	private static final int MAX_NOTICE = 100;

	public static final Type<InstanceStatusPayload> TYPE = new Type<>(Serverworks.id("instance_status"));
	public static final StreamCodec<FriendlyByteBuf, InstanceStatusPayload> STREAM_CODEC =
			CustomPacketPayload.codec(InstanceStatusPayload::write, InstanceStatusPayload::new);

	public InstanceStatusPayload {
		detail = detail.length() > MAX_DETAIL ? detail.substring(0, MAX_DETAIL) : detail;
		consoleLines = List.copyOf(consoleLines);
	}

	private InstanceStatusPayload(FriendlyByteBuf in) {
		this(in.readVarInt(), in.readEnum(State.class), in.readVarInt(), in.readEnum(Failure.class), in.readUtf(MAX_DETAIL),
				in.readVarInt(), in.readVarInt(), in.readBoolean(), in.readBoolean(), in.readBoolean(), in.readBoolean(),
				in.readUtf(MAX_NOTICE), in.readVarLong(), readLines(in));
	}

	private static List<String> readLines(FriendlyByteBuf in) {
		int count = in.readVarInt();
		if (count < 0 || count > MAX_LINES) {
			throw new IllegalArgumentException("Too many console lines: " + count);
		}
		List<String> lines = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			lines.add(in.readUtf(ConsoleBuffer.MAX_LINE_LENGTH * 3));
		}
		return lines;
	}

	private void write(FriendlyByteBuf out) {
		out.writeVarInt(containerId);
		out.writeEnum(state);
		out.writeVarInt(progress);
		out.writeEnum(failure);
		out.writeUtf(detail, MAX_DETAIL);
		out.writeVarInt(port);
		out.writeVarInt(playersOnline);
		out.writeBoolean(hibernated);
		out.writeBoolean(packAvailable);
		out.writeBoolean(eulaAccepted);
		out.writeBoolean(canControl);
		out.writeUtf(notice, MAX_NOTICE);
		out.writeVarLong(consoleFrom);
		out.writeVarInt(consoleLines.size());
		for (String line : consoleLines) {
			out.writeUtf(line, ConsoleBuffer.MAX_LINE_LENGTH * 3);
		}
	}

	@Override
	public Type<InstanceStatusPayload> type() {
		return TYPE;
	}
}
