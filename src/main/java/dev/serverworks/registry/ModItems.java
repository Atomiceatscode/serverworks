package dev.serverworks.registry;

import dev.serverworks.Serverworks;
import dev.serverworks.item.ModDiskItem;
import dev.serverworks.item.WrenchItem;
import dev.serverworks.rack.CasingTier;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ModItems {
	/** Everything in the creative tab, in display order. */
	private static final List<Item> TAB_ITEMS = new ArrayList<>();

	public static final Item DISK_WRITER = registerBlock(ModBlocks.DISK_WRITER, new Item.Properties());
	public static final Item BLANK_DISK = register("blank_disk", Item::new, new Item.Properties().stacksTo(16), true);
	public static final Item MOD_DISK = register("mod_disk", ModDiskItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), false);
	public static final Item PACK_CARTRIDGE = register("pack_cartridge", ModDiskItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE), false);
	public static final Item LINK_CARD = register("link_card", dev.serverworks.item.LinkCardItem::new,
			new Item.Properties().stacksTo(16), true);
	public static final Item CONFIG_CARD = register("config_card", dev.serverworks.item.ConfigCardItem::new,
			new Item.Properties().stacksTo(1), true);
	public static final Item WORLD_DRIVE = register("world_drive", dev.serverworks.item.WorldDriveItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), true);
	public static final Item WRENCH = register("wrench", WrenchItem::new, new Item.Properties().stacksTo(1), true);
	public static final Item OPERATORS_MANUAL = register("operators_manual", dev.serverworks.item.OperatorsManualItem::new,
			new Item.Properties().stacksTo(1), true);

	public static final Item SERVER_CORE = registerBlock(ModBlocks.SERVER_CORE, new Item.Properties().rarity(Rarity.UNCOMMON));

	static {
		registerBlock(ModBlocks.RACK_CASINGS.get(CasingTier.IRON), new Item.Properties());
		registerBlock(ModBlocks.RACK_CASINGS.get(CasingTier.DIAMOND), new Item.Properties());
		registerBlock(ModBlocks.RACK_CASINGS.get(CasingTier.NETHERITE), new Item.Properties().fireResistant());
		registerBlock(ModBlocks.DRIVE_BAY, new Item.Properties());
		registerBlock(ModBlocks.MEMORY_BANK, new Item.Properties());
		registerBlock(ModBlocks.VENT, new Item.Properties());
		registerBlock(ModBlocks.GATEWAY, new Item.Properties());
		registerBlock(ModBlocks.STATUS_DISPLAY, new Item.Properties());
		ModBlocks.PLATFORM_MODULES.values().forEach(block -> registerBlock(block, new Item.Properties()));
	}

	public static final ResourceKey<CreativeModeTab> TAB_KEY =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Serverworks.id("main"));

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties,
			boolean inTab) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Serverworks.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
		if (inTab) {
			TAB_ITEMS.add(item);
		}
		return item;
	}

	/** Registers the item form of a block under the block's own id. */
	private static Item registerBlock(Block block, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		BlockItem item = new BlockItem(block, properties.useBlockDescriptionPrefix().setId(key));
		item.registerBlocks(Item.BY_BLOCK, item);
		TAB_ITEMS.add(item);
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.serverworks.main"))
				.icon(() -> new ItemStack(SERVER_CORE))
				.displayItems((parameters, output) -> TAB_ITEMS.forEach(output::accept))
				.build());
	}
}
