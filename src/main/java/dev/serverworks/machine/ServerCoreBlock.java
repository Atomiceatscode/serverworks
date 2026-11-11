package dev.serverworks.machine;

import dev.serverworks.rack.PartKind;
import dev.serverworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** The rack's controller: it finds the structure around it and is where the rack is operated from. */
public final class ServerCoreBlock extends RackComponentBlock implements EntityBlock {
	public ServerCoreBlock(BlockBehaviour.Properties properties) {
		super(PartKind.CORE, properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ServerCoreBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (level.isClientSide() || type != ModBlockEntities.SERVER_CORE) {
			return null;
		}
		return (BlockEntityTicker<T>) (BlockEntityTicker<ServerCoreBlockEntity>) ServerCoreBlockEntity::serverTick;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block,
			net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
		if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof ServerCoreBlockEntity core) {
			core.neighborSignal(level.hasNeighborSignal(pos));
		}
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, net.minecraft.core.Direction direction) {
		return level.getBlockEntity(pos) instanceof ServerCoreBlockEntity core ? core.comparatorSignal() : 0;
	}

	/** The core always opens, formed or not, because it is where you learn what is missing. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof ServerCoreBlockEntity core) {
			player.openMenu(core);
		}
		return InteractionResult.SUCCESS;
	}
}
