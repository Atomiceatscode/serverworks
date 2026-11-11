package dev.serverworks.showcase;

import com.mojang.brigadier.CommandDispatcher;
import dev.serverworks.Serverworks;
import dev.serverworks.catalog.model.ProjectSummary;
import dev.serverworks.item.DiskLabel;
import dev.serverworks.item.LinkTarget;
import dev.serverworks.machine.DriveBayBlockEntity;
import dev.serverworks.machine.RackComponentBlock;
import dev.serverworks.machine.ServerCoreBlockEntity;
import dev.serverworks.rack.CasingTier;
import dev.serverworks.rack.Platform;
import dev.serverworks.registry.ModBlocks;
import dev.serverworks.registry.ModComponents;
import dev.serverworks.registry.ModItems;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.Container;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * {@code /serverworks showcase}: builds a tour of the mod where the command is run. Every
 * block on a labelled pedestal, a starter station with supplies, and four example racks
 * ready to start. Operators only, because it clears the area it builds in.
 *
 * <p>The area runs 18 blocks to each side and 24 blocks forward (south) from the origin,
 * and up to 8 blocks high.
 */
public final class Showcase {
	private static final int HALF_WIDTH = 18;
	private static final int DEPTH = 24;
	private static final int HEIGHT = 8;

	/** Folder of a world just made with the Create World screen's Showcase switch on; built on first join. */
	private static volatile String pendingWorld;

