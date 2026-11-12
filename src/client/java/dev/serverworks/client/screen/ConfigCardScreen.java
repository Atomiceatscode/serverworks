package dev.serverworks.client.screen;

import dev.serverworks.item.ConfigCardItem;
import dev.serverworks.item.ServerSettings;
import dev.serverworks.net.EditItemPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/** Edits the settings on a Config Card held in one hand. */
public final class ConfigCardScreen extends Screen {
	private static final List<Integer> DISTANCES = List.of(4, 6, 8, 10, 12, 16, 20, 24, 32);
	private static final List<Integer> PROTECTION = List.of(0, 4, 8, 16, 32, 64);
	private static final int COLUMN = 150;
	private static final int GAP = 8;

	private final InteractionHand hand;
	private ServerSettings.Difficulty difficulty;
	private ServerSettings.GameMode gameMode;
	private boolean hardcore;
	private boolean pvp;
	private boolean keepInventory;
	private boolean allowFlight;
	private int viewDistance;
	private int simulationDistance;
	private int spawnProtection;
	private EditBox motd;

	public ConfigCardScreen(InteractionHand hand, ItemStack card) {
		super(Component.translatable("item.serverworks.config_card"));
		this.hand = hand;
		ServerSettings s = ConfigCardItem.settings(card);
		difficulty = s.difficulty();
		gameMode = s.gameMode();
		hardcore = s.hardcore();
		pvp = s.pvp();
		keepInventory = s.keepInventory();
		allowFlight = s.allowFlight();
		viewDistance = nearest(DISTANCES, s.viewDistance());
		simulationDistance = nearest(DISTANCES, s.simulationDistance());
		spawnProtection = nearest(PROTECTION, s.spawnProtection());
	}

	@Override
	protected void init() {
		String typed = motd == null ? null : motd.getValue();
		int left = width / 2 - COLUMN - GAP / 2;
		int right = width / 2 + GAP / 2;
		int y = height / 2 - 92;
		addRenderableWidget(CycleButton.<ServerSettings.Difficulty>builder(
						d -> Component.translatable("options.difficulty." + d.name().toLowerCase(Locale.ROOT)), difficulty)
				.withValues(ServerSettings.Difficulty.values())
				.create(left, y, COLUMN, 20, Component.translatable("options.difficulty"), (b, v) -> difficulty = v));
		addRenderableWidget(CycleButton.<ServerSettings.GameMode>builder(
						m -> Component.translatable("gameMode." + m.name().toLowerCase(Locale.ROOT)), gameMode)
				.withValues(ServerSettings.GameMode.values())
				.create(right, y, COLUMN, 20, Component.translatable("serverworks.config_card.gamemode"), (b, v) -> gameMode = v));
		y += 24;
		addRenderableWidget(CycleButton.onOffBuilder(hardcore)
				.withTooltip(v -> Tooltip.create(Component.translatable("serverworks.config_card.hardcore.tooltip")))
				.create(left, y, COLUMN, 20, Component.translatable("serverworks.config_card.hardcore"), (b, v) -> hardcore = v));
		addRenderableWidget(CycleButton.onOffBuilder(pvp)
				.create(right, y, COLUMN, 20, Component.translatable("serverworks.config_card.pvp"), (b, v) -> pvp = v));
		y += 24;
		addRenderableWidget(CycleButton.onOffBuilder(keepInventory)
				.create(left, y, COLUMN, 20, Component.translatable("serverworks.config_card.keep_inventory"), (b, v) -> keepInventory = v));
		addRenderableWidget(CycleButton.onOffBuilder(allowFlight)
				.withTooltip(v -> Tooltip.create(Component.translatable("serverworks.config_card.allow_flight.tooltip")))
				.create(right, y, COLUMN, 20, Component.translatable("serverworks.config_card.allow_flight"), (b, v) -> allowFlight = v));
		y += 24;
		addRenderableWidget(CycleButton.<Integer>builder(v -> Component.translatable("options.chunks", v), viewDistance)
				.withValues(DISTANCES)
				.create(left, y, COLUMN, 20, Component.translatable("serverworks.config_card.view"), (b, v) -> viewDistance = v));
		addRenderableWidget(CycleButton.<Integer>builder(v -> Component.translatable("options.chunks", v), simulationDistance)
				.withValues(DISTANCES)
				.create(right, y, COLUMN, 20, Component.translatable("serverworks.config_card.simulation"), (b, v) -> simulationDistance = v));
		y += 24;
		addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(v == 0 ? "Off" : v + ""), spawnProtection)
				.withValues(PROTECTION)
				.withTooltip(v -> Tooltip.create(Component.translatable("serverworks.config_card.spawn_protection.tooltip")))
				.create(left, y, COLUMN, 20, Component.translatable("serverworks.config_card.spawn_protection"),
						(b, v) -> spawnProtection = v));
		y += 36;
		motd = new EditBox(font, left, y, COLUMN * 2 + GAP, 20, Component.translatable("serverworks.config_card.motd"));
		motd.setMaxLength(ServerSettings.MAX_MOTD);
		motd.setHint(Component.translatable("serverworks.config_card.motd.hint"));
		ItemStack card = Minecraft.getInstance().player.getItemInHand(hand);
		motd.setValue(typed != null ? typed : ConfigCardItem.settings(card).motd());
		addRenderableWidget(motd);
		y += 32;
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save()).bounds(left, y, COLUMN, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(right, y, COLUMN, 20).build());
	}

	private void save() {
		ServerSettings settings = new ServerSettings(difficulty, gameMode, hardcore, pvp, keepInventory, allowFlight,
				viewDistance, simulationDistance, spawnProtection, motd.getValue());
		ClientPlayNetworking.send(new EditItemPayload(hand, settings, null));
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, height / 2 - 112, 0xFFFFFFFF);
		// The label sits just above the message box, which starts 40 below the middle.
		graphics.text(font, Component.translatable("serverworks.config_card.motd"), width / 2 - COLUMN - GAP / 2,
				height / 2 + 29, 0xFFA0A0A0, false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static int nearest(List<Integer> values, int value) {
		int best = values.getFirst();
		for (int v : values) {
			if (Math.abs(v - value) < Math.abs(best - value)) {
				best = v;
			}
		}
		return best;
	}
}
