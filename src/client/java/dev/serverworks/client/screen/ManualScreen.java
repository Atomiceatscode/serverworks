package dev.serverworks.client.screen;

import dev.serverworks.registry.ModBlocks;
import dev.serverworks.registry.ModItems;
import dev.serverworks.rack.CasingTier;
import dev.serverworks.rack.Platform;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;

/** The Operator's Manual: a contents page and one page per topic, with the items each one is about. */
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

	private record Page(String key, List<ItemLike> items) {
	}

	private static final List<Page> PAGES = List.of(
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

	/** -1 is the contents page. */
	private int page = -1;
	private int left;
	private int top;

	public ManualScreen() {
		super(Component.translatable("item.serverworks.operators_manual"));
	}

	@Override
	protected void init() {
		left = (width - WIDTH) / 2;
		top = (height - HEIGHT) / 2;
		if (page < 0) {
			int columnWidth = (WIDTH - 2 * PADDING - 6) / 2;
			for (int i = 0; i < PAGES.size(); i++) {
				int index = i;
				int column = i % 2;
				int row = i / 2;
				addRenderableWidget(Button.builder(Component.translatable("serverworks.manual." + PAGES.get(i).key() + ".short"),
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
			next.active = page < PAGES.size() - 1;
		}
	}

	private void show(int index) {
		page = Math.max(-1, Math.min(PAGES.size() - 1, index));
		rebuildWidgets();
	}

	public int pageCount() {
		return PAGES.size();
	}

	public void showPage(int index) {
		show(index);
	}

	private static Component title(int index) {
		return Component.translatable("serverworks.manual." + PAGES.get(index).key() + ".title");
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
		Page current = PAGES.get(page);
		graphics.text(font, title(page), x, y, TITLE, false);
		Component pageNumber = Component.translatable("serverworks.manual.page", page + 1, PAGES.size());
		graphics.text(font, pageNumber, left + WIDTH - PADDING - font.width(pageNumber), y, MUTED, false);

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

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
