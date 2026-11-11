package dev.serverworks.registry;

import dev.serverworks.Serverworks;
import dev.serverworks.machine.DiskWriterBlock;
import dev.serverworks.machine.DriveBayBlock;
import dev.serverworks.machine.GatewayBlock;
import dev.serverworks.machine.GatewayState;
import dev.serverworks.machine.StatusDisplayBlock;
import dev.serverworks.machine.PlatformModuleBlock;
import dev.serverworks.machine.RackBlock;
import dev.serverworks.machine.RackCasingBlock;
import dev.serverworks.machine.RackComponentBlock;
import dev.serverworks.machine.ServerCoreBlock;
import dev.serverworks.rack.CasingTier;
import dev.serverworks.rack.PartKind;
import dev.serverworks.rack.Platform;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

public final class ModBlocks {
	public static final Block DISK_WRITER = register("disk_writer", DiskWriterBlock::new, machine(3.5F, 6.0F));

	public static final Map<CasingTier, Block> RACK_CASINGS = casings();
	public static final Block SERVER_CORE = register("server_core", ServerCoreBlock::new,
			machine(3.5F, 6.0F).lightLevel(state -> !state.getValue(RackBlock.FORMED) ? 0
					: state.getValue(RackComponentBlock.ACTIVE) ? 12 : 7));
	public static final Map<Platform, Block> PLATFORM_MODULES = platformModules();
	public static final Block DRIVE_BAY = register("drive_bay", DriveBayBlock::new, machine(3.5F, 6.0F));
	public static final Block MEMORY_BANK = register("memory_bank",
			properties -> new RackComponentBlock(PartKind.MEMORY_BANK, properties), machine(3.5F, 6.0F));
	public static final Block VENT = register("vent",
			properties -> new RackComponentBlock(PartKind.VENT, properties), machine(3.5F, 6.0F));
	public static final Block GATEWAY = register("gateway", GatewayBlock::new,
			machine(3.5F, 6.0F).lightLevel(state -> state.getValue(GatewayBlock.PORTAL) == GatewayState.CLOSED ? 0 : 11));
	public static final Block STATUS_DISPLAY = register("status_display", StatusDisplayBlock::new,
			machine(3.5F, 6.0F).lightLevel(state -> state.getValue(RackBlock.FORMED) ? 5 : 0));

	private ModBlocks() {
	}

	private static Map<CasingTier, Block> casings() {
		Map<CasingTier, Block> blocks = new EnumMap<>(CasingTier.class);
		blocks.put(CasingTier.IRON, register("iron_rack_casing",
				properties -> new RackCasingBlock(CasingTier.IRON, properties), machine(3.5F, 6.0F)));
		blocks.put(CasingTier.DIAMOND, register("diamond_rack_casing",
				properties -> new RackCasingBlock(CasingTier.DIAMOND, properties),
				machine(5.0F, 9.0F).mapColor(MapColor.DIAMOND)));
		blocks.put(CasingTier.NETHERITE, register("netherite_rack_casing",
				properties -> new RackCasingBlock(CasingTier.NETHERITE, properties),
				machine(10.0F, 1200.0F).mapColor(MapColor.COLOR_BLACK).sound(SoundType.NETHERITE_BLOCK)));
		return Collections.unmodifiableMap(blocks);
	}

	private static Map<Platform, Block> platformModules() {
		Map<Platform, Block> blocks = new EnumMap<>(Platform.class);
		for (Platform platform : Platform.values()) {
			blocks.put(platform, register(platform.id() + "_platform_module",
					properties -> new PlatformModuleBlock(platform, properties), machine(3.5F, 6.0F)));
		}
		return Collections.unmodifiableMap(blocks);
	}

	private static BlockBehaviour.Properties machine(float hardness, float blastResistance) {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL)
				.instrument(NoteBlockInstrument.IRON_XYLOPHONE)
				.sound(SoundType.METAL)
				.requiresCorrectToolForDrops()
				.strength(hardness, blastResistance);
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
			BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Serverworks.id(name));
		return Blocks.register(key, factory, properties);
	}

	public static void init() {
	}
}
