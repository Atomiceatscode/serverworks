package dev.serverworks.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.serverworks.Serverworks;
import dev.serverworks.registry.ModBlocks;
import dev.serverworks.registry.ModItems;
import dev.serverworks.rack.CasingTier;
import dev.serverworks.rack.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * The Operator's Manual: a contents page, one page per topic with the items each one is about,
 * then recipe pages that draw every crafting grid.
 */
public final class ManualScreen extends Screen {
	private static final int WIDTH = 248;
	private static final int HEIGHT = 206;
	private static final int PADDING = 14;

	private static final int PAPER = 0xFF1B202B;
	private static final int PAPER_EDGE = 0xFF343C4F;
	private static final int ACCENT = 0xFF6FC3FF;
	private static final int TITLE = 0xFFE8ECF4;
	private static final int BODY = 0xFFC2CADB;
	private static final int MUTED = 0xFF8F9BB3;

	private static final int SLOT = 18;
	private static final int SLOT_FILL = 0xFF10141C;
	private static final int SLOT_EDGE = 0xFF3A4459;
	private static final int RECIPES_PER_PAGE = 4;

	/** One crafting recipe: nine grid cells for shaped recipes (null for empty), or the ingredients of a shapeless one. */
	private record Recipe(Item result, int count, boolean shaped, List<Item> items) {
		boolean isModule() {
			return BuiltInRegistries.ITEM.getKey(result).getPath().endsWith("_platform_module");
		}
	}

	/** A topic page shows its key's text; a recipe page shows its recipes; the modules page lists every Platform Module. */
	private record Page(String key, List<ItemLike> items, List<Recipe> recipes, boolean modules) {
		Page(String key, List<ItemLike> items) {
			this(key, items, List.of(), false);
		}

		boolean isRecipes() {
			return !recipes.isEmpty();
		}
	}

	private static final List<Page> TOPICS = List.of(
			new Page("welcome", List.of(ModItems.OPERATORS_MANUAL, ModBlocks.SERVER_CORE, ModBlocks.GATEWAY)),
			new Page("disks", List.of(ModItems.BLANK_DISK, ModBlocks.DISK_WRITER, ModItems.MOD_DISK, ModItems.PACK_CARTRIDGE)),
			new Page("building", List.of(ModBlocks.RACK_CASINGS.get(CasingTier.IRON), ModBlocks.RACK_CASINGS.get(CasingTier.DIAMOND),
					ModBlocks.RACK_CASINGS.get(CasingTier.NETHERITE), ModItems.WRENCH)),
			new Page("parts", List.of(ModBlocks.SERVER_CORE, ModBlocks.DRIVE_BAY, ModBlocks.MEMORY_BANK, ModBlocks.VENT,
					ModBlocks.GATEWAY, ModBlocks.STATUS_DISPLAY)),
			new Page("platforms", List.of(ModBlocks.PLATFORM_MODULES.get(Platform.VANILLA), ModBlocks.PLATFORM_MODULES.get(Platform.PAPER),
					ModBlocks.PLATFORM_MODULES.get(Platform.FABRIC), ModBlocks.PLATFORM_MODULES.get(Platform.NEOFORGE),
					ModBlocks.PLATFORM_MODULES.get(Platform.VELOCITY))),
			new Page("starting", List.of(ModBlocks.SERVER_CORE)),
			new Page("joining", List.of(ModBlocks.GATEWAY, ModBlocks.STATUS_DISPLAY)),
			new Page("proxies", List.of(ModItems.LINK_CARD, ModBlocks.PLATFORM_MODULES.get(Platform.VELOCITY),
					ModBlocks.PLATFORM_MODULES.get(Platform.BUNGEECORD))),
			new Page("modpacks", List.of(ModItems.PACK_CARTRIDGE)),
			new Page("limits", List.of(ModBlocks.MEMORY_BANK, ModBlocks.VENT)),
			new Page("trouble", List.of(ModItems.WRENCH)));

