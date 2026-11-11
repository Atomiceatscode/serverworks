package dev.serverworks.machine;

import dev.serverworks.rack.PartKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/** The way in to a rack's server. Use it while the server runs to go there. */
public final class GatewayBlock extends RackComponentBlock {
	public static final EnumProperty<GatewayState> PORTAL = EnumProperty.create("portal", GatewayState.class);

	public GatewayBlock(BlockBehaviour.Properties properties) {
		super(PartKind.GATEWAY, properties);
	}

	@Override
	protected BlockState defaultRackState() {
		return super.defaultRackState().setValue(PORTAL, GatewayState.CLOSED);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (!state.getValue(FORMED)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			ServerCoreBlockEntity core = RackCores.owning(serverLevel, pos);
			if (core != null) {
				RackGateway.use(core, serverPlayer, pos);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(PORTAL) != GatewayState.OPEN || random.nextInt(3) != 0) {
			return;
		}
		var facing = state.getValue(FACING);
		double x = pos.getX() + 0.5 + facing.getStepX() * 0.55 + (random.nextDouble() - 0.5) * 0.6;
		double y = pos.getY() + 0.5 + facing.getStepY() * 0.55 + (random.nextDouble() - 0.5) * 0.6;
		double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.55 + (random.nextDouble() - 0.5) * 0.6;
		level.addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL, x, y, z,
				facing.getStepX() * 0.3, facing.getStepY() * 0.3, facing.getStepZ() * 0.3);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(PORTAL);
	}
}
