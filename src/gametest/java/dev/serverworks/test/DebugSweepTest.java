package dev.serverworks.test;

import dev.serverworks.Serverworks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A sweep over everything Serverworks adds: every item and block has a name and a model,
 * every recipe, loot table and advancement loaded, and every item is drawn on one screen
 * to look over for missing textures.
 */
public final class DebugSweepTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		List<String> problems = new ArrayList<>();
		List<Item> items = BuiltInRegistries.ITEM.stream()
				.filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Serverworks.MOD_ID)).toList();
		List<Block> blocks = BuiltInRegistries.BLOCK.stream()
				.filter(block -> BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(Serverworks.MOD_ID)).toList();

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			// Items only have their data once a world is loaded.
			context.runOnClient(client -> {
				Language language = Language.getInstance();
				var models = client.getModelManager();
				var missingItem = models.getItemModel(Identifier.fromNamespaceAndPath(Serverworks.MOD_ID, "no_such_model"));
				for (Item item : items) {
					if (!language.has(item.getDescriptionId())) {
						problems.add("item without a name: " + item.getDescriptionId());
					}
					Identifier model = new ItemStack(item).get(DataComponents.ITEM_MODEL);
					if (model == null || models.getItemModel(model) == missingItem) {
						problems.add("item without a model: " + BuiltInRegistries.ITEM.getKey(item));
					}
				}
				var states = models.getBlockStateModelSet();
				for (Block block : blocks) {
					if (!language.has(block.getDescriptionId())) {
						problems.add("block without a name: " + block.getDescriptionId());
					}
					for (BlockState state : block.getStateDefinition().getPossibleStates()) {
						if (states.get(state) == states.missingModel()) {
							problems.add("block state without a model: " + state);
							break;
						}
					}
				}
			});

			List<String> serverProblems = world.getServer().computeOnServer(server -> {
				List<String> found = new ArrayList<>();
				for (String id : dataFiles("recipe")) {
					var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Serverworks.MOD_ID, id));
					if (server.getRecipeManager().byKey(key).isEmpty()) {
						found.add("recipe did not load: " + id);
					}
				}
				for (Block block : blocks) {
					var table = block.getLootTable();
					if (table.isEmpty() || server.reloadableRegistries().getLootTable(table.get()) == LootTable.EMPTY) {
						found.add("block without a loot table: " + BuiltInRegistries.BLOCK.getKey(block));
					}
				}
				for (String id : dataFiles("advancement")) {
					if (server.getAdvancements().get(Identifier.fromNamespaceAndPath(Serverworks.MOD_ID, id)) == null) {
						found.add("advancement did not load: " + id);
					}
				}
				// Crafting a bay into the next tier keeps what it holds, and sticks craft up to 16 GB.
				var level = server.overworld();
				ItemStack bay = new ItemStack(dev.serverworks.registry.ModBlocks.DRIVE_BAY);
				bay.set(DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(
						List.of(new ItemStack(dev.serverworks.registry.ModItems.CONFIG_CARD))));
				ItemStack advanced = craft(server, level, List.of(bay, new ItemStack(net.minecraft.world.item.Items.DIAMOND)));
				var kept = advanced.get(DataComponents.CONTAINER);
				if (!advanced.is(dev.serverworks.registry.ModBlocks.ADVANCED_DRIVE_BAY.asItem()) || kept == null
						|| kept.nonEmptyItemCopyStream().count() != 1) {
					found.add("crafting an Advanced Drive Bay lost its contents: " + advanced + " " + kept);
				}
				ItemStack elite = craft(server, level, List.of(advanced, new ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT)));
				if (!elite.is(dev.serverworks.registry.ModBlocks.ELITE_DRIVE_BAY.asItem())) {
					found.add("an Elite Drive Bay does not craft: " + elite);
				}
				ItemStack stick = craft(server, level, List.of(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT),
						new ItemStack(net.minecraft.world.item.Items.REDSTONE), new ItemStack(net.minecraft.world.item.Items.REDSTONE),
						new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET)));
				int[] sizes = {1, 2, 4, 8, 16};
				Item[] extras = {null, net.minecraft.world.item.Items.GOLD_INGOT, net.minecraft.world.item.Items.DIAMOND,
						net.minecraft.world.item.Items.EMERALD, net.minecraft.world.item.Items.NETHERITE_SCRAP};
				for (int i = 0; i < sizes.length; i++) {
					if (i > 0) {
						stick = craft(server, level, List.of(stick.copy(), stick.copy(), new ItemStack(extras[i])));
					}
					if (!stick.is(dev.serverworks.registry.ModItems.MEMORY_STICKS.get(sizes[i]))) {
						found.add("the " + sizes[i] + " GB RAM Stick does not craft: " + stick);
						break;
					}
				}
				// A Creative player breaking a full bay still gets it back with its disks.
				var player = server.getPlayerList().getPlayers().getFirst();
				player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
				net.minecraft.core.BlockPos bayPos = player.blockPosition().east(3);
				level.setBlockAndUpdate(bayPos, dev.serverworks.registry.ModBlocks.DRIVE_BAY.defaultBlockState());
				((dev.serverworks.machine.DriveBayBlockEntity) level.getBlockEntity(bayPos))
						.setItem(0, new ItemStack(dev.serverworks.registry.ModItems.CONFIG_CARD));
				player.gameMode.destroyBlock(bayPos);
				boolean dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
						new net.minecraft.world.phys.AABB(bayPos).inflate(2)).stream().anyMatch(entity -> {
							var contents = entity.getItem().get(DataComponents.CONTAINER);
							return contents != null && contents.nonEmptyItemCopyStream().count() == 1;
						});
				if (!dropped) {
					found.add("breaking a full Drive Bay in Creative lost its disks");
				}
				player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				return found;
			});
			problems.addAll(serverProblems);

			context.setScreen(() -> new AllItemsScreen(items));
			context.waitTicks(10);
			context.takeScreenshot("sweep_all_items");
			context.setScreen(() -> null);
		}

		if (!problems.isEmpty()) {
			throw new AssertionError("Sweep found " + problems.size() + " problems:\n" + String.join("\n", problems));
		}
		Serverworks.LOGGER.info("Sweep checked {} items and {} blocks", items.size(), blocks.size());
	}

	/** What a crafting grid holding these items (left to right, top row first) makes, or empty. */
	private static ItemStack craft(net.minecraft.server.MinecraftServer server, net.minecraft.server.level.ServerLevel level,
			List<ItemStack> items) {
		List<ItemStack> grid = new ArrayList<>(items);
		while (grid.size() < 9) {
			grid.add(ItemStack.EMPTY);
		}
		var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, grid);
		return server.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level)
				.map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
	}

	/** Names of the JSON files under data/serverworks/<folder>, as ids without the extension. */
	private static List<String> dataFiles(String folder) {
		Path root = Path.of(System.getProperty("user.dir")).getParent().getParent().getParent()
				.resolve("src/main/resources/data/serverworks").resolve(folder);
		if (!Files.isDirectory(root)) {
			throw new AssertionError("No data folder at " + root);
		}
		try (Stream<Path> files = Files.walk(root)) {
			return files.filter(p -> p.toString().endsWith(".json"))
					.map(p -> root.relativize(p).toString().replace('\\', '/').replace(".json", "")).toList();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	/** Every Serverworks item in a grid, with its name on hover. */
	private static final class AllItemsScreen extends Screen {
		private final List<Item> items;

		AllItemsScreen(List<Item> items) {
			super(Component.literal("All Serverworks items"));
			this.items = items;
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			super.extractRenderState(graphics, mouseX, mouseY, partialTick);
			int columns = 12;
			int left = width / 2 - columns * 20 / 2;
			graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
			for (int i = 0; i < items.size(); i++) {
				int x = left + (i % columns) * 20;
				int y = 28 + (i / columns) * 20;
				graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF2A2F3A);
				graphics.item(new ItemStack(items.get(i)), x, y);
			}
		}
	}
}
