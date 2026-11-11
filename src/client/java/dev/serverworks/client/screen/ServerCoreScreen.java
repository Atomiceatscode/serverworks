package dev.serverworks.client.screen;

import com.mojang.blaze3d.Blaze3D;
import dev.serverworks.Serverworks;
import dev.serverworks.host.ServerInstance.Failure;
import dev.serverworks.host.ServerInstance.State;
import dev.serverworks.item.DiskLabel;
import dev.serverworks.machine.RackStatus;
import dev.serverworks.machine.ServerCoreMenu;
import dev.serverworks.net.ConsoleCommandPayload;
import dev.serverworks.net.InstanceStatusPayload;
import dev.serverworks.net.RackActionPayload;
import dev.serverworks.rack.RackValidator;
import dev.serverworks.registry.ModComponents;
import dev.serverworks.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonLinks;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The rack's control panel. The Rack tab shows what the rack is made of and which disks
 * it carries; the Console tab shows the server's output and takes commands. Start and Stop
 * sit under both.
 */
public final class ServerCoreScreen extends AbstractContainerScreen<ServerCoreMenu> {
	private static final Identifier BACKGROUND = Serverworks.id("textures/gui/container/server_core.png");
	private static final int IMAGE_WIDTH = 230;
	private static final int IMAGE_HEIGHT = 188;

	private static final int BANNER_X = 8;
	private static final int BANNER_Y = 18;
	private static final int BANNER_W = 214;
	private static final int BANNER_H = 36;
	private static final int ROWS_Y = 60;
	private static final int ROW_H = 11;
	private static final int LEFT_X = 10;
	private static final int RIGHT_X = 122;
	private static final int DISKS_LABEL_Y = 97;
	private static final int GRID_X = 8;
	private static final int UPDATE_ARROW = 0xFF63E08A;
	private static final int GRID_Y = 108;
	private static final int GRID_COLUMNS = 12;
	private static final int GRID_ROWS = 3;
	private static final int CELL = 18;
	private static final int PANEL_X0 = 7;
	private static final int PANEL_Y0 = 57;
	private static final int PANEL_X1 = 225;
	private static final int PANEL_Y1 = 163;
	private static final int CONSOLE_LINE_H = 9;
	private static final int MAX_CONSOLE_LINES = 500;
	private static final long NOTICE_MILLIS = 6000;

	private static final int TEXT = 0xFF404040;
	private static final int TEXT_MUTED = 0xFF707070;
	private static final int TEXT_LIGHT = 0xFFE8ECF4;
	private static final int TEXT_DIM = 0xFF8F9BB3;
	private static final int TEXT_ERROR = 0xFFFF7A6B;
	private static final int TEXT_OK = 0xFF63E08A;
	private static final int TEXT_BUSY = 0xFF6FC3FF;
	private static final int TEXT_WARN = 0xFFB25A00;
	private static final int TEXT_NOTICE = 0xFFFFB347;
	private static final int CONSOLE_BG = 0xFF0E1117;
	private static final int CONSOLE_TEXT = 0xFFC9D1E0;
	private static final int CONSOLE_OURS = 0xFF6FC3FF;
	private static final int CONSOLE_WARN = 0xFFFFC266;
	private static final int CONSOLE_ERROR = 0xFFFF7A6B;
	private static final int CONSOLE_INPUT = 0xFF7FE0A0;
	private static final int PROGRESS_TRACK = 0xFF2A3040;
	private static final int PROGRESS_FILL = 0xFF6FC3FF;
	private static final int INCOMPATIBLE_TINT = 0x70FF2A2A;
	private static final int TAB_SELECTED = 0xFF6FC3FF;

	private RackStatus status = RackStatus.UNKNOWN;
	private boolean received;
	private final List<ItemStack> diskStacks = new ArrayList<>();
	private int scrollRow;

	private @Nullable InstanceStatusPayload instance;
	private final List<String> console = new ArrayList<>();
	private long consoleNext;
	/** Lines scrolled up from the newest; 0 follows new output. */
	private int consoleScroll;
	private Component notice = Component.empty();
	private long noticeUntil;

	private boolean consoleTab;
	private boolean backupsTab;
	private @Nullable Button backupsTabButton;
	private @Nullable Button backupNowButton;
	private final List<Button> restoreButtons = new ArrayList<>();
	private List<dev.serverworks.host.Backups.Backup> backups = List.of();
	private boolean backupBusy;
	private static final int BACKUP_ROWS = 7;
	private static final int BACKUP_ROW_H = 14;
	private boolean eulaPanel;
	private @Nullable Button rackTabButton;
	private @Nullable Button consoleTabButton;
	private @Nullable Button powerButton;
	private @Nullable EditBox commandBox;
	private @Nullable Button packButton;
	private @Nullable Button redstoneButton;
	private @Nullable Button updateButton;
	/** Newer versions for this rack's disks, by project id. */
	private final java.util.Map<String, dev.serverworks.net.DiskUpdatesPayload.Update> updates = new java.util.HashMap<>();
	private @Nullable Button eulaRead;
	private @Nullable Button eulaAgree;
	private @Nullable Button eulaCancel;

