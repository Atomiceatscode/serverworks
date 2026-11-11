package dev.serverworks.machine;

import dev.serverworks.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Eight disk slots in two rows, above the player's inventory. */
public final class DriveBayMenu extends AbstractContainerMenu {
	public static final int COLUMNS = 4;
	public static final int SLOT_X = 53;
	public static final int SLOT_Y = 22;

	private final Container bay;

	public DriveBayMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(DriveBayBlockEntity.SLOTS));
	}

	public DriveBayMenu(int containerId, Inventory inventory, Container bay) {
		super(ModMenus.DRIVE_BAY, containerId);
		checkContainerSize(bay, DriveBayBlockEntity.SLOTS);
		this.bay = bay;
		bay.startOpen(inventory.player);
		for (int i = 0; i < DriveBayBlockEntity.SLOTS; i++) {
			addSlot(new Slot(bay, i, SLOT_X + (i % COLUMNS) * 18, SLOT_Y + (i / COLUMNS) * 18) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return DriveBayBlockEntity.isDisk(stack);
				}

				@Override
				public int getMaxStackSize() {
					return 1;
				}
			});
		}
		addStandardInventorySlots(inventory, 8, 72);
	}

	@Override
	public boolean stillValid(Player player) {
		return bay.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = slots.get(slotIndex);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int baySlots = DriveBayBlockEntity.SLOTS;
		if (slotIndex < baySlots) {
			if (!moveItemStackTo(stack, baySlots, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!DriveBayBlockEntity.isDisk(stack) || !moveItemStackTo(stack, 0, baySlots, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		bay.stopOpen(player);
	}
}
