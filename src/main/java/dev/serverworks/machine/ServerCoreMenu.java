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
			ServerPlayNetworking.send(serverPlayer, new RackStatusPayload(containerId, core.status()));
		}
		ServerInstance instance = Serverworks.host().instances().peek(core.rackId());
		ServerInstance.Snapshot snapshot = instance == null ? ServerInstance.Snapshot.IDLE : instance.snapshot();
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