	public ServerCoreScreen(ServerCoreMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
	}

	@Override
	protected void init() {
		super.init();
		String typed = commandBox == null ? "" : commandBox.getValue();
		rackTabButton = addRenderableWidget(Button.builder(Component.translatable("serverworks.server_core.tab.rack"),
				button -> setTab(0)).bounds(leftPos + 80, topPos + 3, 46, 14).build());
		consoleTabButton = addRenderableWidget(Button.builder(Component.translatable("serverworks.server_core.tab.console"),
				button -> setTab(1)).bounds(leftPos + 128, topPos + 3, 46, 14).build());
		backupsTabButton = addRenderableWidget(Button.builder(Component.translatable("serverworks.server_core.tab.backups"),
				button -> setTab(2)).bounds(leftPos + 176, topPos + 3, 46, 14).build());
		backupNowButton = addRenderableWidget(Button.builder(Component.translatable("serverworks.server_core.backup_now"),
				button -> send(RackActionPayload.Action.BACKUP_NOW)).bounds(leftPos + 132, topPos + 166, 90, 16).build());
		restoreButtons.clear();
		for (int i = 0; i < BACKUP_ROWS; i++) {
			int row = i;
			restoreButtons.add(addRenderableWidget(Button.builder(Component.translatable("serverworks.server_core.restore"),
					button -> restore(row)).bounds(leftPos + PANEL_X1 - 52, topPos + PANEL_Y0 + 14 + i * BACKUP_ROW_H, 48, 12)
					.build()));
		}
		powerButton = addRenderableWidget(Button.builder(Component.translatable("serverworks.server_core.start"),
				button -> onPower()).bounds(leftPos + 8, topPos + 166, 90, 16).build());
		commandBox = new EditBox(font, leftPos + 104, topPos + 167, 118, 14, Component.translatable("serverworks.server_core.console.hint"));
		commandBox.setMaxLength(ConsoleCommandPayload.MAX_LENGTH);
		commandBox.setHint(fitHint(Component.translatable("serverworks.server_core.console.hint")));
		commandBox.setValue(typed);
		addRenderableWidget(commandBox);
		packButton = addRenderableWidget(Button.builder(Component.translatable("serverworks.server_core.pack"),
				button -> send(RackActionPayload.Action.EXPORT_PACK)).bounds(leftPos + 132, topPos + 166, 90, 16)
				.tooltip(Tooltip.create(Component.translatable("serverworks.server_core.pack.tooltip"))).build());

		updateButton = addRenderableWidget(Button.builder(Component.empty(),
				button -> send(RackActionPayload.Action.UPDATE_DISKS)).bounds(leftPos + 162, topPos + 94, 60, 13).build());
		redstoneButton = addRenderableWidget(Button.builder(Component.empty(),
				button -> send(RackActionPayload.Action.TOGGLE_REDSTONE)).bounds(leftPos + 104, topPos + 166, 22, 16).build());

		int panelCentre = leftPos + (PANEL_X0 + PANEL_X1) / 2;
		eulaRead = addRenderableWidget(Button.builder(Component.translatable("serverworks.eula.read"),
				button -> Blaze3D.openUri(CommonLinks.EULA)).bounds(panelCentre - 105, topPos + 140, 66, 16).build());
		eulaAgree = addRenderableWidget(Button.builder(Component.translatable("serverworks.eula.agree"), button -> agreeToEula()).bounds(panelCentre - 33, topPos + 140, 66, 16).build());
		eulaCancel = addRenderableWidget(Button.builder(Component.translatable("serverworks.eula.cancel"),
				button -> eulaPanel = false).bounds(panelCentre + 39, topPos + 140, 66, 16).build());
		refreshWidgets();
	}

	/** 0 is the rack, 1 the console, 2 the backups. */
	private void setTab(int tab) {
		backupsTab = tab == 2;
		setConsoleTab(tab == 1);
	}

	private boolean rackTab() {
		return !consoleTab && !backupsTab;
	}

	private void restore(int row) {
		if (row < backups.size()) {
			ClientPlayNetworking.send(new dev.serverworks.net.RestoreBackupPayload(menu.containerId, backups.get(row).name()));
		}
	}

	/** Called when the server sends the rack's backups. */
	public void setBackups(List<dev.serverworks.host.Backups.Backup> list, boolean busy) {
		backups = List.copyOf(list);
		backupBusy = busy;
		refreshWidgets();
	}

