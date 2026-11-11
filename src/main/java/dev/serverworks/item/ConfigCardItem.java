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

/** Holds settings for a rack's server. Use it to edit them; put it in a Drive Bay to apply them. */
public final class ConfigCardItem extends Item {
	public ConfigCardItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			ClientHooks.editConfigCard.accept(hand);
		}
		return InteractionResult.SUCCESS;
	}

	public static ServerSettings settings(ItemStack stack) {
		ServerSettings settings = stack.get(ModComponents.SERVER_SETTINGS);
		return settings == null ? ServerSettings.DEFAULT : settings;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines,
			TooltipFlag flag) {
		ServerSettings s = settings(stack);
		lines.accept(Component.translatable("item.serverworks.config_card.summary",
				Component.translatable("options.difficulty." + s.difficulty().name().toLowerCase(Locale.ROOT)),
				Component.translatable("gameMode." + s.gameMode().name().toLowerCase(Locale.ROOT)))
				.withStyle(ChatFormatting.GRAY));
		if (s.hardcore()) {
			lines.accept(Component.translatable("item.serverworks.config_card.hardcore").withStyle(ChatFormatting.RED));
		}
		if (!s.motd().isEmpty()) {
			lines.accept(Component.literal("\"" + s.motd() + "\"").withStyle(ChatFormatting.DARK_GRAY));
		}
		lines.accept(Component.translatable("item.serverworks.config_card.use").withStyle(ChatFormatting.DARK_GRAY));
	}
}
