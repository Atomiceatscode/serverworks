package dev.serverworks.test;

import dev.serverworks.Serverworks;
import dev.serverworks.client.screen.ServerCoreScreen;
import dev.serverworks.client.trip.Trips;
import dev.serverworks.host.ServerInstance;
import dev.serverworks.host.ServerInstance.State;
import dev.serverworks.machine.RackComponentBlock;
import dev.serverworks.machine.ServerCoreBlockEntity;
import dev.serverworks.rack.CasingTier;
import dev.serverworks.rack.Platform;
import dev.serverworks.registry.ModBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
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

import java.util.UUID;

/**
 * Serverworks on a dedicated server: an operator starts a rack, a non-operator cannot see
 * its console, the Gateway trip leaves a multiplayer server and Return comes back to it, and
 * stopping the dedicated server stops the rack server with it.
 */
public final class DedicatedVisualTest implements FabricClientGameTest {
	private static final int MINUTE = 20 * 60;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		context.getInput().resizeWindow(1280, 720);
		UUID rack;
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			TestServerConnection connection = server.connect();
			connection.waitForChunksRender();
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				s.getPlayerList().op(player.nameAndId());
			});

			BlockPos core = server.computeOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				BlockPos origin = player.blockPosition().south(4);
				buildRack(player.level(), origin);
				return origin.offset(0, 1, 0);
			});
			rack = server.computeOnServer(s -> ((ServerCoreBlockEntity) s.getPlayerList().getPlayers().getFirst().level()
					.getBlockEntity(core)).rackId());
			context.waitTicks(10);

			// An operator starts it, agreeing to the EULA on the way.
			openCore(context, server, core);
			context.runOnClient(client -> screen(client).pressPower());
			context.waitTicks(3);
			context.runOnClient(client -> screen(client).agreeToEula());
			context.waitFor(client -> state(rack) == State.RUNNING || failed(rack), 5 * MINUTE);
			if (failed(rack)) {
				throw new AssertionError("Rack server failed: " + snapshot(rack));
			}
			context.waitTicks(10);
			context.takeScreenshot("dedicated_1_operator_core");
			context.runOnClient(client -> client.player.closeContainer());

			// Without operator rights the console stays private and Start is out of reach.
			server.runOnServer(s -> s.getPlayerList().deop(s.getPlayerList().getPlayers().getFirst().nameAndId()));
			context.waitTicks(10);
			openCore(context, server, core);
			context.runOnClient(client -> screen(client).showConsole(true));
			context.waitTicks(10);
			boolean leaked = context.computeOnClient(client -> !screen(client).consoleLines().isEmpty()
					|| screen(client).instance().canControl() || !screen(client).instance().detail().isEmpty());
			if (leaked) {
				throw new AssertionError("A non-operator received the rack's console or controls");
			}
			context.takeScreenshot("dedicated_2_non_operator_console");
			context.runOnClient(client -> client.player.closeContainer());

			// Through the Gateway from a multiplayer server, and back again.
			BlockPos gateway = core.offset(1, 0, 0);
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(gateway), Direction.NORTH, gateway, false);
				player.gameMode.useItemOn(player, player.level(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
			});
			context.waitFor(client -> Trips.onTrip() && client.level != null && client.player != null
					&& client.gui.screen() == null, 2 * MINUTE);
			context.waitFor(client -> snapshot(rack).playersOnline() == 1, MINUTE);
			context.waitTicks(40);
			context.takeScreenshot("dedicated_3_on_rack_server");
			if (context.computeOnClient(client -> Trips.origin().server() == null)) {
				throw new AssertionError("A trip from a dedicated server must remember that server");
			}

			context.runOnClient(client -> Trips.goBack());
			context.waitFor(client -> client.level != null && client.player != null && client.gui.screen() == null
					&& !Trips.onTrip() && client.getCurrentServer() != null, 2 * MINUTE);
			context.waitTicks(40);
			context.takeScreenshot("dedicated_4_back_on_dedicated_server");
			if (state(rack) != State.RUNNING) {
				throw new AssertionError("The rack server should still be running: " + snapshot(rack));
			}
			// Return made a fresh connection the test framework does not know about, so leave it here.
			context.runOnClient(client -> client.disconnectFromWorld(net.minecraft.network.chat.Component.literal("Test finished")));
			context.waitFor(client -> client.level == null, MINUTE);
		}
		// Closing the dedicated server must take its rack server down with it.
		context.waitFor(client -> !state(rack).active(), MINUTE);
		context.setScreen(net.minecraft.client.gui.screens.TitleScreen::new);
		long stragglers = ProcessHandle.allProcesses()
				.filter(p -> p.info().arguments().map(a -> String.join(" ", a).contains("serverworks.rack=" + rack)).orElse(false))
				.count();
		if (stragglers != 0) {
			throw new AssertionError("The rack server process outlived the dedicated server");
		}
	}

	private static void buildRack(ServerLevel level, BlockPos origin) {
		BlockState casing = ModBlocks.RACK_CASINGS.get(CasingTier.IRON).defaultBlockState();
		for (int x = -2; x <= 2; x++) {
			for (int y = 0; y <= 2; y++) {
				for (int z = 0; z <= 2; z++) {
					boolean inside = Math.abs(x) <= 1 && y == 1 && z == 1;
					level.setBlockAndUpdate(origin.offset(x, y, z), inside ? Blocks.AIR.defaultBlockState() : casing);
				}
			}
		}
		level.setBlockAndUpdate(origin.offset(-1, 1, 0), facing(ModBlocks.STATUS_DISPLAY, Direction.NORTH));
		level.setBlockAndUpdate(origin.offset(0, 1, 0), facing(ModBlocks.SERVER_CORE, Direction.NORTH));
		level.setBlockAndUpdate(origin.offset(1, 1, 0), facing(ModBlocks.GATEWAY, Direction.NORTH));
		level.setBlockAndUpdate(origin.offset(-2, 1, 1), facing(ModBlocks.DRIVE_BAY, Direction.WEST));
		level.setBlockAndUpdate(origin.offset(2, 1, 1), facing(ModBlocks.MEMORY_BANK, Direction.EAST));
		level.setBlockAndUpdate(origin.offset(0, 2, 1), facing(ModBlocks.VENT, Direction.UP));
		level.setBlockAndUpdate(origin.offset(0, 1, 2), facing(ModBlocks.PLATFORM_MODULES.get(Platform.PAPER), Direction.SOUTH));
	}

	private static void openCore(ClientGameTestContext context, TestDedicatedServerContext server, BlockPos core) {
		server.runOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			player.openMenu((ServerCoreBlockEntity) player.level().getBlockEntity(core));
		});
		context.waitForScreen(ServerCoreScreen.class);
		context.waitFor(client -> screen(client) != null && screen(client).hasStatus() && screen(client).instance() != null, 200);
	}

	private static ServerCoreScreen screen(net.minecraft.client.Minecraft client) {
		return client.gui.screen() instanceof ServerCoreScreen screen ? screen : null;
	}

	private static BlockState facing(Block block, Direction direction) {
		return block.defaultBlockState().setValue(RackComponentBlock.FACING, direction);
	}

	private static ServerInstance.Snapshot snapshot(UUID rack) {
		ServerInstance instance = Serverworks.host().instances().peek(rack);
		return instance == null ? ServerInstance.Snapshot.IDLE : instance.snapshot();
	}

	private static State state(UUID rack) {
		return snapshot(rack).state();
	}

	private static boolean failed(UUID rack) {
		return state(rack) == State.FAILED || state(rack) == State.CRASHED;
	}
}
