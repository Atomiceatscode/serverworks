package dev.serverworks.client.mixin;

import dev.serverworks.client.ShowcaseWorldOption;
import dev.serverworks.showcase.Showcase;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** When the Showcase switch is on, asks for the showcase in the world being created. */
@Mixin(CreateWorldScreen.class)
abstract class CreateWorldScreenMixin {
	@Inject(method = "onCreate", at = @At("HEAD"))
	private void serverworks$requestShowcase(CallbackInfo ci) {
		CreateWorldScreen screen = (CreateWorldScreen) (Object) this;
		if (ShowcaseWorldOption.isOn(screen.getUiState())) {
			Showcase.buildInNewWorld(screen.getUiState().getTargetFolder());
		}
	}
}
