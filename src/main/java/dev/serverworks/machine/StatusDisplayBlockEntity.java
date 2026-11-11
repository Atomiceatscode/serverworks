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

/** The three lines a Status Display shows. The rack's core writes them; clients draw them. */
public final class StatusDisplayBlockEntity extends BlockEntity {
	public static final int LINES = 3;

	private List<DisplayLine> lines = List.of();

	public StatusDisplayBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.STATUS_DISPLAY, pos, state);
	}

	public List<DisplayLine> lines() {
		return lines;
	}

	/** Server side: changes what is shown, sending it to nearby players only if it differs. */
	public void show(List<DisplayLine> next) {
		List<DisplayLine> copy = List.copyOf(next.subList(0, Math.min(LINES, next.size())));
		if (copy.equals(lines) || level == null) {
			return;
		}
		lines = copy;
		setChanged();
		level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("lines", DisplayLine.CODEC.listOf(0, LINES), lines);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		lines = input.read("lines", DisplayLine.CODEC.listOf(0, LINES)).orElse(List.of());
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
