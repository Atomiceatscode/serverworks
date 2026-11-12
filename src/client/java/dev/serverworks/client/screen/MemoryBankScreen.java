package dev.serverworks.client.screen;

import dev.serverworks.Serverworks;
import dev.serverworks.machine.MemoryBankMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Four RAM stick slots, and how much memory they add up to. */
public final class MemoryBankScreen extends AbstractContainerScreen<MemoryBankMenu> {
	private static final Identifier BACKGROUND = Serverworks.id("textures/gui/container/memory_bank.png");
	private static final int TEXT = 0xFF404040;
	private static final int TOTAL = 0xFF2F7A4A;

	public MemoryBankScreen(MemoryBankMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 134);
		this.inventoryLabelY = imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, titleLabelX, titleLabelY, TEXT, false);
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
		Component total = Component.translatable("serverworks.memory_bank.total", menu.megabytes() / 1024);
		graphics.text(font, total, imageWidth - 8 - font.width(total), titleLabelY, TOTAL, false);
	}
}
