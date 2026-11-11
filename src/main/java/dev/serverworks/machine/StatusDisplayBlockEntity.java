package dev.serverworks.machine;

import dev.serverworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;

/**
 * What a Status Display shows: up to five lines and the heads of up to six players. The
 * rack's core writes them; clients draw them.
 */
public final class StatusDisplayBlockEntity extends BlockEntity {
	public static final int LINES = 5;
	public static final int HEADS = 6;

	/** A player shown by their head. */
	public record Head(String name, java.util.UUID id) {
		public static final com.mojang.serialization.Codec<Head> CODEC =
				com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
						com.mojang.serialization.Codec.string(1, 16).fieldOf("name").forGetter(Head::name),
						net.minecraft.core.UUIDUtil.CODEC.fieldOf("id").forGetter(Head::id)
				).apply(i, Head::new));
	}

	private List<DisplayLine> lines = List.of();
	private List<Head> heads = List.of();

	public List<Head> heads() {
		return heads;
	}

	public StatusDisplayBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.STATUS_DISPLAY, pos, state);
	}

	public List<DisplayLine> lines() {
		return lines;
	}

	/** Server side: changes what is shown, sending it to nearby players only if it differs. */
	public void show(List<DisplayLine> next) {
		show(next, List.of());
	}

	public void show(List<DisplayLine> next, List<Head> nextHeads) {
		List<DisplayLine> copy = List.copyOf(next.subList(0, Math.min(LINES, next.size())));
		List<Head> headCopy = List.copyOf(nextHeads.subList(0, Math.min(HEADS, nextHeads.size())));
		if ((copy.equals(lines) && headCopy.equals(heads)) || level == null) {
			return;
		}
		lines = copy;
		heads = headCopy;
		setChanged();
		level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("lines", DisplayLine.CODEC.listOf(0, LINES), lines);
		output.store("heads", Head.CODEC.listOf(0, HEADS), heads);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		lines = input.read("lines", DisplayLine.CODEC.listOf(0, LINES)).orElse(List.of());
		heads = input.read("heads", Head.CODEC.listOf(0, HEADS)).orElse(List.of());
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}
}
