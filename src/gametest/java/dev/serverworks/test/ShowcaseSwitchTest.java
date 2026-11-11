package dev.serverworks.test;

import dev.serverworks.registry.ModBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

/** Creates a world through the real Create World screen with the Showcase switch on. */
public final class ShowcaseSwitchTest implements FabricClientGameTest {
	private static final int MINUTE = 20 * 60;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		context.runOnClient(client -> CreateWorldScreen.openFresh(client, () -> client.gui.setScreen(new TitleScreen())));
		context.waitForScreen(CreateWorldScreen.class);
		context.waitTicks(20);
		context.clickScreenButton("serverworks.create_world.showcase");
		context.waitTicks(5);
		context.takeScreenshot("showcase_switch_on");
		boolean ready = context.computeOnClient(client -> {
			var state = ((CreateWorldScreen) client.gui.screen()).getUiState();
			return state.getGameMode() == net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode.CREATIVE
					&& state.isAllowCommands();
		});
		if (!ready) {
			throw new AssertionError("The Showcase switch should pick Creative and commands");
		}
		context.clickScreenButton("selectWorld.create");
		context.waitFor(client -> client.level != null && client.player != null && client.gui.screen() == null
				&& client.hasSingleplayerServer(), 2 * MINUTE);

		// The showcase appears around spawn on first join.
		// The showcase appears around the player on first join. Read the client's copy of the world:
		// the test framework holds the server thread while this check runs.
		context.waitFor(client -> {
			BlockPos origin = client.player.blockPosition();
			for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-18, -1, 0), origin.offset(18, 3, 24))) {
				if (client.level.getBlockState(pos).is(ModBlocks.SERVER_CORE)) {
					return true;
				}
			}
			return false;
		}, MINUTE);
		GameType mode = context.computeOnClient(client -> client.gameMode.getPlayerMode());
		if (mode != GameType.CREATIVE) {
			throw new AssertionError("A showcase world should start in Creative, not " + mode);
		}
		context.waitTicks(100);
		context.takeScreenshot("showcase_switch_world");

		// This world was made outside the test framework, so leave it by hand.
		context.runOnClient(client -> client.disconnectFromWorld(Component.literal("Test finished")));
		context.waitFor(client -> client.level == null, MINUTE);
		// Let the world's server thread finish shutting down; the framework checks for it after each test.
		context.waitFor(client -> !net.fabricmc.fabric.impl.client.gametest.threading.ThreadingImpl.isServerRunning, MINUTE);
		context.setScreen(TitleScreen::new);
	}
}
