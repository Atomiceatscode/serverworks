package dev.serverworks.machine;

import dev.serverworks.Serverworks;
import dev.serverworks.host.ServerInstance;
import dev.serverworks.net.GatewayTripPayload;
import dev.serverworks.rack.Compatibility;
import dev.serverworks.rack.RackScan;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** What happens when a player uses a Gateway. */
public final class RackGateway {
	private RackGateway() {
	}

	public static void use(ServerCoreBlockEntity core, ServerPlayer player, BlockPos gatewayPos) {
		RackScan scan = core.lastScan();
		if (scan == null || !scan.formed()) {
			return;
		}
		ServerInstance instance = Serverworks.host().instances().peek(core.rackId());
		ServerInstance.Snapshot snapshot = instance == null ? ServerInstance.Snapshot.IDLE : instance.snapshot();

		if (snapshot.state() != ServerInstance.State.RUNNING) {
			if (snapshot.hibernated() && snapshot.state().canStart()) {
				String refused = RackControl.wake(core, player);
				message(player, refused == null
						? Component.translatable("serverworks.gateway.waking").withStyle(ChatFormatting.AQUA)
						: Component.translatable(refused).withStyle(ChatFormatting.RED));
			} else if (snapshot.state().active()) {
				message(player, Component.translatable("serverworks.gateway.starting").withStyle(ChatFormatting.AQUA));
			} else {
				message(player, Component.translatable("serverworks.gateway.closed").withStyle(ChatFormatting.GRAY));
			}
			return;
		}

		if (Serverworks.host().linkOf(core.rackId()) != null) {
			message(player, Component.translatable("serverworks.gateway.behind_proxy").withStyle(ChatFormatting.GOLD));
			return;
		}
		boolean proxy = scan.structure().platform().group() == dev.serverworks.rack.Platform.Group.PROXY;
		java.util.List<dev.serverworks.item.DiskLabel> everything = new java.util.ArrayList<>(core.disks(scan.structure()));
		if (proxy) {
			// Players land on the servers behind the proxy, so their content decides who can join.
			for (dev.serverworks.item.LinkTarget link : core.links(scan.structure())) {
				ServerCoreBlockEntity backend = RackCores.byRackId(link.rack());
				if (backend != null && backend.lastScan() != null && backend.lastScan().formed()) {
					everything.addAll(backend.disks(backend.lastScan().structure()));
				}
			}
		}
		Compatibility.Result compatibility = Compatibility.check(everything);
		if (!compatibility.open()) {
			message(player, Component.translatable("serverworks.gateway.restricted",
					String.join(", ", compatibility.clientMods())).withStyle(ChatFormatting.GOLD));
			return;
		}

		// New servers only admit whitelisted players. Whoever the Gateway sends is let in.
		String playerName = player.getGameProfile().name();
		boolean admitted = proxy || (playerName.matches("[A-Za-z0-9_]{1,16}") && instance != null
				&& instance.command("whitelist add " + playerName));
		if (!admitted) {
			message(player, Component.translatable("serverworks.gateway.closed").withStyle(ChatFormatting.GRAY));
			return;
		}

		player.level().playSound(null, gatewayPos, dev.serverworks.registry.ModSounds.GATEWAY_TRAVEL, SoundSource.BLOCKS, 1.0F, 1.0F);
		Component name = Component.translatable("serverworks.gateway.destination",
				Component.translatable(scan.structure().platform().translationKey()));
		dev.serverworks.ModAdvancements.award(player, dev.serverworks.ModAdvancements.GO_THROUGH);
		ServerPlayNetworking.send(player, new GatewayTripPayload(snapshot.port(), name.getString()));
		Serverworks.LOGGER.info("{} went through the gateway at {} to port {}", player.getGameProfile().name(),
				gatewayPos.toShortString(), snapshot.port());
	}

	private static void message(ServerPlayer player, Component text) {
		player.sendOverlayMessage(text);
	}
}
