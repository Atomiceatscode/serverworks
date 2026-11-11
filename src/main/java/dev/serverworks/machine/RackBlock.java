package dev.serverworks.machine;

import dev.serverworks.rack.Cell;
import dev.serverworks.rack.PartKind;
import dev.serverworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Any block that can be part of a rack.
 *
 * <p>Rack blocks carry a {@code formed} state, switched on by the Server Core while they
 * belong to a valid rack. Placing or removing one asks nearby cores to look again.
 */
public class RackBlock extends Block {
	public static final BooleanProperty FORMED = BooleanProperty.create("formed");

	private final PartKind kind;

	public RackBlock(PartKind kind, BlockBehaviour.Properties properties) {
		super(properties);
		this.kind = kind;
		registerDefaultState(defaultRackState());
	}

	/** Subclasses with more properties extend this instead of calling registerDefaultState themselves. */
	protected BlockState defaultRackState() {
		return stateDefinition.any().setValue(FORMED, false);
	}

	public PartKind kind() {
		return kind;
	}

	/** How the rack rules see this block. */
	public Cell cell(BlockState state) {
		return Cell.part(kind);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		// A change of state on the same block (forming, rotating) does not alter the structure.
		if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
			RackCores.markDirtyNear(serverLevel, pos);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		RackCores.markDirtyNear(level, pos);
	}

	/** A held wrench always wins over opening a screen, so it works on every rack block. */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		return stack.is(ModItems.WRENCH) ? InteractionResult.PASS : InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/** Clicking any formed rack block opens the rack's Server Core. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (!state.getValue(FORMED)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel serverLevel) {
			ServerCoreBlockEntity core = RackCores.owning(serverLevel, pos);
			if (core != null) {
				player.openMenu(core);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FORMED);
	}
}
