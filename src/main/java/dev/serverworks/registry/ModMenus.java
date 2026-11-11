package dev.serverworks.registry;

import dev.serverworks.Serverworks;
import dev.serverworks.machine.DiskWriterMenu;
import dev.serverworks.machine.DriveBayMenu;
import dev.serverworks.machine.ServerCoreMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
	public static final MenuType<DiskWriterMenu> DISK_WRITER = register("disk_writer", DiskWriterMenu::new);
	public static final MenuType<DriveBayMenu> DRIVE_BAY = register("drive_bay", DriveBayMenu::new);
	public static final MenuType<ServerCoreMenu> SERVER_CORE = register("server_core", ServerCoreMenu::new);

	private ModMenus() {
	}

	private static <T extends AbstractContainerMenu> MenuType<T> register(String name, MenuType.MenuSupplier<T> factory) {
		return Registry.register(BuiltInRegistries.MENU, Serverworks.id(name),
				new MenuType<>(factory, FeatureFlags.VANILLA_SET));
	}

	public static void init() {
	}
}
