package dev.serverworks.net;

import dev.serverworks.Serverworks;
import dev.serverworks.item.ServerSettings;
import dev.serverworks.item.WorldDrive;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import org.jspecify.annotations.Nullable;

/**
 * Client to server: new contents for the Config Card or World Drive in a hand. Exactly one
 * of the two is set. The server checks the hand really holds that item before writing.
 */
public record EditItemPayload(InteractionHand hand, @Nullable ServerSettings settings, @Nullable WorldDrive drive)
		implements CustomPacketPayload {
	public static final Type<EditItemPayload> TYPE = new Type<>(Serverworks.id("edit_item"));
	public static final StreamCodec<FriendlyByteBuf, EditItemPayload> STREAM_CODEC =
			CustomPacketPayload.codec(EditItemPayload::write, EditItemPayload::new);

	private EditItemPayload(FriendlyByteBuf in) {
		this(in.readEnum(InteractionHand.class),
				in.readBoolean() ? ServerSettings.STREAM_CODEC.decode(in) : null,
				in.readBoolean() ? WorldDrive.STREAM_CODEC.decode(in) : null);
	}

	private void write(FriendlyByteBuf out) {
		out.writeEnum(hand);
		out.writeBoolean(settings != null);
		if (settings != null) {
			ServerSettings.STREAM_CODEC.encode(out, settings);
		}
		out.writeBoolean(drive != null);
		if (drive != null) {
			WorldDrive.STREAM_CODEC.encode(out, drive);
		}
	}

	@Override
	public Type<EditItemPayload> type() {
		return TYPE;
	}
}
