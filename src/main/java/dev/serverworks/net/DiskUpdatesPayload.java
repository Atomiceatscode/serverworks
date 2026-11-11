package dev.serverworks.net;

import dev.serverworks.Serverworks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Server to client: which disks in the rack have a newer version than the one in use. */
public record DiskUpdatesPayload(int containerId, List<Update> updates) implements CustomPacketPayload {
	public static final Type<DiskUpdatesPayload> TYPE = new Type<>(Serverworks.id("disk_updates"));
	public static final StreamCodec<FriendlyByteBuf, DiskUpdatesPayload> STREAM_CODEC =
			CustomPacketPayload.codec(DiskUpdatesPayload::write, DiskUpdatesPayload::new);
	private static final int MAX_UPDATES = 2048;
	private static final int MAX_TEXT = 128;

	/**
	 * @param from   the version in use: the pinned one, or the one last installed
	 * @param to     the newest compatible version's name
	 * @param pinned true when the disk is pinned, so updating rewrites it
	 */
	public record Update(String projectId, String from, String to, boolean pinned) {
	}

	public DiskUpdatesPayload {
		updates = List.copyOf(updates);
	}

	private DiskUpdatesPayload(FriendlyByteBuf in) {
		this(in.readVarInt(), readUpdates(in));
	}

	private static List<Update> readUpdates(FriendlyByteBuf in) {
		int count = in.readVarInt();
		if (count < 0 || count > MAX_UPDATES) {
			throw new IllegalArgumentException("Unreasonable update count " + count);
		}
		List<Update> updates = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			updates.add(new Update(in.readUtf(SelectProjectPayload.MAX_ID_LENGTH), in.readUtf(MAX_TEXT), in.readUtf(MAX_TEXT),
					in.readBoolean()));
		}
		return updates;
	}

	private void write(FriendlyByteBuf out) {
		out.writeVarInt(containerId);
		out.writeVarInt(updates.size());
		for (Update update : updates) {
			out.writeUtf(update.projectId(), SelectProjectPayload.MAX_ID_LENGTH);
			out.writeUtf(clip(update.from()), MAX_TEXT);
			out.writeUtf(clip(update.to()), MAX_TEXT);
			out.writeBoolean(update.pinned());
		}
	}

	private static String clip(String text) {
		return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
	}

	@Override
	public Type<DiskUpdatesPayload> type() {
		return TYPE;
	}
}
