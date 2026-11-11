package dev.serverworks.machine;

import dev.serverworks.rack.PartKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** A screen on the outside of a rack showing its server's state, players and address. */
public final class StatusDisplayBlock extends RackComponentBlock implements EntityBlock {
	public StatusDisplayBlock(BlockBehaviour.Properties properties) {
		super(PartKind.DISPLAY, properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new StatusDisplayBlockEntity(pos, state);
	}
}
