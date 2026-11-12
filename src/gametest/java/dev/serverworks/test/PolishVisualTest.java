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

			// RAM sticks: a 4 GB stick in the Memory Bank makes it a 5 GB, 50-player rack.
			BlockPos bankPos = base.offset(1, 1, 1);
			int ram = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				((dev.serverworks.machine.MemoryBankBlockEntity) level.getBlockEntity(bankPos))
						.setItem(0, new ItemStack(ModItems.MEMORY_STICKS.get(4)));
				var rackCore = (dev.serverworks.machine.ServerCoreBlockEntity) level.getBlockEntity(core);
				rackCore.rescan();
				return rackCore.ramMb(rackCore.lastScan().structure());
			});
			if (ram != 5 * 1024) {
				throw new AssertionError("A 4 GB stick should give 5 GB, not " + ram + " MB");
			}
			server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst()
					.openMenu((net.minecraft.world.MenuProvider) s.overworld().getBlockEntity(bankPos)));
			context.waitTicks(10);
			context.takeScreenshot("polish_2b_memory_bank");
			// Closed as a player would, so the server closes its side of the menu too.
			context.runOnClient(client -> client.player.closeContainer());
			context.waitTicks(3);

			// Each Drive Bay tier opens with its own number of slots, and keeps its disks when broken.
			int t = 0;
			for (net.minecraft.world.level.block.Block tier : java.util.List.of(ModBlocks.DRIVE_BAY, ModBlocks.ADVANCED_DRIVE_BAY,
					ModBlocks.ELITE_DRIVE_BAY)) {
				BlockPos bayPos = base.offset(-4 - t * 2, 0, 0);
				int index = t++;
				server.runOnServer(s -> {
					ServerLevel level = s.overworld();
					level.setBlockAndUpdate(bayPos, facing(tier, Direction.NORTH));
					var bay = (dev.serverworks.machine.DriveBayBlockEntity) level.getBlockEntity(bayPos);
					bay.setItem(bay.getContainerSize() - 1, new ItemStack(ModItems.CONFIG_CARD));
					s.getPlayerList().getPlayers().getFirst().openMenu(bay);
				});
				context.waitTicks(10);
				context.takeScreenshot("polish_2c_drive_bay_" + (index + 1));
				// Closed as a player would, so the server closes its side of the menu too.
				context.runOnClient(client -> client.player.closeContainer());
				context.waitTicks(3);
				int kept = server.computeOnServer(s -> {
					ServerLevel level = s.overworld();
					level.destroyBlock(bayPos, true);
					for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
							new net.minecraft.world.phys.AABB(bayPos).inflate(2))) {
						ItemStack dropped = entity.getItem();
						entity.discard();
						if (dropped.is(tier.asItem())) {
							var contents = dropped.get(net.minecraft.core.component.DataComponents.CONTAINER);
							return contents == null ? 0 : (int) contents.nonEmptyItemCopyStream().count();
						}
					}
					return -1;
				});
				if (kept != 1) {
					throw new AssertionError(tier + " should drop itself with its card inside, got " + kept);
				}
			}

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
