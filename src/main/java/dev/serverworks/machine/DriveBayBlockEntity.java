package dev.serverworks.machine;

import dev.serverworks.rack.RackStructure;
import dev.serverworks.registry.ModBlockEntities;
import dev.serverworks.registry.ModComponents;
import dev.serverworks.registry.ModItems;
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

/** The disk slots of a Drive Bay: as many as its tier holds. */
public final class DriveBayBlockEntity extends BaseContainerBlockEntity
		implements net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<Integer> {
	private final int capacity;
	private NonNullList<ItemStack> items;
	private int lastCount = -1;

	public DriveBayBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DRIVE_BAY, pos, state);
		this.capacity = state.getBlock() instanceof DriveBayBlock bay ? bay.capacity() : 8;
		this.items = NonNullList.withSize(capacity, ItemStack.EMPTY);
	}

	public int capacity() {
		return capacity;
	}

	@Override
	public Integer getScreenOpeningData(net.minecraft.server.level.ServerPlayer player) {
		return capacity;
	}

	/** Like a shulker box, a bay keeps its disks when broken; they go with the dropped bay. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
	}

	/** Written Mod Disks, written Pack Cartridges, and linked Link Cards. */
	public static boolean isDisk(ItemStack stack) {
		return ((stack.is(ModItems.MOD_DISK) || stack.is(ModItems.PACK_CARTRIDGE)) && stack.has(ModComponents.DISK_LABEL))
				|| (stack.is(ModItems.LINK_CARD) && stack.has(ModComponents.LINK_TARGET))
				|| stack.is(ModItems.CONFIG_CARD)
				|| (stack.is(ModItems.WORLD_DRIVE) && stack.has(ModComponents.WORLD_DRIVE));
	}

	public int diskCount() {
		int count = 0;
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
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
			int count = diskCount();
			if (lastCount >= 0 && count != lastCount) {
				serverLevel.playSound(null, worldPosition, count > lastCount ? dev.serverworks.registry.ModSounds.DRIVE_INSERT
						: dev.serverworks.registry.ModSounds.DRIVE_EJECT, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F,
						0.95F + serverLevel.getRandom().nextFloat() * 0.1F);
			}
			lastCount = count;
			if (state.getBlock() instanceof DriveBayBlock bay && state.getValue(DriveBayBlock.DISKS) != bay.lights(count)) {
				serverLevel.setBlock(worldPosition, state.setValue(DriveBayBlock.DISKS, bay.lights(count)), Block.UPDATE_CLIENTS);
			}
			ServerCoreBlockEntity core = RackCores.owning(serverLevel, worldPosition);
			if (core != null) {
				core.contentsChanged();
			}
		}
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return isDisk(stack);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public int getContainerSize() {
		return capacity;
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
		return getBlockState().getBlock().getName();
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new DriveBayMenu(containerId, inventory, this);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(capacity, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		lastCount = diskCount();
	}
}