	/** The Back Up Now button, as pressed by a player. */
	public void pressBackup() {
		send(RackActionPayload.Action.BACKUP_NOW);
	}

	public int backupCount() {
		return backups.size();
	}

	/** Opens a tab, for tests: 0 rack, 1 console, 2 backups. */
	public void showTab(int tab) {
		setTab(tab);
	}

	private void setConsoleTab(boolean console) {
		consoleTab = console;
		consoleScroll = 0;
		if (commandBox != null) {
			setFocused(console ? commandBox : null);
			commandBox.setFocused(console);
		}
		refreshWidgets();
	}

	/** The Start or Stop button, as pressed by a player. */
	public void pressPower() {
		onPower();
	}

	/** The "I agree" button on the EULA panel. */
	public void agreeToEula() {
		if (eulaPanel) {
			send(RackActionPayload.Action.ACCEPT_EULA);
			send(RackActionPayload.Action.START);
			eulaPanel = false;
			refreshWidgets();
		}
	}

	public boolean showingEula() {
		return eulaPanel;
	}

	public void showConsole(boolean console) {
		setTab(console ? 1 : 0);
	}

	private void onPower() {
		if (instance != null && instance.state().active()) {
			send(RackActionPayload.Action.STOP);
		} else if (instance != null && !instance.eulaAccepted()) {
			eulaPanel = true;
		} else {
			send(RackActionPayload.Action.START);
		}
		refreshWidgets();
	}

	private void send(RackActionPayload.Action action) {
		ClientPlayNetworking.send(new RackActionPayload(menu.containerId, action));
	}

	/** Called when the server finds which disks have newer versions. */
	public void setUpdates(List<dev.serverworks.net.DiskUpdatesPayload.Update> found) {
		updates.clear();
		for (var update : found) {
			updates.put(update.projectId(), update);
		}
		refreshWidgets();
	}

	/** The Update button, as pressed by a player. */
	public void pressUpdate() {
		send(RackActionPayload.Action.UPDATE_DISKS);
	}

	/** How many disks have an update, for tests. */
	public int updateCount() {
		return updates.size();
	}

	/** Called when the server sends a fresh snapshot of the rack's structure. */
	public void setStatus(RackStatus status) {
		this.status = status;
		this.received = true;
		diskStacks.clear();
		for (DiskLabel label : status.disks()) {
			ItemStack stack = new ItemStack(label.type() == dev.serverworks.catalog.model.ProjectType.MODPACK
					? ModItems.PACK_CARTRIDGE : ModItems.MOD_DISK);
			stack.set(ModComponents.DISK_LABEL, label);
			diskStacks.add(stack);
		}
		scrollRow = Mth.clamp(scrollRow, 0, maxScrollRow());
		refreshWidgets();
	}

	/** Called when the server sends the state of the rack's server and new console output. */
	public void setInstance(InstanceStatusPayload payload) {
		instance = payload;
		if (!payload.consoleLines().isEmpty()) {
			if (payload.consoleFrom() != consoleNext) {
				// A gap (or a fresh screen): the server sent its most recent lines from scratch.
				console.clear();
			}
			int before = console.size();
			console.addAll(payload.consoleLines());
			consoleNext = payload.consoleFrom() + payload.consoleLines().size();
			if (consoleScroll > 0) {
				consoleScroll += console.size() - before;
			}
			while (console.size() > MAX_CONSOLE_LINES) {
				console.removeFirst();
			}
		}
		if (!payload.notice().isEmpty()) {
			notice = Component.translatable(payload.notice());
			noticeUntil = System.currentTimeMillis() + NOTICE_MILLIS;
		}
		if (payload.eulaAccepted()) {
			eulaPanel = false;
		}
		refreshWidgets();
	}

	/** Shows a short message in the banner, the same way refusals are shown. */
	public void showNotice(Component message) {
		notice = message;
		noticeUntil = System.currentTimeMillis() + NOTICE_MILLIS;
	}

	public RackStatus status() {
		return status;
	}

	public boolean hasStatus() {
		return received;
	}

	public @Nullable InstanceStatusPayload instance() {
		return instance;
	}

	public List<String> consoleLines() {
		return console;
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		refreshWidgets();
	}

