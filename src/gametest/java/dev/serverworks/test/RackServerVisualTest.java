package dev.serverworks.test;

import dev.serverworks.Serverworks;
import dev.serverworks.client.screen.ServerCoreScreen;
import dev.serverworks.client.trip.Trips;
import dev.serverworks.host.ServerInstance;
import dev.serverworks.host.ServerInstance.State;
import dev.serverworks.machine.GatewayBlock;
import dev.serverworks.item.DiskLabel;
import dev.serverworks.machine.DriveBayBlockEntity;
import dev.serverworks.machine.RackComponentBlock;
import dev.serverworks.machine.ServerCoreBlockEntity;
import dev.serverworks.net.ConsoleCommandPayload;
import dev.serverworks.rack.CasingTier;
import dev.serverworks.rack.Platform;
import dev.serverworks.registry.ModBlocks;
import dev.serverworks.registry.ModComponents;
import dev.serverworks.registry.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Starts a real Paper server from a rack, through the Server Core screen, and stops it.
 * Downloads Paper and two plugins, so it needs the internet and takes a minute.
 */
public final class RackServerVisualTest implements FabricClientGameTest {
	private static final int TICKS_PER_MINUTE = 20 * 60;

	/**
	 * Paper by default. SERVERWORKS_TRIP_PLATFORM=fabric runs the trip on a vanilla-style server,
	 * whose whitelist behaves differently in offline mode.
	 */
	private static final Platform PLATFORM = java.util.Optional.ofNullable(System.getenv("SERVERWORKS_TRIP_PLATFORM"))
			.map(Platform::byId).orElse(Platform.PAPER);

	private static final java.util.UUID TEST_DRIVE = java.util.UUID.fromString("0123abcd-1111-2222-3333-444455556666");

	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		context.getInput().resizeWindow(1280, 720);
		// Chunky is pinned to an older build, so the Server Core has an update to offer.
		DiskLabel chunky = DiskLabel.of(Serverworks.catalog().project("chunky").join());
		String minecraft = net.minecraft.SharedConstants.getCurrentVersion().name();
		List<dev.serverworks.catalog.model.ProjectVersion> chunkyBuilds = Serverworks.catalog()
				.versions("chunky", PLATFORM.acceptedLoaders(), List.of(minecraft)).join();
		boolean pinnedOld = chunkyBuilds.size() > 1;
		if (pinnedOld) {
			var older = chunkyBuilds.get(chunkyBuilds.size() - 1);
			chunky = chunky.pinnedTo(older.id(), older.versionNumber());
		}
		List<DiskLabel> disks = List.of(DiskLabel.of(Serverworks.catalog().project("luckperms").join()), chunky);

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();

