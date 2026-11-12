package dev.serverworks.machine;

import dev.serverworks.item.DiskLabel;
import dev.serverworks.rack.Cell;
import dev.serverworks.rack.Pos;
import dev.serverworks.rack.RackScan;
import dev.serverworks.rack.RackStructure;
import dev.serverworks.rack.RackValidator;
import dev.serverworks.registry.ModBlockEntities;
import dev.serverworks.registry.ModComponents;
import dev.serverworks.registry.ModItems;
import dev.serverworks.rack.RackLimits;
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
import java.util.Map;
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
	/** When on, a redstone signal starting or stopping starts or stops the rack. Off until an operator enables it. */
	private boolean redstoneControl;
	/** Whether the core was powered at the last look, so only changes act. */
	private boolean powered;
	/** The comparator output last announced to neighbours. */
	private int signal;
	/** Set while the server stops for an update; it starts again once it is down. */
	private boolean restartPending;
	/** Game rule commands from the Config Card, sent once the server is up. */
	private List<String> pendingRules = List.of();

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
			core.updateSignal();
			core.continueRestart();
		}
	}

	// ---- updates ----

	/** Stops the server and starts it again once it is down, so new versions are installed. */
	void restart() {
		dev.serverworks.host.ServerInstance instance = dev.serverworks.Serverworks.host().instances().peek(rackId);
		if (instance == null) {
			return;
		}
		restartPending = true;
		instance.stop();
	}

	private void continueRestart() {
		if (!restartPending) {
			return;
		}
		dev.serverworks.host.ServerInstance instance = dev.serverworks.Serverworks.host().instances().peek(rackId);
		if (instance == null || !instance.snapshot().state().active()) {
			restartPending = false;
			String refused = RackControl.restart(this);
			if (refused != null) {
				dev.serverworks.Serverworks.LOGGER.info("The rack at {} did not restart after updating: {}",
						worldPosition.toShortString(), refused);
			}
		}
	}

	/** Rewrites pinned disks in this rack's bays to the versions given, by project id. */
	void repin(Map<String, dev.serverworks.catalog.model.ProjectVersion> newest) {
		if (level == null || scan == null || !scan.formed() || newest.isEmpty()) {
			return;
		}
		for (Pos pos : scan.structure().driveBays()) {
			if (level.getBlockEntity(new BlockPos(pos.x(), pos.y(), pos.z())) instanceof DriveBayBlockEntity bay) {
				for (int slot = 0; slot < bay.getContainerSize(); slot++) {
					ItemStack stack = bay.getItem(slot);
					DiskLabel label = stack.get(ModComponents.DISK_LABEL);
					if (label == null || label.pinnedVersion().isEmpty() || !newest.containsKey(label.projectId())) {
						continue;
					}
					var version = newest.get(label.projectId());
					ItemStack updated = stack.copy();
					updated.set(ModComponents.DISK_LABEL, label.pinnedTo(version.id(), version.versionNumber()));
					bay.setItem(slot, updated);
				}
				bay.setChanged();
			}
		}
		contentsChanged();
	}

	// ---- redstone ----

	public boolean redstoneControl() {
		return redstoneControl;
	}

	/** Turns redstone control on or off. The current power level becomes the starting point. */
	void setRedstoneControl(boolean on) {
		redstoneControl = on;
		if (level != null) {
			powered = level.hasNeighborSignal(worldPosition);
		}
		statusVersion++;
		setChanged();
	}

	/** Called when a neighbouring block changes; acts only when the power level changes. */
	void neighborSignal(boolean nowPowered) {
		if (nowPowered == powered) {
			return;
		}
		powered = nowPowered;
		setChanged();
		if (redstoneControl) {
			RackControl.redstone(this, nowPowered);
		}
	}

	/**
	 * What a comparator reads: 0 while the server is not running, 1 when it runs empty, and up to
	 * 15 as it fills.
	 */
	public int comparatorSignal() {
		if (scan == null || !scan.formed()) {
			return 0;
		}
		dev.serverworks.host.ServerInstance instance = dev.serverworks.Serverworks.host().instances().peek(rackId);
		if (instance == null) {
			return 0;
		}
		dev.serverworks.host.ServerInstance.Snapshot snapshot = instance.snapshot();
		if (snapshot.state() != dev.serverworks.host.ServerInstance.State.RUNNING) {
			return 0;
		}
		int max = Math.max(1, maxPlayers(scan.structure()));
		int online = Math.max(0, snapshot.playersOnline());
		return 1 + Math.min(14, (int) Math.ceil(14.0 * online / max));
	}

	private void updateSignal() {
		int now = comparatorSignal();
		if (now != signal && level != null) {
			signal = now;
			level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
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
		if (running && lastState != dev.serverworks.host.ServerInstance.State.RUNNING && instance != null) {
			for (String command : pendingRules) {
				instance.command(command);
			}
			pendingRules = List.of();
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
			List<DisplayLine> lines = displayLines(serverLevel, rack, snapshot, open, instance, maxPlayers(rack));
			List<StatusDisplayBlockEntity.Head> heads = !running || instance == null ? List.of()
					: instance.players().stream().map(p -> new StatusDisplayBlockEntity.Head(p.name(), p.id())).toList();
			for (Pos pos : rack.displays()) {
				if (serverLevel.getBlockEntity(new BlockPos(pos.x(), pos.y(), pos.z())) instanceof StatusDisplayBlockEntity display) {
					display.show(lines, heads);
				}
			}
		}
	}

	private static List<DisplayLine> displayLines(ServerLevel level, RackStructure rack,
			dev.serverworks.host.ServerInstance.Snapshot snapshot, boolean open,
			dev.serverworks.host.@Nullable ServerInstance instance, int maxPlayers) {
		var state = snapshot.state();
		String platform = rack.platform().translationKey();
		if (state == dev.serverworks.host.ServerInstance.State.RUNNING) {
			DisplayLine players = snapshot.playersOnline() >= 0
					? DisplayLine.of("serverworks.display.players", 0xFFFFFF, snapshot.playersOnline(), maxPlayers)
					: DisplayLine.of(platform, 0xFFFFFF);
			String publicAddress = dev.serverworks.ServerworksConfig.get().publicAddress;
			DisplayLine address = !level.getServer().isDedicatedServer()
					? DisplayLine.of("serverworks.display.local", 0x9FB2DA, snapshot.port())
					: !dev.serverworks.ServerworksConfig.get().directPorts
					? DisplayLine.of("serverworks.display.gateway", 0x9FB2DA)
					: publicAddress.isEmpty() ? DisplayLine.of("serverworks.display.port", 0x9FB2DA, snapshot.port())
					: DisplayLine.of("serverworks.display.address", 0x9FB2DA, publicAddress, snapshot.port());
			// Line three is left for the players' heads, drawn by the client.
			return List.of(DisplayLine.of(open ? "serverworks.display.online" : "serverworks.display.online_mods",
					open ? 0x63E08A : 0xFFB347), players, DisplayLine.EMPTY, stats(instance), address);
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

	/** True when players reach this rack only through a Gateway, on a dedicated server in one-port mode. */
	private boolean throughGateway() {
		return level instanceof ServerLevel serverLevel && serverLevel.getServer().isDedicatedServer()
				&& !dev.serverworks.ServerworksConfig.get().directPorts;
	}

	/** "1.2 GB · up 3h 5m", or just the uptime when memory is unknown. */
	private static DisplayLine stats(dev.serverworks.host.@Nullable ServerInstance instance) {
		if (instance == null || instance.runningSince() == 0) {
			return DisplayLine.EMPTY;
		}
		long minutes = (System.currentTimeMillis() - instance.runningSince()) / 60_000;
		String uptime = minutes < 1 ? "<1m" : minutes < 60 ? minutes + "m" : (minutes / 60) + "h " + (minutes % 60) + "m";
		int memory = instance.memoryMb();
		if (memory <= 0) {
			return DisplayLine.of("serverworks.display.uptime", 0x9FB2DA, uptime);
		}
		String gigabytes = String.format(java.util.Locale.ROOT, "%.1f", memory / 1024.0);
		return DisplayLine.of("serverworks.display.stats", 0x9FB2DA, gigabytes, uptime);
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
		if (!scan.formed()) {
			return RackStatus.of(scan, List.of(), 0, redstoneControl, throughGateway(), 0, 0, 0);
		}
		RackStructure rack = scan.structure();
		int ram = ramMb(rack);
		return RackStatus.of(scan, disks(rack), links(rack).size(), redstoneControl, throughGateway(), ram,
				RackLimits.playersFor(ram), diskSlots(rack));
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

	void setPendingRules(List<String> commands) {
		pendingRules = List.copyOf(commands);
	}

	/** The rack's memory: the base amount plus every RAM stick in its Memory Banks. */
	public int ramMb(RackStructure rack) {
		int sticks = 0;
		if (level != null) {
			for (Pos pos : rack.members()) {
				if (level.getBlockEntity(new BlockPos(pos.x(), pos.y(), pos.z())) instanceof MemoryBankBlockEntity bank) {
					sticks += bank.megabytes();
				}
			}
		}
		return RackLimits.ramMb(sticks);
	}

	public int maxPlayers(RackStructure rack) {
		return RackLimits.playersFor(ramMb(rack));
	}

	/** How many disks the rack's Drive Bays hold together. */
	public int diskSlots(RackStructure rack) {
		int slots = 0;
		if (level != null) {
			for (Pos pos : rack.driveBays()) {
				if (level.getBlockEntity(new BlockPos(pos.x(), pos.y(), pos.z())) instanceof DriveBayBlockEntity bay) {
					slots += bay.capacity();
				}
			}
		}
		return slots;
	}

	/** The first Config Card in this rack's bays, or null. A blank card counts, with default settings. */
	public dev.serverworks.item.@org.jspecify.annotations.Nullable ServerSettings configCard(RackStructure rack) {
		ItemStack card = firstInBays(rack, ModItems.CONFIG_CARD);
		return card == null ? null : dev.serverworks.item.ConfigCardItem.settings(card);
	}

	/** The first World Drive in this rack's bays, or null for the rack's own world. */
	public dev.serverworks.item.@org.jspecify.annotations.Nullable WorldDrive worldDrive(RackStructure rack) {
		ItemStack drive = firstInBays(rack, ModItems.WORLD_DRIVE);
		return drive == null ? null : drive.get(ModComponents.WORLD_DRIVE);
	}

	private @Nullable ItemStack firstInBays(RackStructure rack, net.minecraft.world.item.Item item) {
		if (level == null) {
			return null;
		}
		for (Pos pos : rack.driveBays()) {
			if (level.getBlockEntity(new BlockPos(pos.x(), pos.y(), pos.z())) instanceof DriveBayBlockEntity bay) {
				for (int slot = 0; slot < bay.getContainerSize(); slot++) {
					if (bay.getItem(slot).is(item)) {
						return bay.getItem(slot);
					}
				}
			}
		}
		return null;
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
		output.putBoolean("redstone_control", redstoneControl);
		output.putBoolean("powered", powered);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		rackId = input.read("rack_id", UUIDUtil.CODEC).orElseGet(UUID::randomUUID);
		redstoneControl = input.getBooleanOr("redstone_control", false);
		powered = input.getBooleanOr("powered", false);
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
