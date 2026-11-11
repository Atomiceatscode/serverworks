package dev.serverworks.machine;

import dev.serverworks.rack.PartKind;
import dev.serverworks.rack.RackStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Holds the Mod Disks a rack will load. Its front shows how many slots are filled. */
public final class DriveBayBlock extends RackComponentBlock implements EntityBlock {
	public static final IntegerProperty DISKS = IntegerProperty.create("disks", 0, RackStructure.DISKS_PER_BAY);

	public DriveBayBlock(BlockBehaviour.Properties properties) {
		super(PartKind.DRIVE_BAY, properties);
	}

	@Override
	protected BlockState defaultRackState() {
		return super.defaultRackState().setValue(DISKS, 0);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DriveBayBlockEntity(pos, state);
	}

	/** A drive bay opens its own disk slots whether or not the rack is formed. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof DriveBayBlockEntity bay) {
			player.openMenu(bay);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		Containers.updateNeighboursAfterDestroy(state, level, pos);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(DISKS);
	}
}
