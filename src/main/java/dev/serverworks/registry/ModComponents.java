package dev.serverworks.registry;

import dev.serverworks.Serverworks;
import dev.serverworks.item.DiskLabel;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModComponents {
	public static final DataComponentType<DiskLabel> DISK_LABEL = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			Serverworks.id("disk_label"),
			DataComponentType.<DiskLabel>builder()
					.persistent(DiskLabel.CODEC)
					.networkSynchronized(DiskLabel.STREAM_CODEC)
					.cacheEncoding()
					.build());

	public static final DataComponentType<dev.serverworks.item.LinkTarget> LINK_TARGET = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			Serverworks.id("link_target"),
			DataComponentType.<dev.serverworks.item.LinkTarget>builder()
					.persistent(dev.serverworks.item.LinkTarget.CODEC)
					.networkSynchronized(dev.serverworks.item.LinkTarget.STREAM_CODEC)
					.build());

	private ModComponents() {
	}

	public static void init() {
	}
}
