package dev.serverworks.client.screen;

import dev.serverworks.item.WorldDrive;
import dev.serverworks.net.EditItemPayload;
import dev.serverworks.registry.ModComponents;
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
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Names a World Drive, gives it a seed and world type, or picks a saved world to start from. */
public final class WorldDriveScreen extends Screen {
	private static final String NO_SAVE = "";
	private static final int WIDTH = 308;

	private final InteractionHand hand;
	private final WorldDrive original;
	private final boolean existing;
	private WorldDrive.WorldType type;
	private String save;
	private EditBox name;
	private EditBox seed;

	public WorldDriveScreen(InteractionHand hand, ItemStack stack) {
		super(Component.translatable("item.serverworks.world_drive"));
		this.hand = hand;
		WorldDrive drive = stack.get(ModComponents.WORLD_DRIVE);
		this.existing = drive != null;
		this.original = drive != null ? drive
				: new WorldDrive(UUID.randomUUID(), "", "", WorldDrive.WorldType.NORMAL, Optional.empty());
		this.type = original.type();
		this.save = original.importSave().orElse(NO_SAVE);
	}

	@Override
	protected void init() {
		String typedName = name == null ? original.name() : name.getValue();
		String typedSeed = seed == null ? original.seed() : seed.getValue();
		int left = width / 2 - WIDTH / 2;
		int y = height / 2 - 70;
		name = new EditBox(font, left, y, WIDTH, 20, Component.translatable("serverworks.world_drive.name"));
		name.setMaxLength(WorldDrive.MAX_NAME);
		name.setValue(typedName);
		addRenderableWidget(name);
		y += 36;
		seed = new EditBox(font, left, y, WIDTH, 20, Component.translatable("serverworks.world_drive.seed"));
		seed.setMaxLength(WorldDrive.MAX_SEED);
		seed.setHint(Component.translatable("serverworks.world_drive.seed.hint"));
		seed.setValue(typedSeed);
		addRenderableWidget(seed);
		y += 26;
		addRenderableWidget(CycleButton.<WorldDrive.WorldType>builder(
						t -> Component.translatable("serverworks.world_drive.type." + t.name().toLowerCase(Locale.ROOT)), type)
				.withValues(WorldDrive.WorldType.values())
				.create(left, y, WIDTH, 20, Component.translatable("serverworks.world_drive.type"), (b, v) -> type = v));
		y += 24;
		List<String> saves = saveNames();
		if (!saves.contains(save)) {
			save = NO_SAVE;
		}
		CycleButton<String> saveButton = addRenderableWidget(CycleButton.<String>builder(
						s -> s.isEmpty() ? Component.translatable("serverworks.world_drive.save.none") : Component.literal(s), save)
				.withValues(saves)
				.withTooltip(v -> Tooltip.create(Component.translatable("serverworks.world_drive.save.tooltip")))
				.create(left, y, WIDTH, 20, Component.translatable("serverworks.world_drive.save"), (b, v) -> save = v));
		saveButton.active = saves.size() > 1;
		y += 32;
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save())
				.bounds(left, y, WIDTH / 2 - 4, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
				.bounds(left + WIDTH / 2 + 4, y, WIDTH / 2 - 4, 20).build());
	}

	/** Singleplayer saves on this computer, other than the world being played; only offered in singleplayer. */
	private static List<String> saveNames() {
		List<String> out = new ArrayList<>();
		out.add(NO_SAVE);
		Minecraft client = Minecraft.getInstance();
		var server = client.getSingleplayerServer();
		if (server == null) {
			return out;
		}
		Path current = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
		try {
			for (LevelStorageSource.LevelDirectory level : client.getLevelSource().findLevelCandidates()) {
				if (!level.path().toAbsolutePath().normalize().equals(current) && WorldDrive.safeSaveName(level.directoryName())) {
					out.add(level.directoryName());
				}
			}
		} catch (Exception e) {
			// No saves to offer.
		}
		return out;
	}

	private void save() {
		WorldDrive drive = new WorldDrive(original.id(), name.getValue(), seed.getValue(), type,
				save.isEmpty() ? Optional.empty() : Optional.of(save));
		ClientPlayNetworking.send(new EditItemPayload(hand, null, drive));
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int left = width / 2 - WIDTH / 2;
		graphics.centeredText(font, title, width / 2, height / 2 - 100, 0xFFFFFFFF);
		graphics.text(font, Component.translatable("serverworks.world_drive.name"), left, height / 2 - 81, 0xFFA0A0A0, false);
		graphics.text(font, Component.translatable("serverworks.world_drive.seed"), left, height / 2 - 45, 0xFFA0A0A0, false);
		if (existing) {
			graphics.centeredText(font, Component.translatable("serverworks.world_drive.existing"), width / 2,
					height / 2 + 84, 0xFF8F9BB3);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
