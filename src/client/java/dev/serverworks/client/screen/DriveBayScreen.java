package dev.serverworks.client.screen;

import dev.serverworks.Serverworks;
import dev.serverworks.machine.DriveBayMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** A bay's disk slots, one to four rows of eight, above the player's inventory. */
public final class DriveBayScreen extends AbstractContainerScreen<DriveBayMenu> {
	private static final int TEXT = 0xFF404040;
	private final Identifier background;

	public DriveBayScreen(DriveBayMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, DriveBayMenu.inventoryY(menu.rows() * DriveBayMenu.COLUMNS) + 83);
		this.inventoryLabelY = imageHeight - 94;
		this.background = Serverworks.id("textures/gui/container/drive_bay_" + menu.rows() + ".png");
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, background, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, titleLabelX, titleLabelY, TEXT, false);
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
	}
}
