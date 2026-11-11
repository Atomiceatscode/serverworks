package dev.serverworks.client.compat;

import dev.serverworks.registry.ModItems;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The same information pages as {@link JeiIntegration}, for Roughly Enough Items. */
public final class ReiIntegration implements REIClientPlugin {
	@Override
	public void registerDisplays(DisplayRegistry registry) {
		info(registry, ModItems.MOD_DISK, "serverworks.recipe_viewer.mod_disk");
		info(registry, ModItems.PACK_CARTRIDGE, "serverworks.recipe_viewer.pack_cartridge");
		info(registry, ModItems.CONFIG_CARD, "serverworks.recipe_viewer.config_card");
		info(registry, ModItems.WORLD_DRIVE, "serverworks.recipe_viewer.world_drive");
	}

	private static void info(DisplayRegistry registry, Item item, String key) {
		ItemStack stack = new ItemStack(item);
		registry.add(DefaultInformationDisplay.createFromEntry(EntryStacks.of(stack), stack.getHoverName())
				.line(Component.translatable(key)));
	}
}
