package dev.serverworks.item;

import dev.serverworks.ClientHooks;
import dev.serverworks.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.function.Consumer;

/** A world of its own for any rack it is put in. Use it to name it, give it a seed, or copy a saved world. */
public final class WorldDriveItem extends Item {
	public WorldDriveItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			ClientHooks.editWorldDrive.accept(hand);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public Component getName(ItemStack stack) {
		WorldDrive drive = stack.get(ModComponents.WORLD_DRIVE);
		if (drive == null || drive.name().isEmpty()) {
			return super.getName(stack);
		}
		return Component.translatable("item.serverworks.world_drive.named", drive.name());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines,
			TooltipFlag flag) {
		WorldDrive drive = stack.get(ModComponents.WORLD_DRIVE);
		if (drive == null) {
			lines.accept(Component.translatable("item.serverworks.world_drive.blank").withStyle(ChatFormatting.GRAY));
			return;
		}
		lines.accept(Component.translatable("serverworks.world_drive.type." + drive.type().name().toLowerCase(Locale.ROOT))
				.withStyle(ChatFormatting.GRAY));
		if (!drive.seed().isEmpty()) {
			lines.accept(Component.translatable("item.serverworks.world_drive.seed", drive.seed()).withStyle(ChatFormatting.DARK_GRAY));
		}
		drive.importSave().ifPresent(save -> lines.accept(
				Component.translatable("item.serverworks.world_drive.copy", save).withStyle(ChatFormatting.DARK_GRAY)));
		lines.accept(Component.translatable("item.serverworks.world_drive.use").withStyle(ChatFormatting.DARK_GRAY));
	}
}