			// A 5 x 3 x 3 rack whose front face holds a Status Display, the Server Core and a Gateway.
			BlockPos base = server.computeOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				BlockPos origin = player.blockPosition().south(4);
				ServerLevel level = player.level();
				BlockState casing = ModBlocks.RACK_CASINGS.get(CasingTier.DIAMOND).defaultBlockState();
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
				level.setBlockAndUpdate(origin.offset(0, 1, 2), facing(ModBlocks.PLATFORM_MODULES.get(PLATFORM), Direction.SOUTH));
				DriveBayBlockEntity bay = (DriveBayBlockEntity) level.getBlockEntity(origin.offset(-2, 1, 1));
				for (int i = 0; i < disks.size(); i++) {
					ItemStack stack = new ItemStack(ModItems.MOD_DISK);
					stack.set(ModComponents.DISK_LABEL, disks.get(i));
					bay.setItem(i, stack);
				}
				// A Config Card and a World Drive ride along: hard, no PvP, and a superflat world of its own.
				ItemStack card = new ItemStack(ModItems.CONFIG_CARD);
				card.set(ModComponents.SERVER_SETTINGS, new dev.serverworks.item.ServerSettings(
						dev.serverworks.item.ServerSettings.Difficulty.HARD, dev.serverworks.item.ServerSettings.GameMode.SURVIVAL,
						false, false, false, false, 8, 8, 0, "A tested rack"));
				bay.setItem(6, card);
				ItemStack drive = new ItemStack(ModItems.WORLD_DRIVE);
				drive.set(ModComponents.WORLD_DRIVE, new dev.serverworks.item.WorldDrive(TEST_DRIVE, "Test World", "4242",
						dev.serverworks.item.WorldDrive.WorldType.FLAT, java.util.Optional.empty()));
				bay.setItem(7, drive);
				bay.setChanged();
				return origin;
			});
			BlockPos corePos = base.offset(0, 1, 0);
			BlockPos gatewayPos = base.offset(1, 1, 0);
			java.util.UUID rackId = server.computeOnServer(s ->
					((ServerCoreBlockEntity) s.getPlayerList().getPlayers().getFirst().level().getBlockEntity(corePos)).rackId());
			context.waitTicks(10);

			openCore(server, corePos);
			context.waitForScreen(ServerCoreScreen.class);
			context.waitFor(client -> screen(client) != null && screen(client).hasStatus() && screen(client).instance() != null, 200);
			// Before the server runs, the console's hint has to fit inside the panel.
			context.runOnClient(client -> screen(client).showConsole(true));
			context.waitTicks(3);
			context.takeScreenshot("server_0_console_idle");
			context.runOnClient(client -> screen(client).showConsole(false));
			context.waitTicks(2);
			context.runOnClient(client -> screen(client).pressPower());
			context.waitTicks(3);
			if (!context.computeOnClient(client -> screen(client).showingEula())) {
				throw new AssertionError("Starting without an EULA agreement must ask for one first");
			}
			context.takeScreenshot("server_1_eula");

			context.runOnClient(client -> screen(client).agreeToEula());
			context.waitFor(client -> state(client) == State.DOWNLOADING || state(client) == State.STARTING, 2 * TICKS_PER_MINUTE);
			context.waitTicks(2);
			context.takeScreenshot("server_2_preparing");

			context.waitFor(client -> state(client) == State.RUNNING || failed(client), 5 * TICKS_PER_MINUTE);
			String failure = context.computeOnClient(client -> failed(client)
					? screen(client).instance() + "\n" + String.join("\n", screen(client).consoleLines())
					: null);
			if (failure != null) {
				throw new AssertionError("The rack server failed: " + failure);
			}
			context.waitTicks(10);
			context.takeScreenshot("server_3_running");

			context.runOnClient(client -> screen(client).showConsole(true));
			if (PLATFORM.group() == Platform.Group.PLUGIN) {
				context.runOnClient(client -> ClientPlayNetworking.send(
						new ConsoleCommandPayload(screen(client).getMenu().containerId, "plugins")));
				// Paper lists plugins one per line or several to a line, depending on their kind.
				context.waitFor(client -> screen(client).consoleLines().stream().anyMatch(l -> l.contains("Server Plugins (2)"))
						&& screen(client).consoleLines().stream().anyMatch(l -> l.matches(".* - .*LuckPerms.*"))
						&& screen(client).consoleLines().stream().anyMatch(l -> l.matches(".* - .*Chunky.*")), 20 * 30);
			} else {
				// Mod servers have no plugin list; the console still has to answer.
				context.runOnClient(client -> ClientPlayNetworking.send(
						new ConsoleCommandPayload(screen(client).getMenu().containerId, "list")));
				context.waitFor(client -> screen(client).consoleLines().stream().anyMatch(l -> l.contains("players online")), 20 * 30);
			}
			context.waitTicks(5);
			context.takeScreenshot("server_4_console");

			// The card's settings and the drive's world reached the server.
			java.nio.file.Path dir = dev.serverworks.host.Deployment.instanceDir(Serverworks.host().root(), rackId);
			String properties;
			try {
				properties = java.nio.file.Files.readString(dir.resolve("server.properties"));
			} catch (java.io.IOException e) {
				throw new AssertionError("Could not read server.properties", e);
			}
			String driveLevel = "world-" + TEST_DRIVE.toString().replace("-", "").substring(0, 8);
			for (String expected : new String[] {"difficulty=hard", "motd=A tested rack", "level-name=" + driveLevel,
					"level-type=minecraft\\:flat", "level-seed=4242", "view-distance=8"}) {
				if (!properties.contains(expected)) {
					throw new AssertionError("server.properties should contain " + expected + ":\n" + properties);
				}
			}
			if (!java.nio.file.Files.isDirectory(dir.resolve(driveLevel))) {
				throw new AssertionError("The World Drive's world folder was not made");
			}
			context.waitFor(client -> screen(client).consoleLines().stream()
					.anyMatch(l -> l.toLowerCase(java.util.Locale.ROOT).contains("pvp") && l.contains("false")), 20 * 30);
			context.runOnClient(client -> screen(client).showConsole(false));
			ServerInstance running = Serverworks.host().instances().peek(rackId);

			if (pinnedOld) {
				// The older pinned build shows as an update; updating re-pins it and restarts the server.
				context.waitFor(client -> screen(client).updateCount() >= 1, 20 * 60);
				context.waitTicks(5);
				context.takeScreenshot("server_5_update_available");
				context.runOnClient(client -> screen(client).pressUpdate());
				context.waitFor(client -> running.snapshot().state() != State.RUNNING, 20 * 60);
				context.waitFor(client -> running.snapshot().state() == State.RUNNING || failed(client), 3 * TICKS_PER_MINUTE);
				context.waitFor(client -> screen(client).updateCount() == 0, 20 * 60);
				String repinned = server.computeOnServer(s -> {
					ServerCoreBlockEntity core = (ServerCoreBlockEntity) s.overworld().getBlockEntity(corePos);
					return core.disks(core.lastScan().structure()).get(1).pinnedVersion().orElse("");
				});
				if (repinned.isEmpty() || repinned.equals(chunky.pinnedVersion().orElse(""))) {
					throw new AssertionError("Updating should re-pin Chunky to its newest build");
				}
			}

			// A comparator reading the core: 1 means running with nobody on.
			BlockPos comparatorPos = corePos.north();
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				level.setBlockAndUpdate(comparatorPos.below(), Blocks.STONE.defaultBlockState());
				level.setBlockAndUpdate(comparatorPos, Blocks.COMPARATOR.defaultBlockState()
						.setValue(net.minecraft.world.level.block.ComparatorBlock.FACING, Direction.SOUTH));
			});
			context.waitTicks(30);
			int reading = server.computeOnServer(s -> s.overworld().getBlockEntity(comparatorPos)
					instanceof net.minecraft.world.level.block.entity.ComparatorBlockEntity comparator ? comparator.getOutputSignal() : -1);
			if (reading != 1) {
				throw new AssertionError("A comparator on a running, empty rack should read 1, not " + reading);
			}

			// With redstone control on, losing power stops the rack and power starts it again.
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				ServerCoreBlockEntity core = (ServerCoreBlockEntity) level.getBlockEntity(corePos);
				dev.serverworks.machine.RackControl.setRedstoneControl(core, s.getPlayerList().getPlayers().getFirst(), true);
				level.setBlockAndUpdate(comparatorPos, Blocks.REDSTONE_BLOCK.defaultBlockState());
			});
			context.waitTicks(10);
			server.runOnServer(s -> s.overworld().setBlockAndUpdate(comparatorPos, Blocks.AIR.defaultBlockState()));
			context.waitFor(client -> running.snapshot().state() == State.STOPPED, 2 * TICKS_PER_MINUTE);
			server.runOnServer(s -> s.overworld().setBlockAndUpdate(comparatorPos, Blocks.REDSTONE_BLOCK.defaultBlockState()));
			context.waitFor(client -> running.snapshot().state() == State.RUNNING || failed(client), 3 * TICKS_PER_MINUTE);
			if (running.snapshot().state() != State.RUNNING) {
				throw new AssertionError("A redstone signal should start the rack again");
			}
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				ServerCoreBlockEntity core = (ServerCoreBlockEntity) level.getBlockEntity(corePos);
				dev.serverworks.machine.RackControl.setRedstoneControl(core, s.getPlayerList().getPlayers().getFirst(), false);
				level.setBlockAndUpdate(comparatorPos, Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(comparatorPos.below(), Blocks.AIR.defaultBlockState());
			});
			context.waitTicks(20);

			// Back Up Now copies the running server's world.
			context.runOnClient(client -> screen(client).showTab(2));
			context.runOnClient(client -> screen(client).pressBackup());
			context.waitFor(client -> screen(client).backupCount() >= 1, 2 * TICKS_PER_MINUTE);
			context.waitTicks(5);
			context.takeScreenshot("server_6_backups");
			context.runOnClient(client -> screen(client).showTab(0));

			// The rack from outside: the display reports the server and the Gateway is open.
			context.runOnClient(client -> client.player.closeContainer());
			context.getInput().lookAt(corePos);
			context.waitTicks(40);
			context.takeScreenshot("trip_1_rack_live");

			// With players on, the display shows their heads. The only player here is about to
			// leave for the rack, so report them as on it for a moment to see how it looks.
			java.util.UUID me = context.computeOnClient(client -> client.player.getUUID());
			ServerInstance live = Serverworks.host().instances().peek(rackId);
			for (int i = 0; i < 25; i++) {
				live.reportPlayers(1, 40, List.of(new dev.serverworks.host.StatusPinger.Player("Player0", me)));
				context.waitTicks(1);
			}
			context.takeScreenshot("trip_1b_display_heads");
			String portal = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().level()
					.getBlockState(gatewayPos).getValue(GatewayBlock.PORTAL).getSerializedName());
			if (!portal.equals("open")) {
				throw new AssertionError("The Gateway should be open while the server runs, but is " + portal);
			}

			// Through the Gateway: the world is saved and closed, and the game joins the Paper server.
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(gatewayPos), Direction.NORTH, gatewayPos, false);
				player.gameMode.useItemOn(player, player.level(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
			});
			context.waitFor(client -> Trips.onTrip() && client.level != null && client.player != null
					&& client.gui.screen() == null, 2 * TICKS_PER_MINUTE);
			context.waitFor(client -> {
				ServerInstance instance = Serverworks.host().instances().peek(rackId);
				return instance != null && instance.snapshot().playersOnline() == 1;
			}, TICKS_PER_MINUTE);
			context.waitTicks(60);
			context.takeScreenshot("trip_2_on_rack_server");

			// The pause menu offers the way back.
			context.setScreen(() -> new PauseScreen(true));
			context.waitTicks(5);
			boolean offered = context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).stream()
					.anyMatch(widget -> widget.getMessage().getString().startsWith("Return to")));
			if (!offered) {
				throw new AssertionError("The pause menu on a rack server must offer the way back");
			}
			context.takeScreenshot("trip_3_pause_menu");

			// The return key works too.
			context.setScreen(() -> null);
			context.waitTicks(5);
			context.getInput().pressKey(dev.serverworks.client.trip.ReturnKey.KEY);
			context.waitFor(client -> client.hasSingleplayerServer() && client.level != null && client.player != null
					&& client.gui.screen() == null, 2 * TICKS_PER_MINUTE);
			if (context.computeOnClient(client -> Trips.origin() != null)) {
				throw new AssertionError("The trip should be over once the player is home");
			}
			context.waitTicks(80);
			context.takeScreenshot("trip_4_back_home");

			ServerInstance instance = Serverworks.host().instances().peek(rackId);

			instance.stop();
			context.waitFor(client -> instance.snapshot().state() == State.STOPPED, 2 * TICKS_PER_MINUTE);
		}
	}

	private static ServerCoreScreen screen(net.minecraft.client.Minecraft client) {
		return client.gui.screen() instanceof ServerCoreScreen screen ? screen : null;
	}

	private static State state(net.minecraft.client.Minecraft client) {
		ServerCoreScreen screen = screen(client);
		return screen == null || screen.instance() == null ? null : screen.instance().state();
	}

	private static boolean failed(net.minecraft.client.Minecraft client) {
		State state = state(client);
		return state == State.FAILED || state == State.CRASHED;
	}

	private static BlockState facing(Block block, Direction direction) {
		return block.defaultBlockState().setValue(RackComponentBlock.FACING, direction);
	}

	private static void openCore(TestServerContext server, BlockPos corePos) {
		server.runOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			player.openMenu((ServerCoreBlockEntity) player.level().getBlockEntity(corePos));
		});
	}
}
