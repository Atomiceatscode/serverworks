package dev.serverworks.machine;

import dev.serverworks.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A bay's disk slots in rows of eight, like a chest, above the player's inventory. */
public final class DriveBayMenu extends AbstractContainerMenu {
	public static final int COLUMNS = 8;
	public static final int SLOT_X = 17;
	public static final int SLOT_Y = 18;

	private final Container bay;
	private final int capacity;

	/** Client side: the server says how many slots the bay has. */
	public DriveBayMenu(int containerId, Inventory inventory, Integer capacity) {
		this(containerId, inventory, new SimpleContainer(capacity));
	}

	public DriveBayMenu(int containerId, Inventory inventory, Container bay) {
		super(ModMenus.DRIVE_BAY, containerId);
		this.bay = bay;
		this.capacity = bay.getContainerSize();
		bay.startOpen(inventory.player);
		for (int i = 0; i < capacity; i++) {
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
		addStandardInventorySlots(inventory, 8, inventoryY(capacity));
	}

	public int rows() {
		return (capacity + COLUMNS - 1) / COLUMNS;
	}

	/** Where the player's inventory starts below this many bay slots. */
	public static int inventoryY(int capacity) {
		return SLOT_Y + (capacity + COLUMNS - 1) / COLUMNS * 18 + 13;
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
		int baySlots = capacity;
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
