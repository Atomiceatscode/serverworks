package dev.serverworks.machine;

import dev.serverworks.item.MemoryStickItem;
import dev.serverworks.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Four RAM stick slots in a row, above the player's inventory. */
public final class MemoryBankMenu extends AbstractContainerMenu {
	public static final int SLOT_X = 53;
	public static final int SLOT_Y = 20;

	private final Container bank;

	public MemoryBankMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(MemoryBankBlockEntity.SLOTS));
	}

	public MemoryBankMenu(int containerId, Inventory inventory, Container bank) {
		super(ModMenus.MEMORY_BANK, containerId);
		checkContainerSize(bank, MemoryBankBlockEntity.SLOTS);
		this.bank = bank;
		bank.startOpen(inventory.player);
		for (int i = 0; i < MemoryBankBlockEntity.SLOTS; i++) {
			addSlot(new Slot(bank, i, SLOT_X + i * 18, SLOT_Y) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return stack.getItem() instanceof MemoryStickItem;
				}

				@Override
				public int getMaxStackSize() {
					return 1;
				}
			});
		}
		addStandardInventorySlots(inventory, 8, 51);
	}

	/** The memory the sticks shown add up to, in megabytes; for the screen. */
	public int megabytes() {
		int total = 0;
		for (int i = 0; i < MemoryBankBlockEntity.SLOTS; i++) {
			total += MemoryStickItem.megabytes(slots.get(i).getItem());
		}
		return total;
	}

	@Override
	public boolean stillValid(Player player) {
		return bank.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = slots.get(slotIndex);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int bankSlots = MemoryBankBlockEntity.SLOTS;
		if (slotIndex < bankSlots) {
			if (!moveItemStackTo(stack, bankSlots, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!(stack.getItem() instanceof MemoryStickItem) || !moveItemStackTo(stack, 0, bankSlots, false)) {
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
		bank.stopOpen(player);
	}
}
