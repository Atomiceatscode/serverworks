package dev.serverworks.machine;

import dev.serverworks.item.DiskLabel;
import dev.serverworks.rack.Cell;
import dev.serverworks.rack.Pos;
import dev.serverworks.rack.RackScan;
import dev.serverworks.rack.RackStructure;
import dev.serverworks.rack.RackValidator;
import dev.serverworks.registry.ModBlockEntities;
import dev.serverworks.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The brain of a rack. It keeps looking at the blocks around it, switches their
 * {@code formed} state on and off as the structure becomes valid or breaks, and reports
 * the rack's status to anyone with its screen open.
 */
public final class ServerCoreBlockEntity extends BlockEntity implements MenuProvider {
	private static final Component NAME = Component.translatable("container.serverworks.server_core");
	/** How often a core re-checks its structure even when nothing told it to, in ticks. */
	private static final int RESCAN_INTERVAL = 20;
	/** How often Gateways and Status Displays are refreshed, in ticks. */
	private static final int OUTPUT_INTERVAL = 10;

	/** Identifies this rack's server files on disk, so they survive the block being moved. */
	private UUID rackId = UUID.randomUUID();
	private @Nullable RackScan scan;
	private boolean dirty = true;
	private int statusVersion;
	/** The server state at the last output update, to notice changes worth a sound. */
	private dev.serverworks.host.ServerInstance.@Nullable State lastState;
	private int outputTicks;

