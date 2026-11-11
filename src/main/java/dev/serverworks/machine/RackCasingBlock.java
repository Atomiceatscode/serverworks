package dev.serverworks.machine;

import dev.serverworks.rack.CasingTier;
import dev.serverworks.rack.Cell;
import dev.serverworks.rack.PartKind;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** The frame of a rack. Its tier caps what the rack can do; formed, it wears the platform's colour. */
public final class RackCasingBlock extends RackBlock {
	public static final EnumProperty<RackAccent> ACCENT = EnumProperty.create("accent", RackAccent.class);

	private final CasingTier tier;

	public RackCasingBlock(CasingTier tier, BlockBehaviour.Properties properties) {
		super(PartKind.CASING, properties);
		this.tier = tier;
	}

	@Override
	protected BlockState defaultRackState() {
		return super.defaultRackState().setValue(ACCENT, RackAccent.NONE);
	}

	public CasingTier tier() {
		return tier;
	}

	@Override
	public Cell cell(BlockState state) {
		return Cell.casing(tier);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(ACCENT);
	}
}
