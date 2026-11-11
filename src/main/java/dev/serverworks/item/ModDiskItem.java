package dev.serverworks.item;

import dev.serverworks.catalog.model.ProjectType;
import dev.serverworks.catalog.model.SideSupport;
import dev.serverworks.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.Locale;
import java.util.function.Consumer;

/** A disk with one catalog project written on it. Its name and look come from its {@link DiskLabel}. */
public final class ModDiskItem extends Item {
	private static final int MAX_LOADERS_SHOWN = 6;

	public ModDiskItem(Properties properties) {
		super(properties);
	}

	@Override
	public Component getName(ItemStack stack) {
		DiskLabel label = stack.get(ModComponents.DISK_LABEL);
		if (label == null || label.title().isEmpty()) {
			return super.getName(stack);
		}
		return Component.literal(label.title());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> lines, TooltipFlag flag) {
		DiskLabel label = stack.get(ModComponents.DISK_LABEL);
		if (label == null) {
			lines.accept(Component.translatable("item.serverworks.mod_disk.unwritten").withStyle(ChatFormatting.GRAY));
			return;
		}
		Component kind = typeName(label.type());
		if (label.author().isEmpty()) {
			lines.accept(kind.copy().withStyle(ChatFormatting.GRAY));
		} else {
			lines.accept(Component.translatable("item.serverworks.mod_disk.by", kind, label.author())
					.withStyle(ChatFormatting.GRAY));
		}
		if (!label.loaders().isEmpty()) {
			lines.accept(Component.translatable("item.serverworks.mod_disk.runs_on", loaderList(label))
					.withStyle(ChatFormatting.DARK_GRAY));
		}
		if (label.pinnedVersion().isPresent()) {
			lines.accept(Component.translatable("item.serverworks.mod_disk.pinned",
					label.pinnedName().orElse(label.pinnedVersion().get())).withStyle(ChatFormatting.AQUA));
		}
		if (label.clientSide() == SideSupport.REQUIRED) {
			lines.accept(Component.translatable("item.serverworks.mod_disk.client_required")
					.withStyle(ChatFormatting.GOLD));
		} else if (label.clientSide() != SideSupport.UNKNOWN) {
			lines.accept(Component.translatable("item.serverworks.mod_disk.server_only")
					.withStyle(ChatFormatting.GREEN));
		}
		if (flag.isAdvanced()) {
			lines.accept(Component.literal(label.slug() + " (" + label.projectId() + ")").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	public static Component typeName(ProjectType type) {
		return Component.translatable("serverworks.project_type." + type.name().toLowerCase(Locale.ROOT));
	}

	private static String loaderList(DiskLabel label) {
		StringBuilder out = new StringBuilder();
		int shown = Math.min(MAX_LOADERS_SHOWN, label.loaders().size());
		for (int i = 0; i < shown; i++) {
			if (i > 0) {
				out.append(", ");
			}
			String loader = label.loaders().get(i);
			out.append(Character.toUpperCase(loader.charAt(0))).append(loader, 1, loader.length());
		}
		if (label.loaders().size() > shown) {
			out.append(" +").append(label.loaders().size() - shown);
		}
		return out.toString();
	}
}
