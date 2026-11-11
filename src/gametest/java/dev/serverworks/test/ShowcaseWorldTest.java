package dev.serverworks.test;

import dev.serverworks.showcase.Showcase;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.concurrent.TimeUnit;

/**
 * Not a test: makes the "Serverworks Showcase" world, a creative superflat world with the
 * showcase built at spawn. Runs only when named:
 * SERVERWORKS_GAMETEST_ONLY=ShowcaseWorldTest ./gradlew runClientGameTest
 * The world is left in build/run/clientGameTest/saves/.
 */
public final class ShowcaseWorldTest implements FabricClientGameTest {
	public static final String WORLD_NAME = "Serverworks Showcase";

	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(state -> {
					state.setName(WORLD_NAME);
					state.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
					state.setAllowCommands(true);
					for (WorldCreationUiState.WorldTypeEntry type : state.getNormalPresetList()) {
						if (type.preset() != null && type.preset().is(WorldPresets.FLAT)) {
							state.setWorldType(type);
						}
					}
				})
				.create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();

			BlockPos origin = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition());
			var disks = server.computeOnServer(s -> Showcase.setUpWorld(s, s.getPlayerList().getPlayers().getFirst()));
			try {
				disks.get(60, TimeUnit.SECONDS);
			} catch (Exception e) {
				throw new AssertionError("The showcase disks never arrived", e);
			}
			context.waitTicks(60);
			context.takeScreenshot("showcase_spawn_view");
			// A look from above to check the whole layout, then back to spawn so the world saves there.
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
				player.teleportTo(origin.getX() + 0.5, origin.getY() + 14, origin.getZ() - 8.5);
			});
			context.waitTicks(10);
			context.getInput().lookAt(origin.offset(0, 0, 13));
			context.waitTicks(60);
			context.takeScreenshot("showcase_from_above");
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				player.getAbilities().flying = false;
				player.onUpdateAbilities();
				player.teleportTo(player.level(), origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5,
						java.util.Set.of(), 0, 15, true);
			});
			context.waitTicks(20);
		}
	}
}
