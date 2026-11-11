package dev.serverworks.client.screen;

import dev.serverworks.Serverworks;
import dev.serverworks.catalog.CatalogException;
import dev.serverworks.catalog.SearchQuery;
import dev.serverworks.catalog.SearchResult;
import dev.serverworks.catalog.model.ProjectSummary;
import dev.serverworks.catalog.model.ProjectType;
import dev.serverworks.client.icon.IconCache;
import dev.serverworks.item.ModDiskItem;
import dev.serverworks.machine.DiskWriterMenu;
import dev.serverworks.net.SelectProjectPayload;
import dev.serverworks.net.SelectResultPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;

/**
 * The Disk Writer: search the live catalog on the left, see the picked project on the
 * right, and take the written disk from the output slot.
 *
 * <p>Searching happens from this client, so the load on the catalog is spread across
 * players. Writing is confirmed by the server, which looks the project up itself.
 */
public final class DiskWriterScreen extends AbstractContainerScreen<DiskWriterMenu> {
	private static final Identifier BACKGROUND = Serverworks.id("textures/gui/container/disk_writer.png");
	private static final int TEXTURE_WIDTH = 512;
	private static final int TEXTURE_HEIGHT = 256;
	private static final int IMAGE_WIDTH = 276;
	private static final int IMAGE_HEIGHT = 222;

	// Sprites kept beside the background on the same sheet.
	private static final int PLACEHOLDER_U = 288;
	private static final int PLACEHOLDER_V = 0;
	private static final int ARROW_LIT_U = 288;
	private static final int ARROW_LIT_V = 40;
	private static final int ARROW_X = 218;
	private static final int ARROW_Y = 107;
	private static final int ARROW_W = 22;
	private static final int ARROW_H = 15;

	private static final int LIST_X = 8;
	private static final int LIST_Y = 36;
	private static final int LIST_W = 170;
	private static final int LIST_H = 92;
	private static final int ROW_H = 23;
	private static final int SCROLLBAR_X = 180;
	private static final int SCROLLBAR_W = 6;
	private static final int DETAIL_X = 190;
	private static final int DETAIL_W = 78;

	private static final int PAGE_SIZE = 20;
	private static final int SEARCH_DELAY_TICKS = 8;

	private static final int TEXT = 0xFF404040;
	private static final int TEXT_MUTED = 0xFF6E6E6E;
	private static final int TEXT_LIGHT = 0xFFE8ECF4;
	private static final int TEXT_DIM = 0xFF8F9BB3;
	private static final int TEXT_ERROR = 0xFFFF7A6B;
	private static final int TEXT_OK = 0xFF63E08A;
	private static final int DROP_IDLE = 0xFF5A6684;
	private static final int TEXT_PIN = 0xFF6FC3FF;
	private static final int STAR_ON = 0xFFFFC84A;
	private static final int DROP_LIT = 0xFF6FC3FF;
	private static final int ROW_HOVER = 0xFF2E3648;
	private static final int ROW_SELECTED = 0xFF3A4A6B;
	private static final int ROW_SELECTED_EDGE = 0xFF6FA8FF;
	private static final int SCROLL_THUMB = 0xFF8792AB;
	private static final int SCROLL_THUMB_HOT = 0xFFB9C4DE;

	private static final List<@Nullable ProjectType> TYPE_FILTERS = java.util.Arrays.asList(
			null, ProjectType.MOD, ProjectType.PLUGIN, ProjectType.DATAPACK, ProjectType.MODPACK);

	private @Nullable EditBox search;
	private @Nullable Button typeButton;
	private @Nullable Button sortButton;
	private int typeIndex;
	private SearchQuery.Sort sort = SearchQuery.Sort.RELEVANCE;

	private final List<ProjectSummary> results = new ArrayList<>();
	private int totalHits;
	private boolean loading;
	private @Nullable Component listMessage;
	/** Bumped on every new search so answers to superseded searches are ignored. */
	private int generation;
	private int searchCountdown = -1;
	private String lastSearchedText = "";

	private double scroll;
	private int lastMouseX;
	private int lastMouseY;
	private boolean draggingScrollbar;

	/** The row under the mouse when the button went down; a click picks it, a drag carries it. */
	private @Nullable ProjectSummary pressed;
	private double pressX;
	private double pressY;
	private boolean draggingProject;

