package dev.serverworks.net;

import dev.serverworks.Serverworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: how a {@link SelectProjectPayload} turned out. */
public record SelectResultPayload(int containerId, String projectId, Outcome outcome) implements CustomPacketPayload {
	public static final Type<SelectResultPayload> TYPE = new Type<>(Serverworks.id("select_result"));
	public static final StreamCodec<FriendlyByteBuf, SelectResultPayload> STREAM_CODEC =
			CustomPacketPayload.codec(SelectResultPayload::write, SelectResultPayload::new);

	private SelectResultPayload(FriendlyByteBuf in) {
		this(in.readVarInt(), in.readUtf(SelectProjectPayload.MAX_ID_LENGTH), in.readEnum(Outcome.class));
	}

	private void write(FriendlyByteBuf out) {
		out.writeVarInt(containerId);
		out.writeUtf(projectId, SelectProjectPayload.MAX_ID_LENGTH);
		out.writeEnum(outcome);
	}

	@Override
	public Type<SelectResultPayload> type() {
		return TYPE;
	}

	public enum Outcome {
		READY,
		NOT_FOUND,
		RATE_LIMITED,
		/** The server could not reach the catalog or got an unusable answer. */
		UNAVAILABLE,
		/** The request was refused, for example an id that is not a real project id. */
		REFUSED;

		public String translationKey() {
			return "serverworks.disk_writer.outcome." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}
}
