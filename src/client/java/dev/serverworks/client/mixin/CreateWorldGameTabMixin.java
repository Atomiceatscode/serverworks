package dev.serverworks.client.mixin;

import dev.serverworks.client.ShowcaseWorldOption;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the "Serverworks Showcase" switch under the Game tab's other options. */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab")
abstract class CreateWorldGameTabMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void serverworks$addShowcaseSwitch(CreateWorldScreen screen, CallbackInfo ci) {
		GridLayout layout = ((GridLayoutTabAccessor) this).serverworks$layout();
		int[] rows = {0};
		layout.visitChildren(child -> rows[0]++);
		WorldCreationUiState state = screen.getUiState();
		layout.addChild(CycleButton.onOffBuilder(ShowcaseWorldOption.isOn(state))
				.withTooltip(on -> Tooltip.create(Component.translatable("serverworks.create_world.showcase.info")))
				.create(0, 0, 210, 20, Component.translatable("serverworks.create_world.showcase"),
						(button, on) -> ShowcaseWorldOption.set(state, on)), rows[0], 0);
	}
}