	private @Nullable ProjectSummary selected;
	/** While true the list shows starred projects instead of searching the catalog. */
	private boolean favouritesMode;
	private @Nullable Button starButton;
	/** The version the disk will be pinned to, or null for the newest compatible one. */
	private @Nullable String pinnedId;
	private @Nullable String pinnedName;
	/** While true the list shows the selected project's versions instead of search results. */
	private boolean versionMode;
	private final List<dev.serverworks.catalog.model.ProjectVersion> versions = new ArrayList<>();
	private boolean versionsLoading;
	private @Nullable Component versionsMessage;
	private double versionScroll;
	private SelectState selectState = SelectState.NONE;
	private @Nullable Component selectMessage;

	public DiskWriterScreen(DiskWriterMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = DiskWriterMenu.INVENTORY_X;
		this.inventoryLabelY = 130;
	}

	@Override
	protected void init() {
		super.init();
		String previous = search == null ? "" : search.getValue();
		search = new EditBox(font, leftPos + 9, topPos + 18, 122, 14, Component.translatable("serverworks.disk_writer.search"));
		search.setMaxLength(64);
		search.setHint(Component.translatable("serverworks.disk_writer.search").withStyle(ChatFormatting.DARK_GRAY));
		search.setValue(previous);
		search.setResponder(text -> searchCountdown = SEARCH_DELAY_TICKS);
		addRenderableWidget(search);

		starButton = addRenderableWidget(Button.builder(Component.literal(favouritesMode ? "\u2605" : "\u2606"), button -> {
			favouritesMode = !favouritesMode;
			button.setMessage(Component.literal(favouritesMode ? "\u2605" : "\u2606"));
			startSearch();
		}).bounds(leftPos + 133, topPos + 17, 18, 16)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("serverworks.disk_writer.favourites")))
				.build());
		typeButton = addRenderableWidget(Button.builder(typeLabel(), button -> {
			typeIndex = (typeIndex + 1) % TYPE_FILTERS.size();
			button.setMessage(typeLabel());
			startSearch();
		}).bounds(leftPos + 153, topPos + 16, 56, 18).build());
		sortButton = addRenderableWidget(Button.builder(sortLabel(), button -> {
			SearchQuery.Sort[] all = SearchQuery.Sort.values();
			sort = all[(sort.ordinal() + 1) % all.length];
			button.setMessage(sortLabel());
			startSearch();
		}).bounds(leftPos + 211, topPos + 16, 57, 18).build());

		if (results.isEmpty() && !loading) {
			startSearch();
		}
	}

	@Override
	protected void setInitialFocus() {
		if (search != null) {
			setInitialFocus(search);
		}
	}

	private Component typeLabel() {
		ProjectType type = TYPE_FILTERS.get(typeIndex);
		return type == null ? Component.translatable("serverworks.disk_writer.type.all") : ModDiskItem.typeName(type);
	}

	private Component sortLabel() {
		return Component.translatable("serverworks.disk_writer.sort." + sort.apiName());
	}

	/** How many catalog results are currently listed. */
	public int resultCount() {
		return results.size();
	}

	/** Title of the picked project, or an empty string; for tests. */
	public String selectedTitle() {
		return selected == null ? "" : selected.title();
	}

	/** Author of a result row, or an empty string; for tests. */
	public String resultAuthor(int row) {
		return row >= 0 && row < results.size() ? results.get(row).author() : "";
	}

	/** What the search box holds; for tests. */
	public String searchText() {
		return search == null ? "" : search.getValue();
	}

	/** Title of a result row, or an empty string; for tests. */
	public String resultTitle(int row) {
		return row >= 0 && row < results.size() ? results.get(row).title() : "";
	}

	public boolean isSearching() {
		return loading;
	}

	// ---- searching ----

	@Override
	protected void containerTick() {
		super.containerTick();
		if (searchCountdown > 0 && --searchCountdown == 0) {
			searchCountdown = -1;
			if (search != null && !search.getValue().strip().equals(lastSearchedText)) {
				startSearch();
			}
		}
	}

	private void startSearch() {
		versionMode = false;
		results.clear();
		totalHits = 0;
		scroll = 0;
		listMessage = null;
		lastSearchedText = search == null ? "" : search.getValue().strip();
		fetch(0);
	}

	private void fetch(int offset) {
		int requested = ++generation;
		if (favouritesMode) {
			if (offset == 0) {
				List<ProjectSummary> starred = dev.serverworks.client.Favourites.matching(lastSearchedText);
				onSearchDone(new SearchResult(starred, 0, starred.size(), starred.size()), null);
				if (starred.isEmpty()) {
					listMessage = Component.translatable("serverworks.disk_writer.favourites.empty");
				}
			}
			return;
		}
		var collection = SearchQuery.collectionId(lastSearchedText);
		if (collection.isPresent()) {
			if (offset > 0) {
				return;
			}
			loading = true;
			try {
				Serverworks.catalog().collection(collection.get()).whenComplete((list, error) -> minecraft.execute(() -> {
					if (requested == generation) {
						onSearchDone(list == null ? null : new SearchResult(list, 0, list.size(), list.size()), error);
					}
				}));
			} catch (RuntimeException e) {
				onSearchDone(null, e);
			}
			return;
		}
		loading = true;
		ProjectType type = TYPE_FILTERS.get(typeIndex);
		SearchQuery query = SearchQuery.parse(lastSearchedText)
				.withTypes(type == null ? List.of() : List.of(type))
				.withServerCapable(true)
				.withSort(sort)
				.withPage(offset, PAGE_SIZE);
		try {
			Serverworks.catalog().search(query).whenComplete((result, error) -> minecraft.execute(() -> {
				if (requested == generation) {
					onSearchDone(result, error);
				}
			}));
		} catch (RuntimeException e) {
			onSearchDone(null, e);
		}
	}

	private void onSearchDone(@Nullable SearchResult result, @Nullable Throwable error) {
		loading = false;
		if (error != null || result == null) {
			listMessage = Component.translatable(errorKey(error));
			return;
		}
		results.addAll(result.hits());
		totalHits = result.totalHits();
		listMessage = results.isEmpty() ? Component.translatable("serverworks.disk_writer.no_results") : null;
	}

	private static String errorKey(@Nullable Throwable error) {
		Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
		if (cause instanceof CatalogException catalog && catalog.kind() == CatalogException.Kind.RATE_LIMITED) {
			return "serverworks.disk_writer.error.rate_limited";
		}
		if (cause instanceof CatalogException catalog && catalog.kind() == CatalogException.Kind.NOT_FOUND) {
			// Searches only 404 when an @author does not exist.
			return "serverworks.disk_writer.error.no_author";
		}
		return "serverworks.disk_writer.error.unavailable";
	}

	private void loadMoreIfNeeded() {
		if (loading || results.size() >= totalHits || results.isEmpty()) {
			return;
		}
		if (scroll + LIST_H >= contentHeight() - 2 * ROW_H) {
			fetch(results.size());
		}
	}

	// ---- selecting ----

	private void select(ProjectSummary project) {
		if (selected == null || !selected.id().equals(project.id())) {
			pinnedId = null;
			pinnedName = null;
		}
		selected = project;
		selectState = SelectState.PENDING;
		selectMessage = null;
		ClientPlayNetworking.send(new SelectProjectPayload(menu.containerId, project.id(), pinnedId == null ? "" : pinnedId));
	}

	// ---- versions ----

	/** Shows the selected project's versions for this Minecraft version in the list. */
	private void openVersions() {
		if (selected == null) {
			return;
		}
		versionMode = true;
		versionScroll = 0;
		versions.clear();
		versionsLoading = true;
		versionsMessage = null;
		String projectId = selected.id();
		String minecraft = net.minecraft.SharedConstants.getCurrentVersion().name();
		try {
			Serverworks.catalog().versions(projectId, List.of(), List.of(minecraft)).whenComplete((list, error) ->
					this.minecraft.execute(() -> {
						if (selected == null || !selected.id().equals(projectId)) {
							return;
						}
						versionsLoading = false;
						if (error != null || list == null) {
							versionsMessage = Component.translatable(errorKey(error));
							return;
						}
						versions.addAll(list);
						if (versions.isEmpty()) {
							versionsMessage = Component.translatable("serverworks.disk_writer.versions.none", minecraft);
						}
					}));
		} catch (RuntimeException e) {
			versionsLoading = false;
			versionsMessage = Component.translatable(errorKey(e));
		}
	}

	private void closeVersions() {
		versionMode = false;
	}

	/** Row 0 is "newest"; row n is versions[n - 1]. */
	private void pickVersion(int row) {
		if (selected == null) {
			return;
		}
		if (row == 0) {
			pinnedId = null;
			pinnedName = null;
		} else {
			var version = versions.get(row - 1);
			pinnedId = version.id();
			pinnedName = version.versionNumber();
		}
		closeVersions();
		select(selected);
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	private int versionRows() {
		return versions.size() + 1;
	}

	private int versionRowAt(double mouseX, double mouseY) {
		int x = leftPos + LIST_X;
		int y = topPos + LIST_Y;
		if (mouseX < x || mouseX >= x + LIST_W || mouseY < y || mouseY >= y + LIST_H) {
			return -1;
		}
		int row = (int) ((mouseY - y + versionScroll) / ROW_H);
		return row >= 0 && row < versionRows() && !versionsLoading ? row : -1;
	}

	private void extractVersions(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = leftPos + LIST_X;
		int y = topPos + LIST_Y;
		graphics.enableScissor(x, y, x + LIST_W, y + LIST_H);
		int hovered = versionRowAt(mouseX, mouseY);
		if (!versionsLoading) {
			for (int i = 0; i < versionRows(); i++) {
				int rowY = y + i * ROW_H - (int) versionScroll;
				if (rowY + ROW_H < y || rowY > y + LIST_H) {
					continue;
				}
				String id = i == 0 ? null : versions.get(i - 1).id();
				boolean current = java.util.Objects.equals(id, pinnedId);
				if (current) {
					graphics.fill(x, rowY, x + LIST_W, rowY + ROW_H - 1, ROW_SELECTED);
					graphics.fill(x, rowY, x + 2, rowY + ROW_H - 1, ROW_SELECTED_EDGE);
				} else if (i == hovered) {
					graphics.fill(x, rowY, x + LIST_W, rowY + ROW_H - 1, ROW_HOVER);
				}
				int textW = LIST_W - 12;
				if (i == 0) {
					graphics.text(font, clip(Component.translatable("serverworks.disk_writer.versions.newest").getString(), textW),
							x + 6, rowY + 3, TEXT_LIGHT, false);
					graphics.text(font, clip(Component.translatable("serverworks.disk_writer.versions.newest.detail").getString(),
							textW), x + 6, rowY + 13, TEXT_DIM, false);
				} else {
					var version = versions.get(i - 1);
					graphics.text(font, clip(version.versionNumber(), textW), x + 6, rowY + 3, TEXT_LIGHT, false);
					graphics.text(font, clip(versionDetail(version), textW), x + 6, rowY + 13, TEXT_DIM, false);
				}
			}
		}
		graphics.disableScissor();
		Component message = versionsLoading ? Component.translatable("serverworks.disk_writer.loading") : versionsMessage;
		if (message != null) {
			int line = y + LIST_H / 2 + (versionsLoading ? -12 : 4);
			for (var part : font.split(message, LIST_W - 16)) {
				graphics.centeredText(font, part, x + LIST_W / 2, line, versionsLoading ? TEXT_DIM : TEXT_ERROR);
				line += 10;
			}
		}
	}

	private static String versionDetail(dev.serverworks.catalog.model.ProjectVersion version) {
		StringBuilder loaders = new StringBuilder();
		for (String loader : version.loaders()) {
			if (loaders.length() > 0) {
				loaders.append(", ");
			}
			loaders.append(Character.toUpperCase(loader.charAt(0))).append(loader.substring(1));
		}
		String channel = Component.translatable("serverworks.disk_writer.versions."
				+ version.channel().name().toLowerCase(Locale.ROOT)).getString();
		String date = version.datePublished().length() >= 10 ? version.datePublished().substring(0, 10) : "";
		return channel + " · " + date + " · " + loaders;
	}

	/** The "Newest version" / "Pinned: x" line under the title, which opens the version list. */
	private boolean overVersionLink(double mouseX, double mouseY) {
		if (selected == null) {
			return false;
		}
		int y = topPos + LIST_Y + 38;
		int x = leftPos + DETAIL_X;
		return mouseX >= x + 2 && mouseX < x + DETAIL_W - 2 && mouseY >= y - 1 && mouseY < y + 9;
	}

	private Component versionLink() {
		return pinnedName != null
				? Component.translatable("serverworks.disk_writer.versions.pinned", pinnedName)
				: Component.translatable("serverworks.disk_writer.versions.link");
	}

	/** Called when the server answers a selection. */
	public void onSelectResult(SelectResultPayload payload) {
		if (payload.containerId() != menu.containerId || selected == null || !selected.id().equals(payload.projectId())) {
			return;
		}
		if (payload.outcome() == SelectResultPayload.Outcome.READY) {
			selectState = SelectState.READY;
			selectMessage = null;
		} else {
			selectState = SelectState.FAILED;
			selectMessage = Component.translatable(payload.outcome().translationKey());
		}
	}

	// ---- drawing ----

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight,
				TEXTURE_WIDTH, TEXTURE_HEIGHT);
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		if (versionMode) {
			extractVersions(graphics, mouseX, mouseY);
		} else {
			extractList(graphics, mouseX, mouseY);
			extractScrollbar(graphics, mouseX, mouseY);
		}
		extractDetail(graphics);
		loadMoreIfNeeded();
	}

	private void extractList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = leftPos + LIST_X;
		int y = topPos + LIST_Y;
		graphics.enableScissor(x, y, x + LIST_W, y + LIST_H);
		int first = Math.max(0, (int) (scroll / ROW_H));
		int last = Math.min(results.size(), first + LIST_H / ROW_H + 2);
		int hovered = rowAt(mouseX, mouseY);
		for (int i = first; i < last; i++) {
			ProjectSummary project = results.get(i);
			int rowY = y + i * ROW_H - (int) scroll;
			boolean isSelected = selected != null && selected.id().equals(project.id());
			if (isSelected) {
				graphics.fill(x, rowY, x + LIST_W, rowY + ROW_H - 1, ROW_SELECTED);
				graphics.fill(x, rowY, x + 2, rowY + ROW_H - 1, ROW_SELECTED_EDGE);
			} else if (i == hovered) {
				graphics.fill(x, rowY, x + LIST_W, rowY + ROW_H - 1, ROW_HOVER);
			}
			extractIcon(graphics, project.iconUrl(), x + 5, rowY + 3, 16);
			int textX = x + 26;
			int textW = LIST_W - 42;
			boolean starred = dev.serverworks.client.Favourites.contains(project.id());
			if (starred || i == hovered) {
				boolean overStar = i == hovered && overStar(i, mouseX, mouseY);
				graphics.text(font, starred ? "\u2605" : "\u2606", x + LIST_W - 13, rowY + 7,
						starred ? STAR_ON : overStar ? TEXT_LIGHT : TEXT_DIM, false);
			}
			graphics.text(font, clip(project.title(), textW), textX, rowY + 3, TEXT_LIGHT, false);
			graphics.text(font, clip(subtitle(project), textW), textX, rowY + 13, TEXT_DIM, false);
			if (i == hovered && overAuthor(i, mouseX, mouseY)) {
				// The author's name is a link to their other projects.
				int nameW = Math.min(font.width(project.author()), textW);
				graphics.text(font, clip(project.author(), textW), textX, rowY + 13, TEXT_LIGHT, false);
				graphics.fill(textX, rowY + 22, textX + nameW, rowY + 23, TEXT_LIGHT);
			}
		}
		graphics.disableScissor();

		if (results.isEmpty()) {
			Component message = loading ? Component.translatable("serverworks.disk_writer.loading") : listMessage;
			if (message != null) {
				int line = y + LIST_H / 2 - 12;
				for (var part : font.split(message, LIST_W - 16)) {
					graphics.centeredText(font, part, x + LIST_W / 2, line, loading ? TEXT_DIM : TEXT_ERROR);
					line += 10;
				}
			}
		}
		if (hovered >= 0) {
			graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
		}
	}

	private void extractScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int max = maxScroll();
		if (max <= 0) {
			return;
		}
		int trackX = leftPos + SCROLLBAR_X;
		int trackY = topPos + LIST_Y;
		int thumbH = thumbHeight();
		int thumbY = trackY + (int) ((LIST_H - thumbH) * (scroll / max));
		boolean hot = draggingScrollbar
				|| (mouseX >= trackX && mouseX < trackX + SCROLLBAR_W && mouseY >= trackY && mouseY < trackY + LIST_H);
		graphics.fill(trackX + 1, thumbY, trackX + SCROLLBAR_W - 1, thumbY + thumbH, hot ? SCROLL_THUMB_HOT : SCROLL_THUMB);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		if (!draggingProject || pressed == null) {
			return;
		}
		graphics.nextStratum();
		boolean over = overDetail(mouseX, mouseY);
		graphics.outline(leftPos + DETAIL_X - 2, topPos + LIST_Y - 2, DETAIL_W + 4, LIST_H + 4, over ? DROP_LIT : DROP_IDLE);
		String name = clip(pressed.title(), 110);
		int x = mouseX - 9;
		int y = mouseY - 9;
		graphics.fill(x - 1, y - 1, x + 22 + font.width(name) + 4, y + 19, 0xE0141820);
		graphics.outline(x - 1, y - 1, 24 + font.width(name) + 4, 20, over ? DROP_LIT : DROP_IDLE);
		extractIcon(graphics, pressed.iconUrl(), x + 1, y + 1, 16);
		graphics.text(font, name, x + 21, y + 5, TEXT_LIGHT, false);
	}

	private void extractDetail(GuiGraphicsExtractor graphics) {
		int x = leftPos + DETAIL_X;
		int centre = x + DETAIL_W / 2;
		int y = topPos + LIST_Y;
		if (selected == null) {
			int line = y + 20;
			for (var part : font.split(Component.translatable("serverworks.disk_writer.pick"), DETAIL_W - 8)) {
				graphics.centeredText(font, part, centre, line, TEXT_DIM);
				line += 10;
			}
		} else {
			extractIcon(graphics, selected.iconUrl(), centre - 12, y + 3, 24);
			String title = clip(selected.title(), DETAIL_W - 6);
			graphics.text(font, title, centre - font.width(title) / 2, y + 28, TEXT_LIGHT, false);
			String link = clip(versionLink().getString() + " \u25BE", DETAIL_W - 6);
			int linkX = centre - font.width(link) / 2;
			boolean overLink = overVersionLink(lastMouseX, lastMouseY) || versionMode;
			graphics.text(font, link, linkX, y + 38, overLink ? TEXT_LIGHT : (pinnedName != null ? TEXT_PIN : TEXT_DIM), false);
			if (overLink) {
				graphics.fill(linkX, y + 47, linkX + font.width(link), y + 48, TEXT_LIGHT);
			}
			Component status = statusLine();
			int line = y + 51;
			List<net.minecraft.util.FormattedCharSequence> parts = font.split(status, DETAIL_W - 6);
			for (int i = 0; i < Math.min(2, parts.size()); i++) {
				graphics.centeredText(font, parts.get(i), centre, line, statusColor());
				line += 9;
			}
		}
		if (menu.getSlot(DiskWriterMenu.RESULT_SLOT).hasItem()) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos + ARROW_X, topPos + ARROW_Y,
					ARROW_LIT_U, ARROW_LIT_V, ARROW_W, ARROW_H, TEXTURE_WIDTH, TEXTURE_HEIGHT);
		}
	}

	private Component statusLine() {
		return switch (selectState) {
			case PENDING -> Component.translatable("serverworks.disk_writer.status.checking");
			case FAILED -> selectMessage != null ? selectMessage : Component.translatable("serverworks.disk_writer.error.unavailable");
			case READY -> menu.hasBlankDisk()
					? Component.translatable("serverworks.disk_writer.status.ready")
					: Component.translatable("serverworks.disk_writer.status.need_disk");
			case NONE -> Component.empty();
		};
	}

	private int statusColor() {
		return switch (selectState) {
			case FAILED -> TEXT_ERROR;
			case READY -> menu.hasBlankDisk() ? TEXT_OK : TEXT_DIM;
			default -> TEXT_DIM;
		};
	}

	private void extractIcon(GuiGraphicsExtractor graphics, @Nullable String url, int x, int y, int size) {
		Identifier texture = IconCache.INSTANCE.get(url);
		if (texture != null) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, size, size,
					IconCache.ICON_SIZE, IconCache.ICON_SIZE, IconCache.ICON_SIZE, IconCache.ICON_SIZE);
		} else {
			graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, PLACEHOLDER_U, PLACEHOLDER_V, size, size,
					32, 32, TEXTURE_WIDTH, TEXTURE_HEIGHT);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, titleLabelX, titleLabelY, TEXT, false);
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
		Component count = totalHits > 0
				? Component.translatable("serverworks.disk_writer.count", String.format(Locale.ROOT, "%,d", totalHits))
				: Component.translatable("serverworks.disk_writer.powered_by");
		graphics.text(font, count, imageWidth - 8 - font.width(count), titleLabelY, TEXT_MUTED, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (overVersionLink(mouseX, mouseY) && !versionMode) {
			graphics.setTooltipForNextFrame(font, Component.translatable("serverworks.disk_writer.versions.tooltip"), mouseX, mouseY);
			return;
		}
		int row = versionMode ? -1 : rowAt(mouseX, mouseY);
		if (row < 0 || draggingProject || !menu.getCarried().isEmpty()) {
			return;
		}
		ProjectSummary project = results.get(row);
		if (overStar(row, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(font, Component.translatable(dev.serverworks.client.Favourites.contains(project.id())
					? "serverworks.disk_writer.unstar" : "serverworks.disk_writer.star"), mouseX, mouseY);
			return;
		}
		if (overAuthor(row, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(font, Component.translatable("serverworks.disk_writer.by_author", project.author()), mouseX, mouseY);
			return;
		}
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(project.title()));
		lines.add(Component.literal(subtitle(project)).withStyle(ChatFormatting.GRAY));
		if (!project.description().isEmpty()) {
			lines.add(Component.literal(project.description()).withStyle(ChatFormatting.DARK_GRAY));
		}
		if (!project.serverSideOnly()) {
			lines.add(Component.translatable("item.serverworks.mod_disk.client_required").withStyle(ChatFormatting.GOLD));
		}
		List<net.minecraft.util.FormattedCharSequence> wrapped = new ArrayList<>();
		for (Component line : lines) {
			wrapped.addAll(font.split(line, 190));
		}
		graphics.setTooltipForNextFrame(font, wrapped, mouseX, mouseY);
	}

	private String subtitle(ProjectSummary project) {
		String kind = ModDiskItem.typeName(project.type()).getString();
		String downloads = Component.translatable("serverworks.disk_writer.downloads", compact(project.downloads())).getString();
		return project.author().isEmpty() ? kind + " · " + downloads : project.author() + " · " + downloads;
	}

	private String clip(String text, int width) {
		if (font.width(text) <= width) {
			return text;
		}
		return font.plainSubstrByWidth(text, width - font.width("…")) + "…";
	}

	static String compact(long value) {
		if (value >= 1_000_000_000L) {
			return String.format(Locale.ROOT, "%.1fB", value / 1e9);
		}
		if (value >= 1_000_000L) {
			return String.format(Locale.ROOT, "%.1fM", value / 1e6);
		}
		if (value >= 1_000L) {
			return String.format(Locale.ROOT, "%.1fK", value / 1e3);
		}
		return Long.toString(value);
	}

	// ---- input ----

	private int contentHeight() {
		return results.size() * ROW_H;
	}

	private int maxScroll() {
		return Math.max(0, contentHeight() - LIST_H);
	}

	private int thumbHeight() {
		return Mth.clamp((int) ((float) LIST_H * LIST_H / Math.max(1, contentHeight())), 12, LIST_H);
	}

	private int rowAt(double mouseX, double mouseY) {
		int x = leftPos + LIST_X;
		int y = topPos + LIST_Y;
		if (mouseX < x || mouseX >= x + LIST_W || mouseY < y || mouseY >= y + LIST_H) {
			return -1;
		}
		int row = (int) ((mouseY - y + scroll) / ROW_H);
		return row >= 0 && row < results.size() ? row : -1;
	}

	/** Whether the mouse is over a row's star, at its right end. */
	private boolean overStar(int row, double mouseX, double mouseY) {
		int x = leftPos + LIST_X + LIST_W - 16;
		int rowY = topPos + LIST_Y + row * ROW_H - (int) scroll;
		return mouseX >= x && mouseX < x + 14 && mouseY >= rowY + 4 && mouseY < rowY + 18;
	}

	/** Whether the mouse is over the author's name at the start of a row's second line. */
	private boolean overAuthor(int row, double mouseX, double mouseY) {
		if (row < 0 || row >= results.size() || results.get(row).author().isEmpty()) {
			return false;
		}
		int textX = leftPos + LIST_X + 26;
		int rowY = topPos + LIST_Y + row * ROW_H - (int) scroll;
		int nameW = Math.min(font.width(results.get(row).author()), LIST_W - 30);
		return mouseX >= textX && mouseX < textX + nameW && mouseY >= rowY + 12 && mouseY < rowY + 22;
	}

	/** Lists everything by one author, as if the player had typed {@code @name}. */
	private void searchAuthor(String author) {
		if (search != null) {
			search.setValue("@" + author);
			search.moveCursorToEnd(false);
		}
		searchCountdown = -1;
		startSearch();
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	/** The right-hand panel a dragged project can be dropped on. */
	private boolean overDetail(double mouseX, double mouseY) {
		int x = leftPos + DETAIL_X;
		int y = topPos + LIST_Y;
		return mouseX >= x - 2 && mouseX < x + DETAIL_W + 2 && mouseY >= y - 2 && mouseY < y + LIST_H + 2;
	}

	private void pick(ProjectSummary project) {
		if (selected == null || !selected.id().equals(project.id()) || selectState == SelectState.FAILED) {
			select(project);
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}
	}

	private boolean overScrollbar(double mouseX, double mouseY) {
		int x = leftPos + SCROLLBAR_X;
		int y = topPos + LIST_Y;
		return mouseX >= x && mouseX < x + SCROLLBAR_W && mouseY >= y && mouseY < y + LIST_H;
	}

	private void scrollToMouse(double mouseY) {
		int thumbH = thumbHeight();
		double track = LIST_H - thumbH;
		if (track <= 0) {
			return;
		}
		double fraction = (mouseY - (topPos + LIST_Y) - thumbH / 2.0) / track;
		scroll = Mth.clamp(fraction, 0, 1) * maxScroll();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Either button picks: a Mac trackpad often reports taps as right-clicks (two-finger tap or
		// Control-click), and the list has no other use for the right button.
		if (event.button() == 0 || event.button() == 1) {
			if (maxScroll() > 0 && overScrollbar(event.x(), event.y())) {
				draggingScrollbar = true;
				scrollToMouse(event.y());
				return true;
			}
			if (overVersionLink(event.x(), event.y())) {
				if (versionMode) {
					closeVersions();
				} else {
					openVersions();
				}
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
				return true;
			}
			if (versionMode) {
				int versionRow = versionRowAt(event.x(), event.y());
				if (versionRow >= 0) {
					pickVersion(versionRow);
					return true;
				}
				return super.mouseClicked(event, doubleClick);
			}
			int row = rowAt(event.x(), event.y());
			if (row >= 0 && overStar(row, event.x(), event.y())) {
				boolean now = dev.serverworks.client.Favourites.toggle(results.get(row));
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, now ? 1.3F : 0.9F));
				if (favouritesMode && !now) {
					startSearch();
				}
				return true;
			}
			if (row >= 0 && overAuthor(row, event.x(), event.y())) {
				searchAuthor(results.get(row).author());
				return true;
			}
			if (row >= 0) {
				// Pick straight away, so a click works even when a trackpad nudges the cursor;
				// holding and moving carries the project over to the panel as well.
				pressed = results.get(row);
				pressX = event.x();
				pressY = event.y();
				draggingProject = false;
				pick(pressed);
				if (search != null) {
					search.setFocused(false);
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (draggingScrollbar) {
			scrollToMouse(event.y());
			return true;
		}
		if (pressed != null) {
			if (!draggingProject && Math.hypot(event.x() - pressX, event.y() - pressY) > 6) {
				draggingProject = true;
			}
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingScrollbar = false;
		if (pressed != null) {
			if (draggingProject && overDetail(event.x(), event.y())) {
				pick(pressed);
			}
			pressed = null;
			draggingProject = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (versionMode && versionRowAt(x, y) >= 0) {
			versionScroll = Mth.clamp(versionScroll - scrollY * ROW_H, 0, Math.max(0, versionRows() * ROW_H - LIST_H));
			return true;
		}
		if (rowAt(x, y) >= 0 || overScrollbar(x, y)) {
			scroll = Mth.clamp(scroll - scrollY * ROW_H, 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape() && versionMode) {
			closeVersions();
			return true;
		}
		if (event.isEscape()) {
			minecraft.player.closeContainer();
			return true;
		}
		if (search != null && (search.keyPressed(event) || search.canConsumeInput())) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void resize(int width, int height) {
		String text = search == null ? "" : search.getValue();
		init(width, height);
		if (search != null) {
			search.setValue(text);
		}
		searchCountdown = -1;
	}

	private enum SelectState {
		NONE,
		PENDING,
		READY,
		FAILED
	}
}
