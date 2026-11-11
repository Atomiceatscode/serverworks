package dev.serverworks.test;

import dev.serverworks.Serverworks;
import dev.serverworks.client.screen.DriveBayScreen;
import dev.serverworks.client.screen.ServerCoreScreen;
import dev.serverworks.item.DiskLabel;
import dev.serverworks.machine.DriveBayBlockEntity;
import dev.serverworks.machine.RackBlock;
import dev.serverworks.machine.RackComponentBlock;
import dev.serverworks.machine.ServerCoreBlockEntity;
import dev.serverworks.rack.CasingTier;
import dev.serverworks.rack.Platform;
import dev.serverworks.rack.RackScan;
import dev.serverworks.registry.ModBlocks;
import dev.serverworks.registry.ModComponents;
import dev.serverworks.registry.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a rack, checks that it forms and un-forms as the rules say, and saves
 * screenshots of the structure and its screens. Needs internet access for the disks.
 */
public final class RackVisualTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		context.getInput().resizeWindow(1280, 720);
		List<DiskLabel> disks = new ArrayList<>();
		for (String slug : List.of("luckperms", "chunky", "lithium", "viaversion", "fabric-api")) {
			disks.add(DiskLabel.of(Serverworks.catalog().project(slug).join()));
		}

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();

			// A 5 x 4 x 3 rack in front of the player, complete except for its platform module.
			BlockPos origin = server.computeOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				BlockPos base = player.blockPosition().south(5);
				ServerLevel level = player.level();
				BlockState casing = ModBlocks.RACK_CASINGS.get(CasingTier.IRON).defaultBlockState();
				for (int x = -2; x <= 2; x++) {
					for (int y = 0; y <= 3; y++) {
						for (int z = 0; z <= 2; z++) {
							boolean inside = Math.abs(x) < 2 && y > 0 && y < 3 && z == 1;
							level.setBlockAndUpdate(base.offset(x, y, z), inside ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : casing);
						}
					}
				}
				level.setBlockAndUpdate(base.offset(0, 1, 0), facing(ModBlocks.SERVER_CORE, Direction.NORTH));
				level.setBlockAndUpdate(base.offset(-1, 1, 0), facing(ModBlocks.DRIVE_BAY, Direction.NORTH));
				level.setBlockAndUpdate(base.offset(1, 1, 0), facing(ModBlocks.DRIVE_BAY, Direction.NORTH));
				level.setBlockAndUpdate(base.offset(0, 2, 0), facing(ModBlocks.STATUS_DISPLAY, Direction.NORTH));
				level.setBlockAndUpdate(base.offset(1, 2, 0), facing(ModBlocks.MEMORY_BANK, Direction.NORTH));
				level.setBlockAndUpdate(base.offset(0, 3, 1), facing(ModBlocks.VENT, Direction.UP));
				return base;
			});
			BlockPos corePos = origin.offset(0, 1, 0);
			BlockPos modulePos = origin.offset(-1, 2, 0);
			context.waitTicks(5);
			context.getInput().lookAt(origin.offset(0, 2, 0));
			context.waitTicks(25);

			expect(server, corePos, RackScan.Problem.NO_PLATFORM);
			expectFormedState(server, origin, false);
			context.takeScreenshot("rack_1_unformed");

			openCore(context, server, corePos);
			context.takeScreenshot("rack_2_core_needs_platform");
			context.runOnClient(client -> client.player.closeContainer());

			// Adding the module completes it.
			setBlock(server, modulePos, facing(ModBlocks.PLATFORM_MODULES.get(Platform.PAPER), Direction.NORTH));
			context.waitTicks(5);
			expect(server, corePos, RackScan.Problem.NONE);
			expectFormedState(server, origin, true);
			context.waitTicks(10);
			context.takeScreenshot("rack_3_formed_paper");

			// Load disks: some run on Paper, some do not.
			server.runOnServer(s -> {
				ServerLevel level = s.getPlayerList().getPlayers().getFirst().level();
				DriveBayBlockEntity bay = (DriveBayBlockEntity) level.getBlockEntity(origin.offset(-1, 1, 0));
				for (int i = 0; i < disks.size(); i++) {
					ItemStack stack = new ItemStack(ModItems.MOD_DISK);
					stack.set(ModComponents.DISK_LABEL, disks.get(i));
					bay.setItem(i, stack);
				}
				bay.setChanged();
			});
			context.waitTicks(10);
			context.takeScreenshot("rack_4_disks_loaded");

			openCore(context, server, corePos);
			context.waitTicks(60); // icons
			int shown = context.computeOnClient(client -> ((ServerCoreScreen) client.gui.screen()).status().disks().size());
			if (shown != disks.size()) {
				throw new AssertionError("Core screen shows " + shown + " disks, expected " + disks.size());
			}
			context.takeScreenshot("rack_5_core_formed");
			context.runOnClient(client -> client.player.closeContainer());

			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				player.openMenu((DriveBayBlockEntity) player.level().getBlockEntity(origin.offset(-1, 1, 0)));
			});
			context.waitForScreen(DriveBayScreen.class);
			context.waitTicks(10);
			context.takeScreenshot("rack_6_drive_bay");
			context.runOnClient(client -> client.player.closeContainer());

			// Swapping the module re-skins the frame.
			setBlock(server, modulePos, facing(ModBlocks.PLATFORM_MODULES.get(Platform.NEOFORGE), Direction.NORTH));
			context.waitTicks(15);
			expect(server, corePos, RackScan.Problem.NONE);
			context.takeScreenshot("rack_7_formed_neoforge");

			// Knocking out a corner breaks the rack, and every block goes dark again.
			setBlock(server, origin.offset(2, 3, 0), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			context.waitTicks(5);
			expect(server, corePos, RackScan.Problem.MISSING_BLOCK);
			expectFormedState(server, origin, false);

			// The wrench names the problem and outlines the block to fix.
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				ItemStack wrench = new ItemStack(ModItems.WRENCH);
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wrench);
				net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
						net.minecraft.world.phys.Vec3.atCenterOf(corePos), Direction.NORTH, corePos, false);
				net.minecraft.world.InteractionResult result = player.gameMode.useItemOn(player, player.level(), wrench,
						net.minecraft.world.InteractionHand.MAIN_HAND, hit);
				if (!result.consumesAction()) {
					throw new AssertionError("Wrench did nothing on a rack block: " + result);
				}
			});
			context.waitTicks(4);
			if (context.computeOnClient(client -> client.gui.screen() != null)) {
				throw new AssertionError("The wrench opened a screen instead of inspecting the rack");
			}
			context.takeScreenshot("rack_8_wrench");

			// Restoring it forms again; removing the core un-forms everything.
			setBlock(server, origin.offset(2, 3, 0), ModBlocks.RACK_CASINGS.get(CasingTier.IRON).defaultBlockState());
			context.waitTicks(5);
			expectFormedState(server, origin, true);
			server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().level().destroyBlock(corePos, false));
			context.waitTicks(5);
			expectFormedState(server, origin, false);
		}
	}

	private static BlockState facing(Block block, Direction direction) {
		return block.defaultBlockState().setValue(RackComponentBlock.FACING, direction);
	}

	private static void setBlock(TestServerContext server, BlockPos pos, BlockState state) {
		server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().level().setBlockAndUpdate(pos, state));
	}

	private static void openCore(ClientGameTestContext context, TestServerContext server, BlockPos corePos) {
		server.runOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			player.openMenu((ServerCoreBlockEntity) player.level().getBlockEntity(corePos));
		});
		context.waitForScreen(ServerCoreScreen.class);
		context.waitFor(client -> client.gui.screen() instanceof ServerCoreScreen screen && screen.hasStatus(), 100);
		context.waitTicks(5);
	}

	private static void expect(TestServerContext server, BlockPos corePos, RackScan.Problem expected) {
		RackScan.Problem actual = server.computeOnServer(s -> {
			ServerLevel level = s.getPlayerList().getPlayers().getFirst().level();
			RackScan scan = ((ServerCoreBlockEntity) level.getBlockEntity(corePos)).lastScan();
			return scan == null ? null : scan.problem();
		});
		if (actual != expected) {
			throw new AssertionError("Expected rack state " + expected + " but it is " + actual);
		}
	}

	/** Every rack block of the test rack must have the given formed state. */
	private static void expectFormedState(TestServerContext server, BlockPos origin, boolean formed) {
		String wrong = server.computeOnServer(s -> {
			ServerLevel level = s.getPlayerList().getPlayers().getFirst().level();
			for (int x = -2; x <= 2; x++) {
				for (int y = 0; y <= 3; y++) {
					for (int z = 0; z <= 2; z++) {
						BlockPos pos = origin.offset(x, y, z);
						BlockState state = level.getBlockState(pos);
						if (state.getBlock() instanceof RackBlock && state.getValue(RackBlock.FORMED) != formed) {
							return pos.toShortString() + " " + state;
						}
					}
				}
			}
			return null;
		});
		if (wrong != null) {
			throw new AssertionError("Expected formed=" + formed + " everywhere, but found " + wrong);
		}
	}
}
