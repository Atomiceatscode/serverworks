package dev.serverworks.machine;

import dev.serverworks.Serverworks;
import dev.serverworks.host.ContentResolver;
import dev.serverworks.host.Deployment;
import dev.serverworks.host.HostRuntime;
import dev.serverworks.host.LaunchRequest;
import dev.serverworks.host.ModrinthCatalog;
import dev.serverworks.host.Providers;
import dev.serverworks.host.ServerInstance;
import dev.serverworks.item.DiskLabel;
import dev.serverworks.rack.RackScan;
import dev.serverworks.rack.RackStructure;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** The server-side rules for operating a rack: who may, and what starting one involves. */
public final class RackControl {
	private RackControl() {
	}

	/**
	 * Starting a server runs downloaded code on the host machine, so only the person who
	 * owns this game (in singleplayer) or an operator (on a dedicated server) may do it.
	 */
	public static boolean canControl(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (!dev.serverworks.ServerworksConfig.get().operatorsOnly) {
			return true;
		}
		return server.isSingleplayerOwner(player.nameAndId()) || server.getPlayerList().isOp(player.nameAndId());
	}

	/** Accepts the EULA for this machine. Returns a notice key when refused, else null. */
	public static @Nullable String acceptEula(ServerPlayer player) {
		if (!canControl(player)) {
			return "serverworks.notice.not_allowed";
		}
		try {
			Serverworks.host().acceptEula(player.getGameProfile().name());
			return null;
		} catch (IOException e) {
			Serverworks.LOGGER.error("Could not record EULA acceptance", e);
			return "serverworks.notice.eula_save_failed";
		}
	}

	/** Starts the rack's server. Returns a notice key when refused, else null. */
	public static @Nullable String start(ServerCoreBlockEntity core, ServerPlayer player) {
		if (!canControl(player)) {
			return "serverworks.notice.not_allowed";
		}
		String refused = launchAny(core, player);
		if (refused == null) {
			dev.serverworks.ModAdvancements.award(player, dev.serverworks.ModAdvancements.START_SERVER);
		}
		return refused;
	}

	/**
	 * Restarts a server that went to sleep because it was empty. Anyone may do this: an
	 * operator chose to run the server, and waking it only resumes that choice.
	 */
	public static @Nullable String wake(ServerCoreBlockEntity core, ServerPlayer player) {
		ServerInstance instance = Serverworks.host().instances().peek(core.rackId());
		if (instance == null || !instance.snapshot().hibernated()) {
			return "serverworks.notice.not_allowed";
		}
		return launchAny(core, player);
	}

	/** Starts a rack: a proxy brings up the racks it links to first, anything else just starts. */
	private static @Nullable String launchAny(ServerCoreBlockEntity core, ServerPlayer player) {
		core.rescan();
		RackScan scan = core.lastScan();
		if (scan != null && scan.formed() && scan.structure().platform().group() == dev.serverworks.rack.Platform.Group.PROXY) {
			return launchProxy(core, player, scan.structure());
		}
		return launch(core, player, null, null);
	}

	private static @Nullable String launchProxy(ServerCoreBlockEntity proxyCore, ServerPlayer player, RackStructure rack) {
		HostRuntime host = Serverworks.host();
		if (Providers.get(rack.platform()) == null) {
			return Providers.unsupportedReason(rack.platform());
		}
		List<ServerCoreBlockEntity> backends = new ArrayList<>();
		for (dev.serverworks.item.LinkTarget link : proxyCore.links(rack)) {
			ServerCoreBlockEntity backend = RackCores.byRackId(link.rack());
			if (backend == null) {
				return "serverworks.notice.link_unloaded";
			}
			backend.rescan();
			RackScan backendScan = backend.lastScan();
			if (backendScan == null || !backendScan.formed()
					|| !dev.serverworks.host.Forwarding.canBeBackend(backendScan.structure().platform())) {
				return "serverworks.notice.link_invalid";
			}
			backends.add(backend);
		}
		if (backends.isEmpty()) {
			return "serverworks.notice.no_links";
		}
		List<dev.serverworks.rack.Platform> platforms = backends.stream()
				.map(b -> b.lastScan().structure().platform()).toList();
		dev.serverworks.host.Forwarding mode = dev.serverworks.host.Forwarding.choose(rack.platform(), platforms);
		String secret;
		try {
			secret = host.secretFor(proxyCore.rackId());
		} catch (IOException e) {
			Serverworks.LOGGER.error("Could not create a forwarding secret", e);
			return "serverworks.notice.eula_save_failed";
		}
		dev.serverworks.host.ProxyRole.Backend role = new dev.serverworks.host.ProxyRole.Backend(mode, secret);
		List<dev.serverworks.host.ProxyRole.Target> targets = new ArrayList<>();
		for (int i = 0; i < backends.size(); i++) {
			ServerCoreBlockEntity backend = backends.get(i);
			int port = host.instances().portFor(backend.rackId());
			if (port < 0) {
				return "serverworks.notice.no_port";
			}
			targets.add(new dev.serverworks.host.ProxyRole.Target(
					dev.serverworks.host.ProxyConfigs.serverName(platforms.get(i).id(), i + 1), port));
			ServerInstance instance = host.instances().get(backend.rackId());
			if (instance.snapshot().state().active()) {
				// Already running: fine if it was set up for this proxy, otherwise it must restart first.
				if (!role.equals(host.linkOf(backend.rackId()))) {
					return "serverworks.notice.restart_linked";
				}
				continue;
			}
			String refused = launch(backend, player, role, null);
			if (refused != null) {
				return refused;
			}
		}
		String refused = launch(proxyCore, player, null, new dev.serverworks.host.ProxyRole.Proxy(mode, secret, targets));
		if (refused == null) {
			dev.serverworks.ModAdvancements.award(player, dev.serverworks.ModAdvancements.PROXY);
		}
		return refused;
	}

