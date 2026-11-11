package dev.serverworks.machine;

import dev.serverworks.rack.PartKind;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** A rack block with a front face: it can point in any of the six directions. */
public class RackComponentBlock extends RackBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
	/** True while the rack's server is starting or running: fans spin and lights blink. */
	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

	public RackComponentBlock(PartKind kind, BlockBehaviour.Properties properties) {
		super(kind, properties);
	}

	@Override
	protected BlockState defaultRackState() {
		return super.defaultRackState().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
	}

	/** Vents puff a little warm air while the rack runs. */
	@Override
	public void animateTick(BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
			net.minecraft.util.RandomSource random) {
		if (kind() != dev.serverworks.rack.PartKind.VENT || !state.getValue(ACTIVE) || random.nextInt(4) != 0) {
			return;
		}
		Direction facing = state.getValue(FACING);
		double x = pos.getX() + 0.5 + facing.getStepX() * 0.55 + (random.nextDouble() - 0.5) * 0.5;
		double y = pos.getY() + 0.5 + facing.getStepY() * 0.55 + (random.nextDouble() - 0.5) * 0.5;
		double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.55 + (random.nextDouble() - 0.5) * 0.5;
		level.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_SMOKE, x, y, z,
				facing.getStepX() * 0.04, facing.getStepY() * 0.04 + 0.01, facing.getStepZ() * 0.04);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FACING, ACTIVE);
	}
}
