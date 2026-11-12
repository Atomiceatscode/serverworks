package dev.serverworks.test;

import dev.serverworks.Serverworks;
import dev.serverworks.client.screen.ConfigScreen;
import dev.serverworks.client.screen.ManualScreen;
import dev.serverworks.machine.RackComponentBlock;
import dev.serverworks.rack.CasingTier;
import dev.serverworks.rack.Platform;
import dev.serverworks.registry.ModBlocks;
import dev.serverworks.registry.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The finishing touches: the Wrench's build preview, the manual, settings, advancements and Jade. */
public final class PolishVisualTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();

			// Half a rack: the front face and one corner column. The Wrench should outline the rest.
			BlockPos base = server.computeOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				BlockPos origin = player.blockPosition().south(4).east(1);
				ServerLevel level = player.level();
				BlockState casing = ModBlocks.RACK_CASINGS.get(CasingTier.IRON).defaultBlockState();
				for (int x = -1; x <= 1; x++) {
					for (int y = 0; y <= 2; y++) {
						level.setBlockAndUpdate(origin.offset(x, y, 0), casing);
					}
				}
				level.setBlockAndUpdate(origin.offset(-1, 0, 1), casing);
				level.setBlockAndUpdate(origin.offset(0, 1, 0), facing(ModBlocks.SERVER_CORE, Direction.NORTH));
				level.setBlockAndUpdate(origin.offset(1, 1, 1), Blocks.STONE.defaultBlockState());
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH));
				player.getInventory().add(new ItemStack(ModItems.OPERATORS_MANUAL));
				player.getInventory().add(new ItemStack(ModBlocks.DISK_WRITER));
				return origin;
			});
			BlockPos core = base.offset(0, 1, 0);
			context.waitTicks(10);
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(core), Direction.NORTH, core, false);
				player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
			});
			// From the front, the outlines sit behind the face; view them from above and to the side.
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				player.level().setBlockAndUpdate(base.offset(4, 3, -3), Blocks.STONE.defaultBlockState());
				player.teleportTo(base.getX() + 4.5, base.getY() + 4, base.getZ() - 2.5);
			});
			context.waitTicks(5);
			context.getInput().lookAt(base.offset(0, 1, 1));
			context.waitTicks(12);
			context.takeScreenshot("polish_1_wrench_preview");

			// Finish it: the rack forms, and the "Rack and Roll" advancement is granted.
			server.runOnServer(s -> {
				ServerLevel level = s.getPlayerList().getPlayers().getFirst().level();
				BlockState casing = ModBlocks.RACK_CASINGS.get(CasingTier.IRON).defaultBlockState();
				for (int x = -1; x <= 1; x++) {
					for (int y = 0; y <= 2; y++) {
						for (int z = 1; z <= 2; z++) {
							boolean inside = x == 0 && y == 1 && z == 1;
							level.setBlockAndUpdate(base.offset(x, y, z), inside ? Blocks.AIR.defaultBlockState() : casing);
						}
					}
				}
				level.setBlockAndUpdate(base.offset(0, 1, 2), facing(ModBlocks.PLATFORM_MODULES.get(Platform.PAPER), Direction.SOUTH));
				level.setBlockAndUpdate(base.offset(-1, 1, 1), facing(ModBlocks.DRIVE_BAY, Direction.WEST));
				level.setBlockAndUpdate(base.offset(1, 1, 1), facing(ModBlocks.MEMORY_BANK, Direction.EAST));
				level.setBlockAndUpdate(base.offset(0, 2, 1), facing(ModBlocks.VENT, Direction.UP));
			});
			context.waitTicks(30);
			server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst()
					.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() - 3.5));
			context.waitTicks(5);
			context.getInput().lookAt(core);
			context.waitTicks(10);
			context.takeScreenshot("polish_2_formed_with_jade");

			context.setScreen(ManualScreen::new);
			// Opening a screen centres the mouse; move it off the pages so no tooltip covers them.
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(5);
			context.takeScreenshot("polish_3_manual_contents");
			context.runOnClient(client -> ((ManualScreen) client.gui.screen()).showPage(2));
			context.waitTicks(5);
			context.takeScreenshot("polish_4_manual_building");
			context.runOnClient(client -> ((ManualScreen) client.gui.screen()).showPage(6));
			context.waitTicks(5);
			context.takeScreenshot("polish_5_manual_joining");
			context.runOnClient(client -> {
				ManualScreen manual = (ManualScreen) client.gui.screen();
				manual.showPage(manual.firstRecipePage());
			});
			context.waitTicks(5);
			context.takeScreenshot("polish_5b_manual_recipes");
			context.runOnClient(client -> {
				ManualScreen manual = (ManualScreen) client.gui.screen();
				manual.showPage(manual.pageCount() - 2);
			});
			context.waitTicks(5);
			context.takeScreenshot("polish_5c_manual_recipes_last");
			context.runOnClient(client -> {
				ManualScreen manual = (ManualScreen) client.gui.screen();
				manual.showPage(manual.pageCount() - 1);
			});
			context.waitTicks(5);
			context.takeScreenshot("polish_5d_manual_modules");

			// The Config Card and World Drive editors.
			server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst()
					.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CONFIG_CARD)));
			context.waitTicks(5);
			context.runOnClient(client -> dev.serverworks.ClientHooks.editConfigCard.accept(InteractionHand.MAIN_HAND));
			context.waitTicks(5);
			context.takeScreenshot("polish_5e_config_card");
			server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst()
					.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WORLD_DRIVE)));
			context.waitTicks(5);
			context.runOnClient(client -> dev.serverworks.ClientHooks.editWorldDrive.accept(InteractionHand.MAIN_HAND));
			context.waitTicks(5);
			context.takeScreenshot("polish_5f_world_drive");

			context.setScreen(() -> new ConfigScreen(null));
			context.waitTicks(5);
			context.takeScreenshot("polish_6_config");

			context.runOnClient(client -> {
				var advancements = client.player.connection.getAdvancements();
				advancements.setSelectedTab(advancements.get(Serverworks.id("main/root")), false);
				client.gui.setScreen(new AdvancementsScreen(advancements));
			});
			context.waitTicks(10);
			context.takeScreenshot("polish_7_advancements");
			context.setScreen(() -> null);
		}
	}

	private static BlockState facing(Block block, Direction direction) {
		return block.defaultBlockState().setValue(RackComponentBlock.FACING, direction);
	}
}
