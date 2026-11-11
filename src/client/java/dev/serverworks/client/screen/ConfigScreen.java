package dev.serverworks.client.screen;

import dev.serverworks.ServerworksConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Edits {@code config/serverworks.json}, the settings for racks run by this copy of the game. */
public final class ConfigScreen extends Screen {
	private static final int ROW = 24;
	private static final int FIELD_WIDTH = 120;
	private static final int TEXT = 0xFFE8ECF4;
	private static final int MUTED = 0xFF8F9BB3;

	private final @Nullable Screen parent;
	private final ServerworksConfig config = ServerworksConfig.get();
	private final List<Row> rows = new ArrayList<>();
	private @Nullable EditBox idle;
	private @Nullable EditBox maxRunning;
	private @Nullable EditBox firstPort;
	private @Nullable EditBox lastPort;
	private @Nullable EditBox publicAddress;
	private boolean operatorsOnly;

	private record Row(Component label, Component hint, int y) {
	}

	public ConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("serverworks.config.title"));
		this.parent = parent;
		this.operatorsOnly = config.operatorsOnly;
	}

	@Override
	protected void init() {
		rows.clear();
		int fieldX = width / 2 + 10;
		int y = 54;
		idle = number("idle", config.idleMinutes, fieldX, y);
		y += ROW;
		maxRunning = number("max_running", config.maxRunning, fieldX, y);
		y += ROW;
		firstPort = number("first_port", config.firstPort, fieldX, y);
		y += ROW;
		lastPort = number("last_port", config.lastPort, fieldX, y);
		y += ROW;
		rows.add(new Row(Component.translatable("serverworks.config.operators_only"),
				Component.translatable("serverworks.config.operators_only.hint"), y));
		addRenderableWidget(CycleButton.onOffBuilder(operatorsOnly).displayOnlyValue()
				.create(fieldX, y, FIELD_WIDTH, 20, Component.translatable("serverworks.config.operators_only"),
						(button, value) -> operatorsOnly = value));
		y += ROW;
		publicAddress = field("public_address", config.publicAddress, fieldX, y);
		publicAddress.setMaxLength(253);

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> {
			save();
			onClose();
		}).bounds(width / 2 - 154, height - 28, 150, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
				.bounds(width / 2 + 4, height - 28, 150, 20).build());
	}

	private EditBox number(String key, int value, int x, int y) {
		EditBox box = field(key, Integer.toString(value), x, y);
		return box;
	}

	private EditBox field(String key, String value, int x, int y) {
		rows.add(new Row(Component.translatable("serverworks.config." + key),
				Component.translatable("serverworks.config." + key + ".hint"), y));
		EditBox box = new EditBox(font, x, y, FIELD_WIDTH, 20, Component.translatable("serverworks.config." + key));
		box.setValue(value);
		return addRenderableWidget(box);
	}

	private void save() {
		config.idleMinutes = parse(idle, config.idleMinutes);
		config.maxRunning = parse(maxRunning, config.maxRunning);
		config.firstPort = parse(firstPort, config.firstPort);
		config.lastPort = parse(lastPort, config.lastPort);
		config.operatorsOnly = operatorsOnly;
		config.publicAddress = publicAddress == null ? "" : publicAddress.getValue().strip();
		config.save();
	}

	private static int parse(@Nullable EditBox box, int fallback) {
		try {
			return box == null || box.getValue().isEmpty() ? fallback : Integer.parseInt(box.getValue());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 16, TEXT);
		int line = 28;
		for (var part : font.split(Component.translatable("serverworks.config.subtitle"), Math.min(width - 40, 360))) {
			graphics.centeredText(font, part, width / 2, line, MUTED);
			line += 10;
		}
		for (Row row : rows) {
			int labelX = width / 2 - 10 - font.width(row.label());
			graphics.text(font, row.label(), labelX, row.y() + 6, TEXT, false);
			if (mouseY >= row.y() && mouseY < row.y() + 20 && mouseX >= labelX && mouseX < width / 2 + 10 + FIELD_WIDTH) {
				graphics.setTooltipForNextFrame(font, font.split(row.hint(), 220), mouseX, mouseY);
			}
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