	public ServerCoreBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SERVER_CORE, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, ServerCoreBlockEntity core) {
		// Spread periodic checks out so many racks do not all scan on the same tick.
		boolean due = (level.getGameTime() + (pos.asLong() & 0xF)) % RESCAN_INTERVAL == 0;
		if (core.dirty || due) {
			core.rescan();
		}
		if ((level.getGameTime() + (pos.asLong() & 0x7)) % OUTPUT_INTERVAL == 0) {
			core.updateOutputs();
		}
	}

	/** Brings the rack's Gateways and Status Displays up to date with its server. */
	void updateOutputs() {
		if (!(level instanceof ServerLevel serverLevel) || scan == null || !scan.formed()) {
			return;
		}
		RackStructure rack = scan.structure();
		dev.serverworks.host.ServerInstance instance = dev.serverworks.Serverworks.host().instances().peek(rackId);
		dev.serverworks.host.ServerInstance.Snapshot snapshot = instance == null
				? dev.serverworks.host.ServerInstance.Snapshot.IDLE : instance.snapshot();
		boolean running = snapshot.state() == dev.serverworks.host.ServerInstance.State.RUNNING;
		boolean open = dev.serverworks.rack.Compatibility.check(disks(rack)).open();
		boolean active = snapshot.state().active();
		for (Pos pos : rack.members()) {
			BlockPos blockPos = new BlockPos(pos.x(), pos.y(), pos.z());
			BlockState state = serverLevel.getBlockState(blockPos);
			if (state.getBlock() instanceof RackComponentBlock && state.getValue(RackComponentBlock.ACTIVE) != active) {
				serverLevel.setBlock(blockPos, state.setValue(RackComponentBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
			}
		}
		RackEffects.update(serverLevel, worldPosition, rack, lastState, snapshot.state(), outputTicks++);
		lastState = snapshot.state();
		GatewayState portal = !running ? GatewayState.CLOSED : open ? GatewayState.OPEN : GatewayState.RESTRICTED;
		for (Pos pos : rack.gateways()) {
			BlockPos blockPos = new BlockPos(pos.x(), pos.y(), pos.z());
			BlockState state = serverLevel.getBlockState(blockPos);
			if (state.getBlock() instanceof GatewayBlock && state.getValue(GatewayBlock.PORTAL) != portal) {
				serverLevel.setBlock(blockPos, state.setValue(GatewayBlock.PORTAL, portal), Block.UPDATE_CLIENTS);
			}
		}
		if (!rack.displays().isEmpty()) {
			List<DisplayLine> lines = displayLines(serverLevel, rack, snapshot, open);
			for (Pos pos : rack.displays()) {
				if (serverLevel.getBlockEntity(new BlockPos(pos.x(), pos.y(), pos.z())) instanceof StatusDisplayBlockEntity display) {
					display.show(lines);
				}
			}
		}
	}

	private static List<DisplayLine> displayLines(ServerLevel level, RackStructure rack,
			dev.serverworks.host.ServerInstance.Snapshot snapshot, boolean open) {
		var state = snapshot.state();
		String platform = rack.platform().translationKey();
		if (state == dev.serverworks.host.ServerInstance.State.RUNNING) {
			DisplayLine players = snapshot.playersOnline() >= 0
					? DisplayLine.of("serverworks.display.players", 0xFFFFFF, snapshot.playersOnline(), rack.maxPlayers())
					: DisplayLine.of(platform, 0xFFFFFF);
			String publicAddress = dev.serverworks.ServerworksConfig.get().publicAddress;
			DisplayLine address = !level.getServer().isDedicatedServer()
					? DisplayLine.of("serverworks.display.local", 0x9FB2DA, snapshot.port())
					: publicAddress.isEmpty() ? DisplayLine.of("serverworks.display.port", 0x9FB2DA, snapshot.port())
					: DisplayLine.of("serverworks.display.address", 0x9FB2DA, publicAddress, snapshot.port());
			return List.of(DisplayLine.of(open ? "serverworks.display.online" : "serverworks.display.online_mods",
					open ? 0x63E08A : 0xFFB347), players, address);
		}
		if (snapshot.hibernated() && state.canStart()) {
			return List.of(DisplayLine.of("serverworks.display.sleeping", 0x6FC3FF), DisplayLine.of(platform, 0xFFFFFF),
					DisplayLine.of("serverworks.display.wake", 0x9FB2DA));
		}
		DisplayLine headline = switch (state) {
			case IDLE, STOPPED -> DisplayLine.of("serverworks.display.offline", 0x8F9BB3);
			case CRASHED, FAILED -> DisplayLine.of(state.translationKey(), 0xFF7A6B);
			case DOWNLOADING -> DisplayLine.of(state.translationKey(), 0x6FC3FF, snapshot.progress());
			default -> DisplayLine.of(state.translationKey(), 0x6FC3FF);
		};
		return List.of(headline, DisplayLine.of(platform, 0xFFFFFF), DisplayLine.EMPTY);
	}

	public UUID rackId() {
		return rackId;
	}

	public void markDirty() {
		dirty = true;
	}

	/** Called when a drive bay in this rack gains or loses a disk. */
	public void contentsChanged() {
		statusVersion++;
	}

	/** Changes every time {@link #status()} would return something different. */
	public int statusVersion() {
		return statusVersion;
	}

	public @Nullable RackScan lastScan() {
		return scan;
	}

	public boolean contains(BlockPos pos) {
		return scan != null && scan.formed() && scan.structure().contains(new Pos(pos.getX(), pos.getY(), pos.getZ()));
	}

	/** Looks at the structure now and applies whatever changed. Safe to call any time on the server. */
	public void rescan() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		dirty = false;
		BlockPos p = worldPosition;
		int r = RackValidator.MAX_SIZE;
		// Never pull unloaded chunks in just to look at them; try again later instead.
		if (!serverLevel.isLoaded(p.offset(-r, 0, -r)) || !serverLevel.isLoaded(p.offset(r, 0, -r))
				|| !serverLevel.isLoaded(p.offset(-r, 0, r)) || !serverLevel.isLoaded(p.offset(r, 0, r))) {
			dirty = true;
			return;
		}
		RackScan next = RackValidator.scan(pos -> cellAt(serverLevel, pos), new Pos(p.getX(), p.getY(), p.getZ()));
		if (next.equals(scan)) {
			return;
		}
		applyFormed(serverLevel, scan, next);
		if (next.formed() && (scan == null || !scan.formed())) {
			dev.serverworks.ModAdvancements.awardNear(serverLevel, worldPosition, dev.serverworks.ModAdvancements.BUILD_RACK);
			if (next.structure().tier() == dev.serverworks.rack.CasingTier.NETHERITE) {
				dev.serverworks.ModAdvancements.awardNear(serverLevel, worldPosition, dev.serverworks.ModAdvancements.NETHERITE_RACK);
			}
		}
		scan = next;
		statusVersion++;
		updateOutputs();
	}

	/** How the rack rules see the blocks around this core, for previews. Server side only. */
	public dev.serverworks.rack.RackValidator.View cellView() {
		ServerLevel serverLevel = (ServerLevel) level;
		return pos -> cellAt(serverLevel, pos);
	}

	private static Cell cellAt(ServerLevel level, Pos pos) {
		BlockState state = level.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z()));
		if (state.getBlock() instanceof RackBlock rack) {
			return rack.cell(state);
		}
		return state.isAir() ? Cell.AIR : Cell.OBSTRUCTION;
	}

	private static void applyFormed(ServerLevel level, @Nullable RackScan before, @Nullable RackScan after) {
		Set<Pos> stillMembers = new HashSet<>();
		if (after != null && after.formed()) {
			RackAccent accent = RackAccent.of(after.structure().platform());
			for (Pos pos : after.structure().members()) {
				stillMembers.add(pos);
				setFormed(level, pos, true, accent);
			}
		}
		if (before != null && before.formed()) {
			for (Pos pos : before.structure().members()) {
				if (!stillMembers.contains(pos)) {
					setFormed(level, pos, false, RackAccent.NONE);
				}
			}
		}
	}

	private static void setFormed(ServerLevel level, Pos pos, boolean formed, RackAccent accent) {
		BlockPos blockPos = new BlockPos(pos.x(), pos.y(), pos.z());
		BlockState state = level.getBlockState(blockPos);
		if (!(state.getBlock() instanceof RackBlock)) {
			return;
		}
		BlockState updated = state.setValue(RackBlock.FORMED, formed);
		if (updated.hasProperty(RackCasingBlock.ACCENT)) {
			updated = updated.setValue(RackCasingBlock.ACCENT, accent);
		}
		if (!formed && updated.hasProperty(RackComponentBlock.ACTIVE)) {
			updated = updated.setValue(RackComponentBlock.ACTIVE, false);
		}
		if (!formed && updated.hasProperty(GatewayBlock.PORTAL)) {
			updated = updated.setValue(GatewayBlock.PORTAL, GatewayState.CLOSED);
		}
		if (!formed && level.getBlockEntity(blockPos) instanceof StatusDisplayBlockEntity display) {
			display.show(List.of());
		}
		if (updated != state) {
			level.setBlock(blockPos, updated, Block.UPDATE_CLIENTS);
		}
	}

	/** A snapshot of the rack for the Server Core screen. */
	public RackStatus status() {
		if (scan == null) {
			return RackStatus.UNKNOWN;
		}
		return RackStatus.of(scan, scan.formed() ? disks(scan.structure()) : List.of(),
				scan.formed() ? links(scan.structure()).size() : 0);
	}

	/** The label of every disk in this rack's drive bays, in bay order. */
	public List<DiskLabel> disks(RackStructure rack) {
		List<DiskLabel> disks = new ArrayList<>();
		if (level == null) {
			return disks;
		}
		for (Pos pos : rack.driveBays()) {
			if (level.getBlockEntity(new BlockPos(pos.x(), pos.y(), pos.z())) instanceof DriveBayBlockEntity bay) {
				for (int slot = 0; slot < bay.getContainerSize(); slot++) {
					ItemStack stack = bay.getItem(slot);
					DiskLabel label = stack.get(ModComponents.DISK_LABEL);
					if (label != null) {
						disks.add(label);
					}
				}
			}
		}
		return disks;
	}

	/** Every Link Card in this rack's drive bays, in bay order, without repeats. */
	public List<dev.serverworks.item.LinkTarget> links(RackStructure rack) {
		List<dev.serverworks.item.LinkTarget> links = new ArrayList<>();
		if (level == null) {
			return links;
		}
		for (Pos pos : rack.driveBays()) {
			if (level.getBlockEntity(new BlockPos(pos.x(), pos.y(), pos.z())) instanceof DriveBayBlockEntity bay) {
				for (int slot = 0; slot < bay.getContainerSize(); slot++) {
					dev.serverworks.item.LinkTarget target = bay.getItem(slot).get(ModComponents.LINK_TARGET);
					if (target != null && links.stream().noneMatch(l -> l.rack().equals(target.rack()))) {
						links.add(target);
					}
				}
			}
		}
		return links;
	}

	// ---- lifecycle ----

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (level instanceof ServerLevel) {
			RackCores.add(this);
		}
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		if (level instanceof ServerLevel) {
			RackCores.add(this);
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		RackCores.remove(this);
	}

	/** Runs when the core block is actually broken or replaced, not when its chunk unloads. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (level instanceof ServerLevel serverLevel) {
			applyFormed(serverLevel, scan, null);
			scan = null;
			// Without its core a rack cannot be operated, so its server is stopped too.
			dev.serverworks.host.ServerInstance instance = dev.serverworks.Serverworks.host().instances().peek(rackId);
			if (instance != null) {
				instance.stop();
			}
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("rack_id", UUIDUtil.CODEC, rackId);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		rackId = input.read("rack_id", UUIDUtil.CODEC).orElseGet(UUID::randomUUID);
	}

	// ---- menu ----

	@Override
	public Component getDisplayName() {
		return NAME;
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new ServerCoreMenu(containerId, inventory, this);
	}
}