	private Showcase() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, selection) -> register(dispatcher));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			String pending = pendingWorld;
			if (pending != null && !server.isDedicatedServer() && pending.equals(worldFolder(server))) {
				pendingWorld = null;
				ServerPlayer player = handler.getPlayer();
				server.execute(() -> setUpWorld(server, player));
			}
		});
	}

	/** Asks for the showcase in the singleplayer world about to be created in {@code folder}. */
	public static void buildInNewWorld(String folder) {
		pendingWorld = folder;
	}

	private static String worldFolder(MinecraftServer server) {
		var path = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
		return path == null ? "" : path.toString();
	}

	/**
	 * Builds the showcase where the player stands, makes it the spawn, and keeps it calm: no
	 * night, rain or mobs. The returned future completes when the example disks are in place.
	 */
	public static CompletableFuture<Void> setUpWorld(MinecraftServer server, ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos origin = player.blockPosition();
		CompletableFuture<Void> disks = build(level, origin);
		CommandSourceStack quiet = server.createCommandSourceStack().withSuppressedOutput();
		for (String command : new String[] {
				"gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false",
				"kill @e[type=!player]", "time set noon", "weather clear",
				"setworldspawn " + origin.getX() + " " + origin.getY() + " " + origin.getZ() + " 0"}) {
			server.getCommands().performPrefixedCommand(quiet, command);
		}
		player.teleportTo(level, origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, Set.of(), 0, 15, true);
		player.sendSystemMessage(Component.translatable("serverworks.showcase.welcome"));
		return disks;
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("serverworks")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("showcase").executes(context -> {
					CommandSourceStack source = context.getSource();
					BlockPos origin = BlockPos.containing(source.getPosition());
					build(source.getLevel(), origin);
					source.sendSuccess(() -> Component.translatable("serverworks.showcase.built"), true);
					return 1;
				})));
	}

	/**
	 * Builds the showcase with its front edge at {@code origin}, extending south. Disks are
	 * filled in once the catalog answers, a few seconds later; the returned future completes then.
	 */
	public static CompletableFuture<Void> build(ServerLevel level, BlockPos origin) {
		clear(level, origin);
		floor(level, origin);

		BlockPos title = origin.offset(3, 0, 2);
		standingSign(level, title, DyeColor.CYAN, "Serverworks", "Showcase", "Read each sign,", "then try it all");

		// Row 1: the machines, each on a labelled pedestal.
		List<ItemLike> machines = List.of(ModBlocks.DISK_WRITER, ModBlocks.RACK_CASINGS.get(CasingTier.IRON),
				ModBlocks.RACK_CASINGS.get(CasingTier.DIAMOND), ModBlocks.RACK_CASINGS.get(CasingTier.NETHERITE),
				ModBlocks.SERVER_CORE, ModBlocks.DRIVE_BAY, ModBlocks.MEMORY_BANK, ModBlocks.VENT, ModBlocks.GATEWAY,
				ModBlocks.STATUS_DISPLAY);
		row(level, origin.offset(0, 0, 5), machines);

		// Row 2: one Platform Module per server software.
		List<ItemLike> modules = new ArrayList<>();
		for (Platform platform : Platform.values()) {
			modules.add(ModBlocks.PLATFORM_MODULES.get(platform));
		}
		row(level, origin.offset(0, 0, 8), modules);

		// Starter station: a Disk Writer with supplies beside it.
		BlockPos station = origin.offset(14, 0, 12);
		level.setBlockAndUpdate(station, ModBlocks.DISK_WRITER.defaultBlockState()
				.setValue(dev.serverworks.machine.DiskWriterBlock.FACING, Direction.NORTH));
		Container supplies = chest(level, station.west());
		fill(supplies, List.of(stack(ModItems.OPERATORS_MANUAL, 1), stack(ModItems.WRENCH, 1), stack(ModItems.BLANK_DISK, 16),
				stack(ModItems.BLANK_DISK, 16), stack(ModItems.LINK_CARD, 8)));
		Container written = chest(level, station.west(2));
		standingSign(level, station.north(), DyeColor.LIME, "1. Disk Writer", "Put a Blank Disk", "in, search, pick,", "take the disk");
		standingSign(level, station.west().north(), DyeColor.LIME, "Supplies", "Manual, Wrench,", "Blank Disks,", "Link Cards");
		standingSign(level, station.west(2).north(), DyeColor.LIME, "Written disks", "Ready to slot", "into any", "Drive Bay");

		// Example racks, each 5 wide with display, core and gateway on the front.
		BlockPos paper = origin.offset(8, 0, 16);
		BlockPos fabric = origin.offset(1, 0, 16);
		BlockPos velocity = origin.offset(-6, 0, 16);
		rack(level, paper, Platform.PAPER);
		rack(level, fabric, Platform.FABRIC);
		rack(level, velocity, Platform.VELOCITY);
		standingSign(level, paper.offset(0, 0, -2), DyeColor.YELLOW, "2. Paper rack", "Plugins loaded.", "Open the core,", "press Start");
		standingSign(level, fabric.offset(0, 0, -2), DyeColor.YELLOW, "3. Fabric rack", "Mods loaded.", "Start it, then", "use the Gateway");
		standingSign(level, velocity.offset(0, 0, -2), DyeColor.YELLOW, "4. Velocity proxy", "Linked to the", "Paper rack. Start", "it to start both");

		// A half-built rack to try the Wrench on.
		BlockPos partial = origin.offset(-14, 0, 16);
		BlockState casing = ModBlocks.RACK_CASINGS.get(CasingTier.IRON).defaultBlockState();
		for (int x = -1; x <= 1; x++) {
			for (int y = 0; y <= 2; y++) {
				level.setBlockAndUpdate(partial.offset(x, y, 0), casing);
			}
		}
		level.setBlockAndUpdate(partial.offset(0, 1, 0), facing(ModBlocks.SERVER_CORE));
		standingSign(level, partial.offset(0, 0, -2), DyeColor.ORANGE, "5. Unfinished", "Use the Wrench", "on it to see", "what's missing");

		// Disks need the catalog; they are slotted in when it answers.
		ServerCoreBlockEntity paperCore = (ServerCoreBlockEntity) level.getBlockEntity(paper.offset(0, 1, 0));
		MinecraftServer server = level.getServer();
		CompletableFuture<Map<String, ItemStack>> disks = fetch(List.of("luckperms", "chunky", "lithium", "ferrite-core",
				"fabric-api", "worldedit", "vanilla-perfected"));
		return disks.thenAccept(found -> server.execute(() -> {
			bay(level, paper, List.of(found.get("luckperms"), found.get("chunky")));
			bay(level, fabric, List.of(found.get("lithium"), found.get("ferrite-core")));
			ItemStack link = new ItemStack(ModItems.LINK_CARD);
			if (paperCore != null) {
				link.set(ModComponents.LINK_TARGET, new LinkTarget(paperCore.rackId(), paperCore.getBlockPos(),
						level.dimension().identifier().toString(), Platform.PAPER.id()));
			}
			bay(level, velocity, List.of(link));
			List<ItemStack> extras = new ArrayList<>();
			for (ItemStack stack : found.values()) {
				extras.add(stack.copy());
			}
			fill(written, extras);
		}));
	}

	// ---------------------------------------------------------------- pieces

	private static void clear(ServerLevel level, BlockPos origin) {
		for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
			for (int z = 0; z <= DEPTH; z++) {
				for (int y = 0; y < HEIGHT; y++) {
					level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	private static void floor(ServerLevel level, BlockPos origin) {
		for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
			for (int z = 0; z <= DEPTH; z++) {
				boolean path = z == 3 || z == 6 || z == 10 || z == 13;
				level.setBlockAndUpdate(origin.offset(x, -1, z),
						(path ? Blocks.POLISHED_DEEPSLATE : Blocks.SMOOTH_STONE).defaultBlockState());
			}
		}
	}

	/**
	 * Items spaced two apart, centred on {@code centre}, each on a pedestal with a name sign in
	 * front. They run east to west, so they read left to right for someone facing south.
	 */
	private static void row(ServerLevel level, BlockPos centre, List<ItemLike> blocks) {
		int start = blocks.size() - 1;
		for (int i = 0; i < blocks.size(); i++) {
			BlockPos pedestal = centre.offset(start - i * 2, 0, 0);
			level.setBlockAndUpdate(pedestal, Blocks.POLISHED_ANDESITE.defaultBlockState());
			Block block = ((Item) blocks.get(i).asItem()).getDefaultInstance().getItem() instanceof net.minecraft.world.item.BlockItem item
					? item.getBlock() : Blocks.AIR;
			BlockState state = block.defaultBlockState();
			if (state.hasProperty(RackComponentBlock.FACING)) {
				state = state.setValue(RackComponentBlock.FACING, Direction.NORTH);
			} else if (state.hasProperty(dev.serverworks.machine.DiskWriterBlock.FACING)) {
				state = state.setValue(dev.serverworks.machine.DiskWriterBlock.FACING, Direction.NORTH);
			}
			level.setBlockAndUpdate(pedestal.above(), state);
			wallSign(level, pedestal.north(), Component.translatable(block.getDescriptionId()));
		}
	}

	/** A 5 x 3 x 3 rack: display, core and gateway in front, bay west, memory east, vent on top, module behind. */
	private static void rack(ServerLevel level, BlockPos origin, Platform platform) {
		BlockState casing = ModBlocks.RACK_CASINGS.get(CasingTier.IRON).defaultBlockState();
		for (int x = -2; x <= 2; x++) {
			for (int y = 0; y <= 2; y++) {
				for (int z = 0; z <= 2; z++) {
					boolean inside = Math.abs(x) <= 1 && y == 1 && z == 1;
					level.setBlockAndUpdate(origin.offset(x, y, z), inside ? Blocks.AIR.defaultBlockState() : casing);
				}
			}
		}
		level.setBlockAndUpdate(origin.offset(-1, 1, 0), facing(ModBlocks.STATUS_DISPLAY));
		level.setBlockAndUpdate(origin.offset(0, 1, 0), facing(ModBlocks.SERVER_CORE));
		level.setBlockAndUpdate(origin.offset(1, 1, 0), facing(ModBlocks.GATEWAY));
		level.setBlockAndUpdate(origin.offset(-2, 1, 1), ModBlocks.DRIVE_BAY.defaultBlockState().setValue(RackComponentBlock.FACING, Direction.WEST));
		level.setBlockAndUpdate(origin.offset(2, 1, 1), ModBlocks.MEMORY_BANK.defaultBlockState().setValue(RackComponentBlock.FACING, Direction.EAST));
		level.setBlockAndUpdate(origin.offset(0, 2, 1), ModBlocks.VENT.defaultBlockState().setValue(RackComponentBlock.FACING, Direction.UP));
		level.setBlockAndUpdate(origin.offset(0, 1, 2), ModBlocks.PLATFORM_MODULES.get(platform).defaultBlockState()
				.setValue(RackComponentBlock.FACING, Direction.SOUTH));
	}

	private static void bay(ServerLevel level, BlockPos rack, List<ItemStack> stacks) {
		if (level.getBlockEntity(rack.offset(-2, 1, 1)) instanceof DriveBayBlockEntity bay) {
			int slot = 0;
			for (ItemStack stack : stacks) {
				if (stack != null && !stack.isEmpty()) {
					bay.setItem(slot++, stack.copy());
				}
			}
			bay.setChanged();
		}
	}

	private static BlockState facing(Block block) {
		return block.defaultBlockState().setValue(RackComponentBlock.FACING, Direction.NORTH);
	}

	private static Container chest(ServerLevel level, BlockPos pos) {
		level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
		return (Container) level.getBlockEntity(pos);
	}

	private static void fill(Container container, List<ItemStack> stacks) {
		if (container == null) {
			return;
		}
		int slot = 0;
		for (ItemStack stack : stacks) {
			if (slot >= container.getContainerSize()) {
				break;
			}
			container.setItem(slot++, stack);
		}
		container.setChanged();
	}

	private static ItemStack stack(Item item, int count) {
		return new ItemStack(item, count);
	}

	/** Looks projects up in the catalog and writes each onto a disk or cartridge. Missing ones are skipped. */
	private static CompletableFuture<Map<String, ItemStack>> fetch(List<String> slugs) {
		Map<String, ItemStack> found = new java.util.LinkedHashMap<>();
		List<CompletableFuture<Void>> lookups = new ArrayList<>();
		for (String slug : slugs) {
			lookups.add(Serverworks.catalog().project(slug).handle((project, error) -> {
				if (project != null) {
					synchronized (found) {
						found.put(slug, written(project));
					}
				}
				return null;
			}));
		}
		return CompletableFuture.allOf(lookups.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
			// Keep the order asked for.
			Map<String, ItemStack> ordered = new java.util.LinkedHashMap<>();
			for (String slug : slugs) {
				if (found.containsKey(slug)) {
					ordered.put(slug, found.get(slug));
				}
			}
			return ordered;
		});
	}

	private static ItemStack written(ProjectSummary project) {
		DiskLabel label = DiskLabel.of(project);
		ItemStack stack = new ItemStack(label.type() == dev.serverworks.catalog.model.ProjectType.MODPACK
				? ModItems.PACK_CARTRIDGE : ModItems.MOD_DISK);
		stack.set(ModComponents.DISK_LABEL, label);
		return stack;
	}

	private static void wallSign(ServerLevel level, BlockPos pos, Component name) {
		level.setBlockAndUpdate(pos, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH));
		text(level, pos, DyeColor.BLACK, false, sign -> sign.setLine(1, name));
	}

	/** A free-standing sign facing north, toward someone walking in from the origin. */
	private static void standingSign(ServerLevel level, BlockPos pos, DyeColor colour, String... lines) {
		level.setBlockAndUpdate(pos, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8));
		text(level, pos, colour, true, sign -> {
			for (int i = 0; i < Math.min(4, lines.length); i++) {
				sign.setLine(i, i == 0 ? Component.literal(lines[i]).withStyle(net.minecraft.ChatFormatting.BOLD)
						: Component.literal(lines[i]));
			}
		});
	}

	private static void text(ServerLevel level, BlockPos pos, DyeColor colour, boolean glowing,
			Consumer<net.minecraft.world.level.block.entity.SignText.Mutable> lines) {
		if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			sign.updateText(text -> {
				var mutable = text.asMutable().setColor(colour).setTextGlowing(glowing);
				lines.accept(mutable);
				return mutable.asImmutable();
			}, SignTextSlot.FRONT);
			sign.setWaxed(true);
			level.sendBlockUpdated(pos, sign.getBlockState(), sign.getBlockState(), Block.UPDATE_ALL);
		}
	}
}
