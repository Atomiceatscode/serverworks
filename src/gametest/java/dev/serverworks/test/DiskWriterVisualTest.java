package dev.serverworks.test;

import dev.serverworks.client.screen.DiskWriterScreen;
import dev.serverworks.machine.DiskWriterMenu;
import dev.serverworks.registry.ModBlocks;
import dev.serverworks.registry.ModComponents;
import dev.serverworks.registry.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Walks through the Disk Writer end to end against the live catalog and saves a
 * screenshot at each step. It needs internet access, so it is a review tool rather than
 * a CI gate.
 */
public final class DiskWriterVisualTest implements FabricClientGameTest {
	private static final int LIST_X = 8;
	private static final int LIST_Y = 36;
	private static final int ROW_H = 23;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();

			BlockPos writerPos = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos pos = player.blockPosition().south(2);
				player.level().setBlockAndUpdate(pos, ModBlocks.DISK_WRITER.defaultBlockState());
				player.getInventory().add(new ItemStack(ModItems.BLANK_DISK, 8));
				return pos;
			});
			context.waitTicks(5);
			context.getInput().lookAt(writerPos);
			context.waitTicks(10);
			context.takeScreenshot("1_writer_in_world");

			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockState state = player.level().getBlockState(writerPos);
				player.openMenu(state.getMenuProvider(player.level(), writerPos));
			});
			context.waitForScreen(DiskWriterScreen.class);
			waitForResults(context);
			context.waitTicks(80); // let icons download
			context.takeScreenshot("2_catalog_popular");

			context.getInput().typeChars("lithium");
			context.waitTicks(15);
			waitForResults(context);
			context.waitTicks(60);
			context.takeScreenshot("3_catalog_search");

			insertBlankDisks(context);
			clickRow(context, 0);
			waitForWrittenDisk(context);
			context.waitTicks(10);
			context.takeScreenshot("4_ready_to_write");
			takeResult(context);

			clickRow(context, 1);
			waitForWrittenDisk(context);
			takeResult(context);
			clickRow(context, 2);
			waitForWrittenDisk(context);
			takeResult(context);
			context.waitTicks(10);
			context.takeScreenshot("5_disks_written");

			context.runOnClient(client -> client.player.closeContainer());
			context.waitTicks(5);
			context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			context.waitTicks(20);
			context.takeScreenshot("6_inventory");
			context.setScreen(() -> null);

			int diskSlot = context.computeOnClient(client -> {
				for (int i = 0; i < 9; i++) {
					if (client.player.getInventory().getItem(i).has(ModComponents.DISK_LABEL)) {
						return i;
					}
				}
				return -1;
			});
			if (diskSlot < 0) {
				throw new AssertionError("No written disk reached the hotbar");
			}
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(diskSlot));
			context.waitTicks(20);
			context.takeScreenshot("7_disk_in_hand");
		}
	}

	private static void waitForResults(ClientGameTestContext context) {
		context.waitFor(client -> client.gui.screen() instanceof DiskWriterScreen screen
				&& !screen.isSearching() && screen.resultCount() > 0, 400);
	}

	private static void waitForWrittenDisk(ClientGameTestContext context) {
		context.waitFor(client -> client.player.containerMenu instanceof DiskWriterMenu menu
				&& menu.getSlot(DiskWriterMenu.RESULT_SLOT).hasItem(), 400);
	}

	private static void insertBlankDisks(ClientGameTestContext context) {
		context.runOnClient(client -> {
			DiskWriterMenu menu = (DiskWriterMenu) client.player.containerMenu;
			for (Slot slot : menu.slots) {
				if (slot.index > DiskWriterMenu.RESULT_SLOT && slot.getItem().is(ModItems.BLANK_DISK)) {
					client.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, client.player);
					return;
				}
			}
			throw new AssertionError("No blank disks in the inventory");
		});
		context.waitTicks(5);
	}

	/** Picks the written disk up and drops it in a free hotbar slot. Shift-click would write every blank disk at once. */
	private static void takeResult(ClientGameTestContext context) {
		context.runOnClient(client -> {
			DiskWriterMenu menu = (DiskWriterMenu) client.player.containerMenu;
			client.gameMode.handleContainerInput(menu.containerId, DiskWriterMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, client.player);
			for (int i = menu.slots.size() - 9; i < menu.slots.size(); i++) {
				if (!menu.getSlot(i).hasItem()) {
					client.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.PICKUP, client.player);
					return;
				}
			}
			throw new AssertionError("No free hotbar slot for the written disk");
		});
		context.waitTicks(5);
	}

	private static void clickRow(ClientGameTestContext context, int row) {
		context.runOnClient(client -> {
			DiskWriterScreen screen = (DiskWriterScreen) client.gui.screen();
			double x = (screen.width - 276) / 2.0 + LIST_X + 40;
			double y = (screen.height - 222) / 2.0 + LIST_Y + row * ROW_H + 10;
			screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)), false);
		});
		context.waitTicks(2);
	}
}
