package dev.serverworks.machine;

import dev.serverworks.item.DiskLabel;
import dev.serverworks.registry.ModBlocks;
import dev.serverworks.registry.ModComponents;
import dev.serverworks.registry.ModItems;
import dev.serverworks.registry.ModMenus;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Disk Writer inventory: a blank disk goes in, a written Mod Disk comes out.
 *
 * <p>The project to write is chosen on the client but confirmed by the server (see
 * {@code SelectProjectPayload}), so the label on a written disk always comes from the
 * catalog and never from what a client claims.
 */
public final class DiskWriterMenu extends AbstractContainerMenu {
	public static final int INPUT_SLOT = 0;
	public static final int RESULT_SLOT = 1;
	public static final int INPUT_X = 197;
	public static final int RESULT_X = 245;
	public static final int SLOT_Y = 106;
	public static final int INVENTORY_X = 57;
	public static final int INVENTORY_Y = 140;

	private static final int INV_START = 2;
	private static final int INV_END = 29;
	private static final int HOTBAR_END = 38;

	private final ContainerLevelAccess access;
	private final Slot inputSlot;
	private final Slot resultSlot;
	private final ResultContainer resultContainer = new ResultContainer();
	private final Container inputContainer = new SimpleContainer(1) {
		@Override
		public void setChanged() {
			super.setChanged();
			DiskWriterMenu.this.slotsChanged(this);
		}
	};
	private @Nullable DiskLabel selected;
	private long lastSoundTime;

	public DiskWriterMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, ContainerLevelAccess.NULL);
	}

	public DiskWriterMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
		super(ModMenus.DISK_WRITER, containerId);
		this.access = access;
		this.inputSlot = addSlot(new Slot(inputContainer, 0, INPUT_X, SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(ModItems.BLANK_DISK);
			}
		});
		this.resultSlot = addSlot(new Slot(resultContainer, 1, RESULT_X, SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack carried) {
				carried.onCraftedBy(player, carried.getCount());
				inputSlot.remove(1);
				refreshResult();
				DiskWriterMenu.this.access.execute((level, pos) -> {
					long gameTime = level.getGameTime();
					if (lastSoundTime != gameTime) {
						level.playSound(null, pos, dev.serverworks.registry.ModSounds.DISK_WRITE, SoundSource.BLOCKS, 1.0F, 1.0F);
						lastSoundTime = gameTime;
					}
				});
				super.onTake(player, carried);
			}
		});
		addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
	}

	/** Server side: sets the project to write after the catalog has confirmed it. Null clears it. */
	public void select(@Nullable DiskLabel label) {
		this.selected = label;
		refreshResult();
	}

	public @Nullable DiskLabel selected() {
		return selected;
	}

	public boolean hasBlankDisk() {
		return inputSlot.hasItem();
	}

	@Override
	public void slotsChanged(Container container) {
		if (container == inputContainer) {
			refreshResult();
		}
	}

	private void refreshResult() {
		if (selected != null && inputSlot.hasItem()) {
			ItemStack disk = new ItemStack(selected.type() == dev.serverworks.catalog.model.ProjectType.MODPACK
					? ModItems.PACK_CARTRIDGE : ModItems.MOD_DISK);
			disk.set(ModComponents.DISK_LABEL, selected);
			resultSlot.set(disk);
		} else {
			resultSlot.set(ItemStack.EMPTY);
		}
		broadcastChanges();
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, ModBlocks.DISK_WRITER);
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
		return target.container != resultContainer && super.canTakeItemForPickAll(carried, target);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = slots.get(slotIndex);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (slotIndex == RESULT_SLOT) {
			if (!moveItemStackTo(stack, INV_START, HOTBAR_END, true)) {
				return ItemStack.EMPTY;
			}
			slot.onQuickCraft(stack, original);
		} else if (slotIndex == INPUT_SLOT) {
			if (!moveItemStackTo(stack, INV_START, HOTBAR_END, false)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.is(ModItems.BLANK_DISK)) {
			if (!moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (slotIndex < INV_END) {
			if (!moveItemStackTo(stack, INV_END, HOTBAR_END, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(stack, INV_START, INV_END, false)) {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		}
		slot.setChanged();
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		broadcastChanges();
		return original;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		resultContainer.removeItemNoUpdate(RESULT_SLOT);
		access.execute((level, pos) -> clearContainer(player, inputContainer));
	}
}
