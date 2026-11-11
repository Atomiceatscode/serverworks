package dev.serverworks.machine;

import dev.serverworks.Serverworks;
import dev.serverworks.host.ConsoleBuffer;
import dev.serverworks.host.ServerInstance;
import dev.serverworks.net.ClientPackPayload;
import dev.serverworks.net.InstanceStatusPayload;
import dev.serverworks.net.RackActionPayload;
import dev.serverworks.net.RackStatusPayload;
import dev.serverworks.registry.ModMenus;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The Server Core's screen has no item slots; it is a status and control panel. While it
 * is open the server pushes the rack's structure, its server's state, and new console
 * lines whenever any of them change.
 */
public final class ServerCoreMenu extends AbstractContainerMenu {
	/** Generous, because the block that was clicked may be the far side of a large rack. */
	private static final double MAX_DISTANCE_SQUARED = 16 * 16;

	private final @Nullable ServerCoreBlockEntity core;
	private final Player player;
	private int sentRackVersion = -1;
	private int sentInstanceVersion = -1;
	private boolean sentEula;
	private long sentConsole = -1;
	private String notice = "";
	/** Game time of the last client pack sent, so the button cannot be used to flood the connection. */
	private long lastPackSent = -1000;
	private boolean noticeDirty;

	/** Client side. */
	public ServerCoreMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, null);
	}

	public ServerCoreMenu(int containerId, Inventory inventory, @Nullable ServerCoreBlockEntity core) {
		super(ModMenus.SERVER_CORE, containerId);
		this.core = core;
		this.player = inventory.player;
	}

	public @Nullable ServerCoreBlockEntity core() {
		return core;
	}

	/** Server side: runs a button press from this player's screen. */
	public void handle(RackActionPayload.Action action) {
		if (core == null || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		String refused = switch (action) {
			case START -> RackControl.start(core, serverPlayer);
			case STOP -> RackControl.stop(core, serverPlayer);
			case ACCEPT_EULA -> RackControl.acceptEula(serverPlayer);
			case EXPORT_PACK -> sendClientPack(serverPlayer);
			case TOGGLE_REDSTONE -> RackControl.setRedstoneControl(core, serverPlayer, !core.redstoneControl());
			case UPDATE_DISKS -> RackControl.updateDisks(core, serverPlayer);
			case BACKUP_NOW -> backUp(serverPlayer);
		};
		setNotice(refused);
	}

	/** Anyone at the rack may take its client pack: it only lists public downloads. */
	private @Nullable String sendClientPack(ServerPlayer serverPlayer) {
		long now = serverPlayer.level().getGameTime();
		if (now - lastPackSent < 40) {
			return null;
		}
		lastPackSent = now;
		java.nio.file.Path pack = packPath();
		try {
			if (pack == null || !java.nio.file.Files.exists(pack) || java.nio.file.Files.size(pack) > ClientPackPayload.MAX_BYTES) {
				return "serverworks.notice.no_pack";
			}
			String name = "serverworks-" + core.rackId().toString().substring(0, 8) + ".mrpack";
			ServerPlayNetworking.send(serverPlayer, new ClientPackPayload(name, java.nio.file.Files.readAllBytes(pack)));
			return null;
		} catch (java.io.IOException e) {
			return "serverworks.notice.no_pack";
		}
	}

	private java.nio.file.@Nullable Path packPath() {
		return core == null ? null : dev.serverworks.host.Deployment.instanceDir(Serverworks.host().root(), core.rackId())
				.resolve(dev.serverworks.host.ClientPack.FILE_NAME);
	}

	/** True while a backup or restore started from this screen is running. */
	private boolean backupBusy;
	private boolean backupsSent;

	private @Nullable String backUp(ServerPlayer serverPlayer) {
		if (!RackControl.canControl(serverPlayer)) {
			return "serverworks.notice.not_allowed";
		}
		if (backupBusy) {
			return null;
		}
		java.nio.file.Path dir = dev.serverworks.host.Deployment.instanceDir(Serverworks.host().root(), core.rackId());
		if (!java.nio.file.Files.isDirectory(dir.resolve(dev.serverworks.host.Backups.levelName(dir)))) {
			return "serverworks.notice.no_world";
		}
		backupBusy = true;
		backupsSent = false;
		var server = serverPlayer.level().getServer();
		dev.serverworks.host.Backups.backUp(dir, Serverworks.host().instances().peek(core.rackId()),
				dev.serverworks.ServerworksConfig.get().keepBackups).whenComplete((made, error) -> server.execute(() -> {
			backupBusy = false;
			backupsSent = false;
			setNotice(error == null ? "serverworks.notice.backed_up" : "serverworks.notice.backup_failed");
		}));
		return null;
	}

	/** Server side: restores a backup, if the player may and the server is stopped. */
	public void restore(String name) {
		if (core == null || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		if (!RackControl.canControl(serverPlayer)) {
			setNotice("serverworks.notice.not_allowed");
			return;
		}
		ServerInstance instance = Serverworks.host().instances().peek(core.rackId());
		if (instance != null && instance.snapshot().state().active()) {
			setNotice("serverworks.notice.stop_first");
			return;
		}
		if (backupBusy) {
			return;
		}
		backupBusy = true;
		backupsSent = false;
		java.nio.file.Path dir = dev.serverworks.host.Deployment.instanceDir(Serverworks.host().root(), core.rackId());
		var server = serverPlayer.level().getServer();
		java.util.concurrent.CompletableFuture.runAsync(() -> {
			try {
				dev.serverworks.host.Backups.restore(dir, name);
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}).whenComplete((ignored, error) -> server.execute(() -> {
			backupBusy = false;
			backupsSent = false;
			if (error != null) {
				Serverworks.LOGGER.warn("Could not restore backup {}", name, error);
			}
			setNotice(error == null ? "serverworks.notice.restored" : "serverworks.notice.restore_failed");
		}));
	}

	public void handleCommand(String command) {
		if (core != null && player instanceof ServerPlayer serverPlayer) {
			setNotice(RackControl.command(core, serverPlayer, command));
		}
	}

	private void setNotice(@Nullable String key) {
		notice = key == null ? "" : key;
		noticeDirty = true;
	}

	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (core == null || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		if (core.statusVersion() != sentRackVersion) {
			sentRackVersion = core.statusVersion();
			RackStatus status = core.status();
			ServerPlayNetworking.send(serverPlayer, new RackStatusPayload(containerId, status));
			requestUpdates(serverPlayer, status);
		}
		ServerInstance instance = Serverworks.host().instances().peek(core.rackId());
		ServerInstance.Snapshot snapshot = instance == null ? ServerInstance.Snapshot.IDLE : instance.snapshot();
		if (!backupsSent) {
			backupsSent = true;
			// Backups are the world's history, so only those who run the rack see them.
			List<dev.serverworks.host.Backups.Backup> list = RackControl.canControl(serverPlayer)
					? dev.serverworks.host.Backups.list(dev.serverworks.host.Deployment.instanceDir(Serverworks.host().root(), core.rackId()))
					: List.of();
			ServerPlayNetworking.send(serverPlayer, new dev.serverworks.net.BackupsPayload(containerId, list, backupBusy));
		}
		boolean eula = Serverworks.host().eulaAccepted();
		boolean consoleBehind = instance != null && sentConsole >= 0 && instance.console().nextSequence() > sentConsole
				&& RackControl.canControl(serverPlayer);
		if (snapshot.version() == sentInstanceVersion && eula == sentEula && !noticeDirty && !consoleBehind
				&& sentInstanceVersion != -1) {
			return;
		}
		sentInstanceVersion = snapshot.version();
		sentEula = eula;
		noticeDirty = false;
		java.nio.file.Path pack = packPath();
		boolean packAvailable = pack != null && java.nio.file.Files.exists(pack);
		// The console shows player addresses and identities, so only those who run the rack see it.
		boolean control = RackControl.canControl(serverPlayer);
		List<String> lines = List.of();
		long from = 0;
		if (instance != null && control) {
			ConsoleBuffer console = instance.console();
			long start = sentConsole < 0 ? Math.max(0, console.nextSequence() - InstanceStatusPayload.MAX_LINES) : sentConsole;
			ConsoleBuffer.Page page = console.since(start, InstanceStatusPayload.MAX_LINES);
			from = page.firstSequence();
			lines = page.lines();
			sentConsole = page.nextSequence();
		}
		ServerPlayNetworking.send(serverPlayer, new InstanceStatusPayload(containerId, snapshot.state(), snapshot.progress(),
				snapshot.failure(), control ? snapshot.detail() : "", snapshot.port(), snapshot.playersOnline(),
				snapshot.hibernated(), packAvailable, eula, control, notice, from, lines));
		notice = "";
	}

	/** Looks for newer versions of the rack's disks in the background and sends them when known. */
	private void requestUpdates(ServerPlayer serverPlayer, RackStatus status) {
		if (core == null || !status.formed() || status.platform() == null || status.disks().isEmpty()) {
			ServerPlayNetworking.send(serverPlayer, new dev.serverworks.net.DiskUpdatesPayload(containerId, List.of()));
			return;
		}
		int asked = sentRackVersion;
		var server = serverPlayer.level().getServer();
		DiskUpdates.check(core.rackId(), status.platform(), status.disks()).thenAccept(updates -> server.execute(() -> {
			if (serverPlayer.containerMenu == this && asked == sentRackVersion && !serverPlayer.hasDisconnected()) {
				ServerPlayNetworking.send(serverPlayer, new dev.serverworks.net.DiskUpdatesPayload(containerId, updates));
			}
		}));
	}

	@Override
	public boolean stillValid(Player player) {
		if (core == null) {
			return true;
		}
		return !core.isRemoved() && player.distanceToSqr(Vec3.atCenterOf(core.getBlockPos())) <= MAX_DISTANCE_SQUARED;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		return ItemStack.EMPTY;
	}
}