	private static @Nullable String launch(ServerCoreBlockEntity core, ServerPlayer player,
			dev.serverworks.host.ProxyRole.@Nullable Backend asBackend, dev.serverworks.host.ProxyRole.@Nullable Proxy asProxy) {
		HostRuntime host = Serverworks.host();
		if (!host.eulaAccepted()) {
			return "serverworks.notice.eula_required";
		}
		core.rescan();
		RackScan scan = core.lastScan();
		if (scan == null || !scan.formed()) {
			return "serverworks.notice.not_formed";
		}
		RackStructure rack = scan.structure();
		if (!Providers.supports(rack.platform())) {
			return Providers.unsupportedReason(rack.platform());
		}
		List<DiskLabel> disks = core.disks(rack);
		List<ContentResolver.Wanted> wanted = new ArrayList<>();
		long cartridges = disks.stream().filter(d -> d.type() == dev.serverworks.catalog.model.ProjectType.MODPACK).count();
		if (cartridges > 1) {
			return "serverworks.notice.one_cartridge";
		}
		for (DiskLabel disk : disks) {
			if (!rack.platform().accepts(disk.loaders())) {
				return "serverworks.notice.incompatible_disks";
			}
			if (wanted.stream().noneMatch(w -> w.projectId().equals(disk.projectId()))) {
				wanted.add(new ContentResolver.Wanted(disk.projectId(), disk.title(), disk.pinnedVersion().orElse(null),
						disk.clientSide(), disk.type() == dev.serverworks.catalog.model.ProjectType.MODPACK));
			}
		}
		ServerInstance instance = host.instances().get(core.rackId());
		if (instance.snapshot().state().active()) {
			return null;
		}
		if (host.instances().activeCount() >= HostRuntime.maxRunning()) {
			return "serverworks.notice.too_many";
		}
		int port = host.instances().portFor(core.rackId());
		if (port < 0) {
			return "serverworks.notice.no_port";
		}

		MinecraftServer server = player.level().getServer();
		LaunchRequest request = new LaunchRequest(
				core.rackId(),
				rack.platform(),
				SharedConstants.getCurrentVersion().name(),
				rack.ramMb(),
				rack.maxPlayers(),
				port,
				server.isDedicatedServer() ? "" : "127.0.0.1",
				// Matches the host, except in a development environment, whose players have no real accounts.
				server.usesAuthentication() && !net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment(),
				true,
				"A Serverworks rack",
				wanted,
				asBackend,
				asProxy);
		Deployment deployment = new Deployment(host.root(), host.fetcher(),
				new ContentResolver(new ModrinthCatalog(Serverworks.catalog())), request, HostRuntime.currentJava(),
				HostRuntime.currentJavaVersion());
		instance.start(port, deployment);
		host.setLink(core.rackId(), asBackend);
		Serverworks.LOGGER.info("{} started the {} rack server at {} on port {}", player.getGameProfile().name(),
				rack.platform().id(), core.getBlockPos().toShortString(), port);
		return null;
	}

	public static @Nullable String stop(ServerCoreBlockEntity core, ServerPlayer player) {
		if (!canControl(player)) {
			return "serverworks.notice.not_allowed";
		}
		ServerInstance instance = Serverworks.host().instances().peek(core.rackId());
		if (instance != null) {
			instance.stop();
		}
		return null;
	}

	public static @Nullable String command(ServerCoreBlockEntity core, ServerPlayer player, String command) {
		if (!canControl(player)) {
			return "serverworks.notice.not_allowed";
		}
		ServerInstance instance = Serverworks.host().instances().peek(core.rackId());
		if (instance == null || !instance.command(command)) {
			return "serverworks.notice.not_running";
		}
		return null;
	}
}