	private void refreshWidgets() {
		if (powerButton == null || commandBox == null) {
			return;
		}
		State state = instance == null ? State.IDLE : instance.state();
		boolean active = state.active();
		boolean control = instance != null && instance.canControl();
		powerButton.setMessage(Component.translatable(active ? "serverworks.server_core.stop" : "serverworks.server_core.start"));
		Component blocked = null;
		if (instance != null && !control) {
			blocked = Component.translatable("serverworks.server_core.start.not_allowed");
		} else if (!active && received && !status.formed()) {
			blocked = Component.translatable("serverworks.server_core.start.not_formed");
		} else if (!active && status.formed() && !dev.serverworks.host.Providers.supports(status.platform())) {
			blocked = Component.translatable(dev.serverworks.host.Providers.unsupportedReason(status.platform()));
		} else if (!active && status.formed() && status.platform().group() == dev.serverworks.rack.Platform.Group.PROXY
				&& status.linkCount() == 0) {
			blocked = Component.translatable("serverworks.notice.no_links");
		}
		powerButton.active = instance != null && blocked == null && state != State.STOPPING && !eulaPanel;
		powerButton.setTooltip(blocked == null ? null : Tooltip.create(blocked));

		boolean running = state == State.RUNNING && control;
		commandBox.visible = consoleTab && !eulaPanel;
		commandBox.setEditable(running);
		commandBox.setHint(fitHint(Component.translatable(running ? "serverworks.server_core.console.hint"
				: "serverworks.server_core.console.not_running")));
		commandBox.setTooltip(Tooltip.create(Component.translatable(running
				? "serverworks.server_core.console.hint.tooltip" : "serverworks.server_core.console.not_running.tooltip")));
		if (packButton != null) {
			boolean modPlatform = status.formed() && status.platform().group() == dev.serverworks.rack.Platform.Group.MOD;
			packButton.visible = rackTab() && !eulaPanel && modPlatform;
			if (updateButton != null) {
				updateButton.visible = rackTab() && !eulaPanel && status.formed() && !updates.isEmpty();
				updateButton.active = instance != null && instance.canControl();
				updateButton.setMessage(Component.translatable("serverworks.server_core.update", updates.size()));
				boolean restarts = instance != null && instance.state().active();
				updateButton.setTooltip(Tooltip.create(Component.translatable(restarts
						? "serverworks.server_core.update.restart" : "serverworks.server_core.update.tooltip")));
			}
			if (redstoneButton != null) {
				redstoneButton.visible = rackTab() && !eulaPanel && status.formed();
				redstoneButton.active = instance != null && instance.canControl();
				redstoneButton.setTooltip(Tooltip.create(Component.translatable(status.redstoneControl()
						? "serverworks.server_core.redstone.on" : "serverworks.server_core.redstone.off")));
			}
			packButton.active = instance != null && instance.packAvailable();
			packButton.setTooltip(Tooltip.create(Component.translatable(packButton.active
					? "serverworks.server_core.pack.tooltip" : "serverworks.server_core.pack.unavailable")));
		}
		boolean controls = instance != null && instance.canControl();
		boolean stopped = instance == null || !instance.state().active();
		if (backupNowButton != null) {
			backupNowButton.visible = backupsTab && !eulaPanel;
			backupNowButton.active = controls && !backupBusy && status.formed();
		}
		for (int i = 0; i < restoreButtons.size(); i++) {
			Button restore = restoreButtons.get(i);
			restore.visible = backupsTab && !eulaPanel && i < backups.size();
			restore.active = controls && stopped && !backupBusy;
			restore.setTooltip(Tooltip.create(Component.translatable(stopped
					? "serverworks.server_core.restore.tooltip" : "serverworks.server_core.restore.stop_first")));
		}
		if (rackTabButton != null && consoleTabButton != null && backupsTabButton != null) {
			rackTabButton.active = !rackTab();
			consoleTabButton.active = !consoleTab;
			backupsTabButton.active = !backupsTab;
		}
		if (eulaRead != null && eulaAgree != null && eulaCancel != null) {
			eulaRead.visible = eulaPanel;
			eulaAgree.visible = eulaPanel;
			eulaCancel.visible = eulaPanel;
		}
	}

	/** The command box draws its hint unclipped, so shorten it to the box's inside width. */
	private Component fitHint(Component hint) {
		int room = commandBox == null ? 110 : commandBox.getInnerWidth();
		String text = hint.getString();
		if (font.width(text) > room) {
			text = font.plainSubstrByWidth(text, room - font.width("…")) + "…";
		}
		return Component.literal(text).withStyle(ChatFormatting.DARK_GRAY);
	}

