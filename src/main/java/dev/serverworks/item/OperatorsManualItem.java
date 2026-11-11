package dev.serverworks.item;

import dev.serverworks.ClientHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** The in-game guide to Serverworks. */
public final class OperatorsManualItem extends Item {
	public OperatorsManualItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			ClientHooks.openManual.run();
		}
		return InteractionResult.SUCCESS;
	}
}
