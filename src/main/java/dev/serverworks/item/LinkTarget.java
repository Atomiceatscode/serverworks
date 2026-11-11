package dev.serverworks.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * What a Link Card points at: another rack, which a proxy rack holding the card sends players to.
 *
 * @param platform the linked rack's platform id when the card was made, for the tooltip
 */
public record LinkTarget(UUID rack, BlockPos core, String dimension, String platform) {
	public static final Codec<LinkTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("rack").forGetter(LinkTarget::rack),
			BlockPos.CODEC.fieldOf("core").forGetter(LinkTarget::core),
			Codec.string(1, 128).fieldOf("dimension").forGetter(LinkTarget::dimension),
			Codec.string(1, 32).fieldOf("platform").forGetter(LinkTarget::platform)
	).apply(i, LinkTarget::new));

	public static final StreamCodec<ByteBuf, LinkTarget> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
}
