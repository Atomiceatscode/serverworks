package dev.serverworks.client.compat;

import dev.serverworks.Serverworks;
import dev.serverworks.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * JEI already shows Serverworks' crafting recipes; this adds what crafting cannot show:
 * disks and cartridges are written in the Disk Writer. Loaded only when JEI is installed.
 */
@JeiPlugin
public final class JeiIntegration implements IModPlugin {
	@Override
	public Identifier getPluginUid() {
		return Serverworks.id("jei");
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		registration.addIngredientInfo(ModItems.MOD_DISK, Component.translatable("serverworks.recipe_viewer.mod_disk"));
		registration.addIngredientInfo(ModItems.PACK_CARTRIDGE, Component.translatable("serverworks.recipe_viewer.pack_cartridge"));
		registration.addIngredientInfo(ModItems.CONFIG_CARD, Component.translatable("serverworks.recipe_viewer.config_card"));
		registration.addIngredientInfo(ModItems.WORLD_DRIVE, Component.translatable("serverworks.recipe_viewer.world_drive"));
	}
}
