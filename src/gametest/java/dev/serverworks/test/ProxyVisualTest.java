package dev.serverworks.test;

import dev.serverworks.Serverworks;
import dev.serverworks.client.screen.ServerCoreScreen;
import dev.serverworks.client.trip.Trips;
import dev.serverworks.host.ServerInstance;
import dev.serverworks.host.ServerInstance.State;
import dev.serverworks.item.LinkTarget;
import dev.serverworks.machine.DriveBayBlockEntity;
import dev.serverworks.machine.RackComponentBlock;
import dev.serverworks.machine.ServerCoreBlockEntity;
import dev.serverworks.rack.CasingTier;
import dev.serverworks.rack.Platform;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * A Velocity rack in front of a Paper rack: starting the proxy starts Paper too, and the
 * proxy's Gateway lands the player on Paper. Needs the internet; reuses the EULA agreement
 * made by the earlier rack test in the same run.
 */
public final class ProxyVisualTest implements FabricClientGameTest {
	private static final int MINUTE = 20 * 60;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();

			BlockPos[] cores = server.computeOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				BlockPos proxyBase = player.blockPosition().south(4);
				BlockPos paperBase = proxyBase.east(7);
				buildRack(level, paperBase, Platform.PAPER, false);
				buildRack(level, proxyBase, Platform.VELOCITY, true);
				return new BlockPos[] {proxyBase.offset(0, 1, 0), paperBase.offset(0, 1, 0)};
			});
			BlockPos proxyCore = cores[0];
			BlockPos paperCore = cores[1];
			context.waitTicks(10);

			// A Link Card for the Paper rack goes into the proxy's Drive Bay.
			UUID paperRack = server.computeOnServer(s -> {
				ServerLevel level = s.getPlayerList().getPlayers().getFirst().level();
				ServerCoreBlockEntity paper = (ServerCoreBlockEntity) level.getBlockEntity(paperCore);
				ItemStack card = new ItemStack(ModItems.LINK_CARD);
				card.set(ModComponents.LINK_TARGET, new LinkTarget(paper.rackId(), paperCore, level.dimension().identifier().toString(),
						Platform.PAPER.id()));
				DriveBayBlockEntity bay = (DriveBayBlockEntity) level.getBlockEntity(proxyCore.offset(-2, 0, 1));
				bay.setItem(0, card);
				bay.setChanged();
				return paper.rackId();
			});
			UUID proxyRack = server.computeOnServer(s -> ((ServerCoreBlockEntity) s.getPlayerList().getPlayers().getFirst()
					.level().getBlockEntity(proxyCore)).rackId());
			context.waitTicks(30);

			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				player.openMenu((ServerCoreBlockEntity) player.level().getBlockEntity(proxyCore));
			});
			context.waitForScreen(ServerCoreScreen.class);
			context.waitFor(client -> client.gui.screen() instanceof ServerCoreScreen screen && screen.hasStatus()
					&& screen.instance() != null, 200);
			context.runOnClient(client -> ((ServerCoreScreen) client.gui.screen()).pressPower());
			context.waitFor(client -> state(proxyRack) == State.RUNNING && state(paperRack) == State.RUNNING
					|| failed(proxyRack) || failed(paperRack), 6 * MINUTE);
			if (failed(proxyRack) || failed(paperRack)) {
				throw new AssertionError("Proxy " + snapshot(proxyRack) + " / Paper " + snapshot(paperRack) + "\n"
						+ String.join("\n", Serverworks.host().instances().peek(proxyRack).console().since(0, 200).lines()));
			}
			context.waitTicks(20);
			context.takeScreenshot("proxy_1_core");
			context.runOnClient(client -> client.player.closeContainer());
			context.getInput().lookAt(proxyCore);
			context.waitTicks(40);
			context.takeScreenshot("proxy_2_racks");

			// Through the proxy's Gateway: the player lands on the Paper server behind it.
			BlockPos gateway = proxyCore.offset(1, 0, 0);
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(gateway), Direction.NORTH, gateway, false);
				player.gameMode.useItemOn(player, player.level(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
			});
			context.waitFor(client -> Trips.onTrip() && client.level != null && client.player != null
					&& client.gui.screen() == null, 2 * MINUTE);
			context.waitFor(client -> snapshot(paperRack).playersOnline() == 1, MINUTE);
			context.waitTicks(60);
			context.takeScreenshot("proxy_3_on_paper_through_velocity");

			context.runOnClient(client -> Trips.goBack());
			context.waitFor(client -> client.hasSingleplayerServer() && client.level != null && client.player != null
					&& client.gui.screen() == null, 2 * MINUTE);
			Serverworks.host().instances().peek(proxyRack).stop();
			Serverworks.host().instances().peek(paperRack).stop();
			context.waitFor(client -> state(proxyRack) == State.STOPPED && state(paperRack) == State.STOPPED, 2 * MINUTE);
		}
	}

	/** A 5 x 3 x 3 rack. Front face: display, core, gateway. Drive bay on the west end, memory on the east. */
	private static void buildRack(ServerLevel level, BlockPos origin, Platform platform, boolean front) {
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
		if (front) {
			level.setBlockAndUpdate(origin.offset(1, 1, 0), facing(ModBlocks.GATEWAY, Direction.NORTH));
		}
		level.setBlockAndUpdate(origin.offset(-2, 1, 1), facing(ModBlocks.DRIVE_BAY, Direction.WEST));
		level.setBlockAndUpdate(origin.offset(2, 1, 1), facing(ModBlocks.MEMORY_BANK, Direction.EAST));
		level.setBlockAndUpdate(origin.offset(0, 2, 1), facing(ModBlocks.VENT, Direction.UP));
		level.setBlockAndUpdate(origin.offset(0, 1, 2), facing(ModBlocks.PLATFORM_MODULES.get(platform), Direction.SOUTH));
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
