package dev.serverworks.registry;

import dev.serverworks.Serverworks;
import dev.serverworks.machine.DriveBayBlockEntity;
import dev.serverworks.machine.ServerCoreBlockEntity;
import dev.serverworks.machine.StatusDisplayBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

public final class ModBlockEntities {
	public static final BlockEntityType<ServerCoreBlockEntity> SERVER_CORE =
			register("server_core", ServerCoreBlockEntity::new, ModBlocks.SERVER_CORE);
	public static final BlockEntityType<DriveBayBlockEntity> DRIVE_BAY =
			register("drive_bay", DriveBayBlockEntity::new, ModBlocks.DRIVE_BAY);
	public static final BlockEntityType<StatusDisplayBlockEntity> STATUS_DISPLAY =
			register("status_display", StatusDisplayBlockEntity::new, ModBlocks.STATUS_DISPLAY);

	private ModBlockEntities() {
	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String name,
			BlockEntityType.BlockEntitySupplier<T> factory, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Serverworks.id(name),
				new BlockEntityType<>(factory, Set.of(block)));
	}

	public static void init() {
	}
}
