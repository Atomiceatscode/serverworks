package dev.serverworks.machine;

import dev.serverworks.rack.PartKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Holds up to four RAM sticks for its rack's server. Its front lights one module per stick. */
public final class MemoryBankBlock extends RackComponentBlock implements EntityBlock {
	public static final IntegerProperty STICKS = IntegerProperty.create("sticks", 0, MemoryBankBlockEntity.SLOTS);

	public MemoryBankBlock(BlockBehaviour.Properties properties) {
		super(PartKind.MEMORY_BANK, properties);
	}

	@Override
	protected BlockState defaultRackState() {
		return super.defaultRackState().setValue(STICKS, 0);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MemoryBankBlockEntity(pos, state);
	}

	/** A Memory Bank opens its own stick slots whether or not the rack is formed. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof MemoryBankBlockEntity bank) {
			player.openMenu(bank);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		Containers.updateNeighboursAfterDestroy(state, level, pos);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(STICKS);
	}
}