		// ---- drawing ----

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
		extractBanner(graphics);
		if (eulaPanel) {
			extractEula(graphics);
		} else if (consoleTab) {
			extractConsole(graphics);
		} else if (backupsTab) {
			extractBackups(graphics);
		} else {
			extractDisks(graphics);
		}
	}

	private void extractBanner(GuiGraphicsExtractor graphics) {
		int x = leftPos + BANNER_X + 6;
		int y = topPos + BANNER_Y;
		if (!received) {
			graphics.text(font, Component.translatable("serverworks.server_core.reading"), x, y + 14, TEXT_DIM, false);
			return;
		}
		State state = instance == null ? State.IDLE : instance.state();
		boolean showServer = state != State.IDLE && status.formed();
		boolean sleeping = showServer && instance.hibernated() && state.canStart();

		Component headline;
		int headlineColor;
		if (sleeping) {
			headline = Component.translatable("serverworks.instance.state.sleeping");
			headlineColor = TEXT_BUSY;
		} else if (showServer) {
			headline = state == State.DOWNLOADING
					? Component.translatable(state.translationKey(), instance.progress())
					: Component.translatable(state.translationKey());
			headlineColor = switch (state) {
				case RUNNING -> TEXT_OK;
				case CRASHED, FAILED -> TEXT_ERROR;
				case STOPPED -> TEXT_DIM;
				default -> TEXT_BUSY;
			};
		} else if (status.formed()) {
			headline = Component.translatable("serverworks.server_core.formed");
			headlineColor = TEXT_OK;
		} else {
			headline = Component.translatable("serverworks.server_core.not_formed");
			headlineColor = TEXT_ERROR;
		}
		graphics.text(font, headline, x, y + 6, headlineColor, false);

		List<FormattedCharSequence> second = font.split(secondLine(state, showServer), BANNER_W - 34);
		int lineColor = System.currentTimeMillis() < noticeUntil ? TEXT_NOTICE : TEXT_LIGHT;
		for (int i = 0; i < Math.min(2, second.size()); i++) {
			graphics.text(font, second.get(i), x, y + 17 + i * 10, lineColor, false);
		}

		if (status.formed()) {
			int swatch = 0xFF000000 | status.platform().color();
			int sx = leftPos + BANNER_X + BANNER_W - 18;
			graphics.fill(sx, y + 6, sx + 12, y + 18, swatch);
			if (state == State.RUNNING) {
				graphics.fill(sx + 4, y + 22, sx + 8, y + 26, TEXT_OK);
			}
		}
		if (showServer && (state == State.DOWNLOADING || state == State.RESOLVING || state == State.INSTALLING
				|| state == State.STARTING || state == State.STOPPING)) {
			int barX0 = leftPos + BANNER_X + 1;
			int barX1 = leftPos + BANNER_X + BANNER_W - 1;
			int barY = y + BANNER_H - 3;
			graphics.fill(barX0, barY, barX1, barY + 2, PROGRESS_TRACK);
			int filled;
			if (state == State.DOWNLOADING) {
				filled = (barX1 - barX0) * instance.progress() / 100;
			} else {
				// No measurable progress: a sliding block shows it is still working.
				int width = (barX1 - barX0) / 5;
				int travel = (barX1 - barX0) - width;
				int offset = (int) ((System.currentTimeMillis() / 8) % (2L * travel));
				offset = offset > travel ? 2 * travel - offset : offset;
				graphics.fill(barX0 + offset, barY, barX0 + offset + width, barY + 2, PROGRESS_FILL);
				filled = -1;
			}
			if (filled >= 0) {
				graphics.fill(barX0, barY, barX0 + filled, barY + 2, PROGRESS_FILL);
			}
		}
	}

	private Component secondLine(State state, boolean showServer) {
		if (System.currentTimeMillis() < noticeUntil) {
			return notice;
		}
		if (!status.formed()) {
			return problemText();
		}
		if (showServer && state == State.RUNNING) {
			Component where = minecraft.hasSingleplayerServer()
					? Component.translatable("serverworks.server_core.join_local", instance.port())
					: Component.translatable("serverworks.server_core.join_port", instance.port());
			if (instance.playersOnline() >= 0) {
				where = Component.translatable("serverworks.server_core.join_players", where, instance.playersOnline(),
						status.maxPlayers());
			}
			dev.serverworks.rack.Compatibility.Result compatibility = dev.serverworks.rack.Compatibility.check(status.disks());
			if (!compatibility.open()) {
				return Component.translatable("serverworks.server_core.gateway_restricted", where,
						String.join(", ", compatibility.clientMods()));
			}
			return where;
		}
		if (showServer && instance.hibernated() && state.canStart()) {
			return Component.translatable("serverworks.server_core.sleeping");
		}
		if (showServer && (state == State.FAILED || state == State.CRASHED) && instance.failure() != Failure.NONE) {
			return Component.translatable(instance.failure().translationKey());
		}
		return Component.translatable("serverworks.server_core.summary",
				Component.translatable(status.platform().translationKey()),
				Component.translatable(status.platform().group().translationKey()));
	}

	private Component problemText() {
		String key = status.problem().translationKey();
		if (status.problemPos() != null) {
			return Component.translatable(key + ".at", status.problemPos().getX(), status.problemPos().getY(),
					status.problemPos().getZ());
		}
		return Component.translatable(key);
	}

	private void extractDisks(GuiGraphicsExtractor graphics) {
		int first = scrollRow * GRID_COLUMNS;
		int last = Math.min(diskStacks.size(), first + GRID_COLUMNS * GRID_ROWS);
		for (int i = first; i < last; i++) {
			int x = leftPos + GRID_X + ((i - first) % GRID_COLUMNS) * CELL;
			int y = topPos + GRID_Y + ((i - first) / GRID_COLUMNS) * CELL;
			graphics.item(diskStacks.get(i), x, y);
			if (!status.accepts(status.disks().get(i))) {
				graphics.fill(x, y, x + 16, y + 16, INCOMPATIBLE_TINT);
			} else if (updates.containsKey(status.disks().get(i).projectId())) {
				// A small green arrow in the corner: a newer version is available.
				int ax = x + 11;
				int ay = y;
				graphics.fill(ax + 2, ay, ax + 3, ay + 1, UPDATE_ARROW);
				graphics.fill(ax + 1, ay + 1, ax + 4, ay + 2, UPDATE_ARROW);
				graphics.fill(ax, ay + 2, ax + 5, ay + 3, UPDATE_ARROW);
				graphics.fill(ax + 1, ay + 3, ax + 4, ay + 5, UPDATE_ARROW);
			}
		}
		if (received && status.formed() && diskStacks.isEmpty()) {
			graphics.centeredText(font, Component.translatable(status.platform().group() == dev.serverworks.rack.Platform.Group.PROXY
					? "serverworks.server_core.no_disks_proxy" : "serverworks.server_core.no_disks"),
					leftPos + IMAGE_WIDTH / 2, topPos + GRID_Y + 22, TEXT_DIM);
		}
	}

	private int visibleConsoleLines() {
		return (PANEL_Y1 - PANEL_Y0 - 6) / CONSOLE_LINE_H;
	}

	private void extractBackups(GuiGraphicsExtractor graphics) {
		int x0 = leftPos + PANEL_X0;
		int y0 = topPos + PANEL_Y0;
		int x1 = leftPos + PANEL_X1;
		int y1 = topPos + PANEL_Y1;
		graphics.fill(x0, y0, x1, y1, CONSOLE_BG);
		Component heading = backupBusy ? Component.translatable("serverworks.server_core.backups.busy")
				: Component.translatable("serverworks.server_core.backups.heading", backups.size());
		graphics.text(font, heading, x0 + 5, y0 + 3, backupBusy ? TEXT_BUSY : TEXT_DIM, false);
		if (backups.isEmpty()) {
			String key = instance != null && !instance.canControl() ? "serverworks.server_core.backups.operators"
					: "serverworks.server_core.backups.empty";
			List<FormattedCharSequence> lines = font.split(Component.translatable(key), x1 - x0 - 20);
			int y = (y0 + y1) / 2 - lines.size() * 5;
			for (FormattedCharSequence line : lines) {
				graphics.centeredText(font, line, (x0 + x1) / 2, y, TEXT_DIM);
				y += 10;
			}
			return;
		}
		java.time.format.DateTimeFormatter format = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
		for (int i = 0; i < Math.min(BACKUP_ROWS, backups.size()); i++) {
			var backup = backups.get(i);
			int y = y0 + 14 + i * BACKUP_ROW_H;
			String when = java.time.Instant.ofEpochMilli(backup.modified()).atZone(java.time.ZoneId.systemDefault()).format(format);
			graphics.text(font, when, x0 + 5, y + 2, TEXT_LIGHT, false);
			String size = String.format(java.util.Locale.ROOT, "%.1f MB", backup.size() / 1_048_576.0);
			graphics.text(font, size, x0 + 100, y + 2, TEXT_DIM, false);
		}
	}

	private void extractConsole(GuiGraphicsExtractor graphics) {
		int x0 = leftPos + PANEL_X0;
		int y0 = topPos + PANEL_Y0;
		int x1 = leftPos + PANEL_X1;
		int y1 = topPos + PANEL_Y1;
		graphics.fill(x0, y0, x1, y1, CONSOLE_BG);
		if (console.isEmpty()) {
			String key = instance != null && !instance.canControl() ? "serverworks.server_core.console.operators"
					: "serverworks.server_core.console.empty";
			List<FormattedCharSequence> lines = font.split(Component.translatable(key), x1 - x0 - 20);
			int y = (y0 + y1) / 2 - lines.size() * 5;
			for (FormattedCharSequence line : lines) {
				graphics.centeredText(font, line, (x0 + x1) / 2, y, TEXT_DIM);
				y += 10;
			}
			return;
		}
		int visible = visibleConsoleLines();
		consoleScroll = Mth.clamp(consoleScroll, 0, Math.max(0, console.size() - visible));
		int last = console.size() - consoleScroll;
		int first = Math.max(0, last - visible);
		graphics.enableScissor(x0 + 2, y0 + 2, x1 - 2, y1 - 2);
		int y = y1 - 4 - (last - first) * CONSOLE_LINE_H;
		for (int i = first; i < last; i++) {
			String line = console.get(i);
			graphics.text(font, clip(line, x1 - x0 - 8), x0 + 4, y, consoleColor(line), false);
			y += CONSOLE_LINE_H;
		}
		graphics.disableScissor();
		if (consoleScroll > 0) {
			Component more = Component.translatable("serverworks.server_core.console.newer", consoleScroll);
			graphics.text(font, more, x1 - 4 - font.width(more), y0 + 3, TEXT_NOTICE, false);
		}
	}

	private static int consoleColor(String line) {
		if (line.startsWith("[Serverworks]")) {
			return CONSOLE_OURS;
		}
		if (line.startsWith("> ")) {
			return CONSOLE_INPUT;
		}
		String upper = line.toUpperCase(Locale.ROOT);
		if (upper.contains("/ERROR]") || upper.contains("ERROR:") || upper.contains("EXCEPTION")) {
			return CONSOLE_ERROR;
		}
		if (upper.contains("/WARN]")) {
			return CONSOLE_WARN;
		}
		return CONSOLE_TEXT;
	}

	private String clip(String text, int width) {
		if (font.width(text) <= width) {
			return text;
		}
		return font.plainSubstrByWidth(text, width - font.width("…")) + "…";
	}

	private void extractEula(GuiGraphicsExtractor graphics) {
		int x0 = leftPos + PANEL_X0;
		int y0 = topPos + PANEL_Y0;
		int x1 = leftPos + PANEL_X1;
		graphics.fill(x0, y0, x1, topPos + PANEL_Y1, CONSOLE_BG);
		graphics.centeredText(font, Component.translatable("serverworks.eula.title"), (x0 + x1) / 2, y0 + 8, TEXT_LIGHT);
		int y = y0 + 24;
		for (FormattedCharSequence line : font.split(Component.translatable("serverworks.eula.body"), x1 - x0 - 20)) {
			graphics.centeredText(font, line, (x0 + x1) / 2, y, TEXT_DIM);
			y += 10;
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (redstoneButton != null && redstoneButton.visible) {
			// Lit redstone dust when redstone control is on, a grey torch-less dust when off.
			int x = redstoneButton.getX() - leftPos + 3;
			int y = redstoneButton.getY() - topPos;
			graphics.item(new ItemStack(status.redstoneControl() ? net.minecraft.world.item.Items.REDSTONE_TORCH
					: net.minecraft.world.item.Items.REDSTONE), x, y);
		}
		graphics.text(font, title, titleLabelX, titleLabelY, TEXT, false);
		// The selected tab's button is disabled (you are already there), so mark it as the current one.
		int tabX = backupsTab ? 176 : consoleTab ? 128 : 80;
		graphics.fill(tabX + 3, 15, tabX + 43, 17, TAB_SELECTED);
		if (!rackTab() || eulaPanel) {
			return;
		}
		if (!received || !status.formed()) {
			Component help = Component.translatable("serverworks.server_core.help", RackValidator.MIN_SIZE, RackValidator.MAX_SIZE);
			List<FormattedCharSequence> lines = font.split(help, imageWidth - 2 * LEFT_X);
			for (int i = 0; i < Math.min(4, lines.size()); i++) {
				graphics.text(font, lines.get(i), LEFT_X, ROWS_Y - 2 + i * 10, TEXT_MUTED, false);
			}
			return;
		}
		row(graphics, LEFT_X, 0, "serverworks.server_core.frame", Component.translatable(status.tier().translationKey()), TEXT);
		row(graphics, LEFT_X, 1, "serverworks.server_core.size",
				Component.literal(status.sizeX() + " x " + status.sizeY() + " x " + status.sizeZ()), TEXT);
		boolean ventsShort = status.vents() < status.ventsNeeded();
		row(graphics, LEFT_X, 2, "serverworks.server_core.vents",
				Component.translatable("serverworks.server_core.vents.value", status.vents(), status.ventsNeeded()),
				ventsShort ? TEXT_WARN : TEXT);
		row(graphics, RIGHT_X, 0, "serverworks.server_core.memory",
				Component.translatable("serverworks.server_core.memory.value", gigabytes(status.ramMb()),
						gigabytes(status.tier().maxRamMb())), status.ramMb() >= status.tier().maxRamMb() ? TEXT_WARN : TEXT);
		row(graphics, RIGHT_X, 1, "serverworks.server_core.players",
				Component.translatable("serverworks.server_core.players.value", status.maxPlayers()), TEXT);
		row(graphics, RIGHT_X, 2, "serverworks.server_core.bays", Component.literal(Integer.toString(status.driveBays())), TEXT);

		int incompatible = 0;
		for (DiskLabel disk : status.disks()) {
			if (!status.accepts(disk)) {
				incompatible++;
			}
		}
		Component loaded = status.platform().group() == dev.serverworks.rack.Platform.Group.PROXY
				? Component.translatable("serverworks.server_core.disks_links", status.disks().size(), status.linkCount())
				: Component.translatable("serverworks.server_core.disks", status.disks().size(), status.diskSlots());
		graphics.text(font, loaded, LEFT_X, DISKS_LABEL_Y, TEXT, false);
		if (incompatible > 0) {
			Component warning = Component.translatable("serverworks.server_core.incompatible", incompatible);
			graphics.text(font, warning, imageWidth - 10 - font.width(warning), DISKS_LABEL_Y, TEXT_WARN, false);
		}
	}

	private void row(GuiGraphicsExtractor graphics, int x, int index, String labelKey, Component value, int valueColor) {
		int y = ROWS_Y + index * ROW_H;
		Component label = Component.translatable(labelKey);
		graphics.text(font, label, x, y, TEXT_MUTED, false);
		graphics.text(font, value, x + font.width(label) + 4, y, valueColor, false);
	}

	private static String gigabytes(int megabytes) {
		return megabytes % 1024 == 0 ? Integer.toString(megabytes / 1024)
				: String.format(Locale.ROOT, "%.1f", megabytes / 1024.0);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (instance != null && !instance.detail().isEmpty() && overBanner(mouseX, mouseY)
				&& (instance.state() == State.FAILED || instance.state() == State.CRASHED)) {
			graphics.setTooltipForNextFrame(font, font.split(Component.literal(instance.detail()), 220), mouseX, mouseY);
			return;
		}
		if (!rackTab() || eulaPanel) {
			return;
		}
		int index = diskAt(mouseX, mouseY);
		if (index < 0) {
			return;
		}
		ItemStack stack = diskStacks.get(index);
		List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(stack));
		if (!status.accepts(status.disks().get(index)) && status.platform() != null) {
			lines.add(Component.translatable("serverworks.server_core.disk_incompatible",
					Component.translatable(status.platform().translationKey())).withStyle(ChatFormatting.RED));
		}
		var update = updates.get(status.disks().get(index).projectId());
		if (update != null) {
			lines.add(Component.translatable("serverworks.server_core.update.line", update.from(), update.to())
					.withStyle(ChatFormatting.GREEN));
		}
		graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
	}

	private boolean overBanner(double mouseX, double mouseY) {
		return mouseX >= leftPos + BANNER_X && mouseX < leftPos + BANNER_X + BANNER_W
				&& mouseY >= topPos + BANNER_Y && mouseY < topPos + BANNER_Y + BANNER_H;
	}

	private int diskAt(double mouseX, double mouseY) {
		double dx = mouseX - (leftPos + GRID_X);
		double dy = mouseY - (topPos + GRID_Y);
		if (dx < 0 || dy < 0 || dx >= GRID_COLUMNS * CELL || dy >= GRID_ROWS * CELL) {
			return -1;
		}
		int index = scrollRow * GRID_COLUMNS + (int) (dy / CELL) * GRID_COLUMNS + (int) (dx / CELL);
		return index < diskStacks.size() ? index : -1;
	}

	private int maxScrollRow() {
		int rows = (diskStacks.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
		return Math.max(0, rows - GRID_ROWS);
	}

	// ---- input ----

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (consoleTab) {
			consoleScroll = Mth.clamp(consoleScroll + (int) Math.signum(scrollY) * 3, 0,
					Math.max(0, console.size() - visibleConsoleLines()));
			return true;
		}
		if (backupsTab) {
			return super.mouseScrolled(x, y, scrollX, scrollY);
		}
		if (maxScrollRow() > 0) {
			scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScrollRow());
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			if (eulaPanel) {
				eulaPanel = false;
				refreshWidgets();
				return true;
			}
			minecraft.player.closeContainer();
			return true;
		}
		if (commandBox != null && commandBox.isFocused() && commandBox.visible) {
			if (event.isConfirmation()) {
				String command = commandBox.getValue().strip();
				if (!command.isEmpty() && instance != null && instance.state() == State.RUNNING) {
					ClientPlayNetworking.send(new ConsoleCommandPayload(menu.containerId, command));
					commandBox.setValue("");
					consoleScroll = 0;
				}
				return true;
			}
			if (commandBox.keyPressed(event) || commandBox.canConsumeInput()) {
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void resize(int width, int height) {
		int tab = backupsTab ? 2 : consoleTab ? 1 : 0;
		init(width, height);
		setTab(tab);
	}
}