	private final List<Page> pages;
	private final int firstRecipePage;
	/** -1 is the contents page. */
	private int page = -1;
	private int left;
	private int top;

	public ManualScreen() {
		super(Component.translatable("item.serverworks.operators_manual"));
		List<Page> all = new ArrayList<>(TOPICS);
		firstRecipePage = all.size();
		List<Recipe> recipes = loadRecipes();
		List<Recipe> parts = recipes.stream().filter(r -> !r.isModule()).toList();
		List<Recipe> modules = recipes.stream().filter(Recipe::isModule).toList();
		for (int i = 0; i < parts.size(); i += RECIPES_PER_PAGE) {
			all.add(new Page("recipes", List.of(), parts.subList(i, Math.min(parts.size(), i + RECIPES_PER_PAGE)), false));
		}
		if (!modules.isEmpty()) {
			all.add(new Page("recipes", List.of(), modules, true));
		}
		pages = List.copyOf(all);
	}

	/** Reads the recipe list the data generator writes next to the textures. */
	private static List<Recipe> loadRecipes() {
		List<Recipe> recipes = new ArrayList<>();
		Identifier location = Identifier.fromNamespaceAndPath(Serverworks.MOD_ID, "manual_recipes.json");
		try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(location)) {
			for (JsonElement element : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("recipes")) {
				JsonObject recipe = element.getAsJsonObject();
				List<Item> items = new ArrayList<>();
				for (JsonElement item : recipe.getAsJsonArray("items")) {
					items.add(item.isJsonNull() ? null : item(item.getAsString()));
				}
				recipes.add(new Recipe(item(recipe.get("result").getAsString()), recipe.get("count").getAsInt(),
						"shaped".equals(recipe.get("shape").getAsString()), items));
			}
		} catch (Exception e) {
			Serverworks.LOGGER.warn("Could not read the manual's recipes", e);
		}
		return recipes;
	}

	private static Item item(String id) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
		return item == null ? Items.BARRIER : item;
	}

	@Override
	protected void init() {
		left = (width - WIDTH) / 2;
		top = (height - HEIGHT) / 2;
		if (page < 0) {
			int columnWidth = (WIDTH - 2 * PADDING - 6) / 2;
			int entries = firstRecipePage + (pages.size() > firstRecipePage ? 1 : 0);
			for (int i = 0; i < entries; i++) {
				int index = i;
				int column = i % 2;
				int row = i / 2;
				addRenderableWidget(Button.builder(Component.translatable("serverworks.manual." + pages.get(i).key() + ".short"),
						button -> show(index))
						.bounds(left + PADDING + column * (columnWidth + 6), top + 44 + row * 22, columnWidth, 20).build());
			}
		} else {
			addRenderableWidget(Button.builder(Component.literal("<"), button -> show(page - 1))
					.bounds(left + PADDING, top + HEIGHT - 28, 24, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("serverworks.manual.contents"), button -> show(-1))
					.bounds(left + WIDTH / 2 - 40, top + HEIGHT - 28, 80, 20).build());
			Button next = addRenderableWidget(Button.builder(Component.literal(">"), button -> show(page + 1))
					.bounds(left + WIDTH - PADDING - 24, top + HEIGHT - 28, 24, 20).build());
			next.active = page < pages.size() - 1;
		}
	}

	private void show(int index) {
		page = Math.max(-1, Math.min(pages.size() - 1, index));
		rebuildWidgets();
	}

	public int pageCount() {
		return pages.size();
	}

	/** Index of the first recipe page, for tests. */
	public int firstRecipePage() {
		return firstRecipePage;
	}

	public void showPage(int index) {
		show(index);
	}

	private Component title(int index) {
		if (pages.get(index).modules()) {
			return Component.translatable("serverworks.manual.recipes.modules");
		}
		return Component.translatable("serverworks.manual." + pages.get(index).key() + ".title");
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, PAPER_EDGE);
		graphics.fill(left, top, left + WIDTH, top + HEIGHT, PAPER);
		graphics.fill(left, top, left + WIDTH, top + 3, ACCENT);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int x = left + PADDING;
		int y = top + PADDING;
		if (page < 0) {
			graphics.text(font, Component.translatable("serverworks.manual.heading"), x, y, TITLE, false);
			graphics.text(font, Component.translatable("serverworks.manual.subheading"), x, y + 13, MUTED, false);
			return;
		}
		Page current = pages.get(page);
		graphics.text(font, title(page), x, y, TITLE, false);
		Component pageNumber = Component.translatable("serverworks.manual.page", page + 1, pages.size());
		graphics.text(font, pageNumber, left + WIDTH - PADDING - font.width(pageNumber), y, MUTED, false);
		if (current.modules()) {
			extractModules(graphics, current, x, y + 16, mouseX, mouseY);
			return;
		}
		if (current.isRecipes()) {
			extractRecipes(graphics, current, x, y + 16, mouseX, mouseY);
			return;
		}

		int iconY = y + 14;
		for (int i = 0; i < current.items().size(); i++) {
			graphics.item(new ItemStack(current.items().get(i)), x + i * 20, iconY);
		}
		int textY = iconY + 22;
		List<FormattedCharSequence> lines = new ArrayList<>();
		for (String paragraph : Component.translatable("serverworks.manual." + current.key() + ".body").getString().split("\n")) {
			lines.addAll(font.split(Component.literal(paragraph), WIDTH - 2 * PADDING));
			lines.add(FormattedCharSequence.EMPTY);
		}
		int maxLines = (HEIGHT - 34 - (textY - top)) / 10;
		for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
			graphics.text(font, lines.get(i), x, textY + i * 10, BODY, false);
		}
		for (int i = 0; i < current.items().size(); i++) {
			int ix = x + i * 20;
			if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iconY && mouseY < iconY + 16) {
				graphics.setTooltipForNextFrame(font, new ItemStack(current.items().get(i)), mouseX, mouseY);
			}
		}
	}

	/** Up to four recipes in a two by two layout, each a named grid, an arrow and the result. */
	private void extractRecipes(GuiGraphicsExtractor graphics, Page current, int x, int y, int mouseX, int mouseY) {
		int cellWidth = (WIDTH - 2 * PADDING) / 2;
		int cellHeight = 72;
		List<Recipe> recipes = current.recipes();
		for (int i = 0; i < recipes.size(); i++) {
			Recipe recipe = recipes.get(i);
			int cx = x + (i % 2) * cellWidth;
			int cy = y + (i / 2) * cellHeight;
			Component name = new ItemStack(recipe.result()).getHoverName();
			graphics.text(font, font.substrByWidth(name, cellWidth - 4).getString(), cx, cy, ACCENT, false);
			int gy = cy + 11;
			for (int cell = 0; cell < 9; cell++) {
				Item item = cell < recipe.items().size() ? recipe.items().get(cell) : null;
				slot(graphics, cx + (cell % 3) * SLOT, gy + (cell / 3) * SLOT, item, 1, mouseX, mouseY);
			}
			int arrowX = cx + 3 * SLOT + 5;
			graphics.text(font, "\u2192", arrowX, gy + SLOT + 5, MUTED, false);
			slot(graphics, arrowX + 12, gy + SLOT, recipe.result(), recipe.count(), mouseX, mouseY);
			if (!recipe.shaped()) {
				Component anyOrder = Component.translatable("serverworks.manual.recipes.shapeless");
			graphics.text(font, anyOrder, cx + cellWidth - 1 - font.width(anyOrder), gy + 2 * SLOT + 6, MUTED, false);
			}
		}
		if (page == pages.size() - 1 || !pages.get(page + 1).isRecipes() || pages.get(page + 1).modules()) {
			int noteY = y + ((recipes.size() + 1) / 2) * cellHeight + 2;
			List<FormattedCharSequence> lines = font.split(Component.translatable("serverworks.manual.recipes.disks"), WIDTH - 2 * PADDING);
			for (int i = 0; i < lines.size(); i++) {
				graphics.text(font, lines.get(i), x, noteY + i * 10, BODY, false);
			}
			int iconY = noteY + lines.size() * 10 + 4;
			slot(graphics, x, iconY, ModItems.BLANK_DISK, 1, mouseX, mouseY);
			graphics.text(font, "\u2192", x + SLOT + 5, iconY + 5, MUTED, false);
			slot(graphics, x + SLOT + 17, iconY, ModBlocks.DISK_WRITER.asItem(), 1, mouseX, mouseY);
			graphics.text(font, "\u2192", x + 2 * SLOT + 22, iconY + 5, MUTED, false);
			slot(graphics, x + 2 * SLOT + 34, iconY, ModItems.MOD_DISK, 1, mouseX, mouseY);
			slot(graphics, x + 3 * SLOT + 36, iconY, ModItems.PACK_CARTRIDGE, 1, mouseX, mouseY);
		}
	}

	/** Every Platform Module shares three ingredients and adds one of its own. */
	private void extractModules(GuiGraphicsExtractor graphics, Page current, int x, int y, int mouseX, int mouseY) {
		// The three shared ingredients, then each platform's own item and the module it makes.
		Recipe first = current.recipes().getFirst();
		List<Item> shared = first.items().subList(0, Math.min(3, first.items().size()));
		int rowY = y;
		int sx = x;
		for (Item item : shared) {
			slot(graphics, sx, rowY, item, 1, mouseX, mouseY);
			graphics.text(font, "+", sx + SLOT + 3, rowY + 5, MUTED, false);
			sx += SLOT + 12;
		}
		graphics.text(font, Component.translatable("serverworks.manual.recipes.modules.body"), sx, rowY + 5, BODY, false);
		int listY = rowY + SLOT + 4;
		int columnWidth = (WIDTH - 2 * PADDING) / 2;
		int rows = (current.recipes().size() + 1) / 2;
		for (int i = 0; i < current.recipes().size(); i++) {
			Recipe recipe = current.recipes().get(i);
			int cx = x + (i / rows) * columnWidth;
			int cy = listY + (i % rows) * 17;
			Item own = recipe.items().size() > 3 ? recipe.items().get(3) : null;
			if (own != null) {
				graphics.item(new ItemStack(own), cx, cy);
				hover(graphics, own, cx, cy, mouseX, mouseY);
			}
			graphics.text(font, "\u2192", cx + 19, cy + 4, MUTED, false);
			graphics.item(new ItemStack(recipe.result()), cx + 29, cy);
			hover(graphics, recipe.result(), cx + 29, cy, mouseX, mouseY);
			String id = BuiltInRegistries.ITEM.getKey(recipe.result()).getPath().replace("_platform_module", "");
			Platform platform = Platform.byId(id);
			Component name = platform == null ? new ItemStack(recipe.result()).getHoverName() : Component.translatable(platform.translationKey());
			graphics.text(font, name, cx + 48, cy + 4, TITLE, false);
		}
	}

	/** Draws one inventory-style slot with an item and its count. */
	private void slot(GuiGraphicsExtractor graphics, int x, int y, Item item, int count, int mouseX, int mouseY) {
		graphics.fill(x, y, x + SLOT, y + SLOT, SLOT_EDGE);
		graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, SLOT_FILL);
		if (item == null) {
			return;
		}
		ItemStack stack = new ItemStack(item, count);
		graphics.item(stack, x + 1, y + 1);
		graphics.itemDecorations(font, stack, x + 1, y + 1);
		hover(graphics, item, x + 1, y + 1, mouseX, mouseY);
	}

	private void hover(GuiGraphicsExtractor graphics, Item item, int x, int y, int mouseX, int mouseY) {
		if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
			graphics.setTooltipForNextFrame(font, new ItemStack(item), mouseX, mouseY);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
