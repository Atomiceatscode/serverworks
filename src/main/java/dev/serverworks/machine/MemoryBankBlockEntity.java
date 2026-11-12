package dev.serverworks.machine;

import dev.serverworks.item.MemoryStickItem;
import dev.serverworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The four RAM stick slots of a Memory Bank. */
public final class MemoryBankBlockEntity extends BaseContainerBlockEntity {
	public static final int SLOTS = 4;
	private static final Component NAME = Component.translatable("container.serverworks.memory_bank");

	private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

	public MemoryBankBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MEMORY_BANK, pos, state);
	}

	/** The memory every stick here adds up to, in megabytes. */
	public int megabytes() {
		int total = 0;
		for (ItemStack stack : items) {
			total += MemoryStickItem.megabytes(stack);
		}
		return total;
	}

	public int sticks() {
		int count = 0;
		for (ItemStack stack : items) {
			if (stack.getItem() instanceof MemoryStickItem) {
				count++;
			}
		}
		return count;
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (level instanceof ServerLevel serverLevel) {
			BlockState state = getBlockState();
			int sticks = sticks();
			if (state.getBlock() instanceof MemoryBankBlock && state.getValue(MemoryBankBlock.STICKS) != sticks) {
				serverLevel.setBlock(worldPosition, state.setValue(MemoryBankBlock.STICKS, sticks), Block.UPDATE_CLIENTS);
			}
			ServerCoreBlockEntity core = RackCores.owning(serverLevel, worldPosition);
			if (core != null) {
				core.contentsChanged();
			}
		}
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return stack.getItem() instanceof MemoryStickItem;
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public int getContainerSize() {
		return SLOTS;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	protected Component getDefaultName() {
		return NAME;
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new MemoryBankMenu(containerId, inventory, this);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
	}
}
