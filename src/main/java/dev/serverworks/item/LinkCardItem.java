package dev.serverworks.item;

import dev.serverworks.host.Forwarding;
import dev.serverworks.machine.RackCores;
import dev.serverworks.machine.ServerCoreBlockEntity;
import dev.serverworks.rack.Platform;
import dev.serverworks.rack.RackScan;
import dev.serverworks.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.util.Prediction;

import java.util.function.Consumer;

/**
 * Connects a proxy rack to the racks behind it. Sneak-use a card on a server rack to point
 * the card at it, then put the card in the proxy rack's Drive Bay.
 */
public final class LinkCardItem extends Item {
	public LinkCardItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		Player player = context.getPlayer();
		ServerCoreBlockEntity core = level.getBlockEntity(context.getClickedPos()) instanceof ServerCoreBlockEntity here
				? here : RackCores.owning(level, context.getClickedPos());
		if (core == null) {
			return InteractionResult.PASS;
		}
		RackScan scan = core.lastScan();
		if (scan == null || !scan.formed()) {
			message(player, Component.translatable("serverworks.link.not_formed").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		Platform platform = scan.structure().platform();
		if (!Forwarding.canBeBackend(platform)) {
			message(player, Component.translatable("serverworks.link.proxy").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		LinkTarget target = new LinkTarget(core.rackId(), core.getBlockPos(), level.dimension().identifier().toString(),
				platform.id());
		ItemStack held = context.getItemInHand();
		if (held.getCount() > 1 && player != null) {
			ItemStack linked = held.split(1);
			linked.set(ModComponents.LINK_TARGET, target);
			player.getInventory().placeItemBackInInventory(linked, Prediction.SERVER_ONLY);
		} else {
			held.set(ModComponents.LINK_TARGET, target);
		}
		level.playSound(null, context.getClickedPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.4F);
		message(player, Component.translatable("serverworks.link.linked", Component.translatable(platform.translationKey()),
				core.getBlockPos().getX(), core.getBlockPos().getY(), core.getBlockPos().getZ()).withStyle(ChatFormatting.GREEN));
		return InteractionResult.SUCCESS;
	}

	@Override
	public Component getName(ItemStack stack) {
		LinkTarget target = stack.get(ModComponents.LINK_TARGET);
		if (target == null) {
			return super.getName(stack);
		}
		Platform platform = Platform.byId(target.platform());
		return Component.translatable("item.serverworks.link_card.linked",
				platform == null ? Component.literal(target.platform()) : Component.translatable(platform.translationKey()));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines,
			TooltipFlag flag) {
		LinkTarget target = stack.get(ModComponents.LINK_TARGET);
		if (target == null) {
			lines.accept(Component.translatable("item.serverworks.link_card.blank").withStyle(ChatFormatting.GRAY));
			return;
		}
		lines.accept(Component.translatable("item.serverworks.link_card.at", target.core().getX(), target.core().getY(),
				target.core().getZ()).withStyle(ChatFormatting.GRAY));
		lines.accept(Component.translatable("item.serverworks.link_card.use").withStyle(ChatFormatting.DARK_GRAY));
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return stack.has(ModComponents.LINK_TARGET);
	}

	private static void message(Player player, Component text) {
		if (player != null) {
			player.sendOverlayMessage(text);
		}
	}
}
