package dev.serverworks.net;

import dev.serverworks.Serverworks;
import dev.serverworks.catalog.CatalogException;
import dev.serverworks.item.DiskLabel;
import dev.serverworks.machine.DiskWriterMenu;
import dev.serverworks.machine.ServerCoreMenu;
import dev.serverworks.net.SelectResultPayload.Outcome;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerworksNetworking {
	/** Minimum gap between catalog lookups one player can cause on the server. */
	private static final long MIN_SELECT_INTERVAL_MILLIS = 250;
	private static final Map<UUID, Long> LAST_SELECT = new ConcurrentHashMap<>();

	private ServerworksNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(SelectProjectPayload.TYPE, SelectProjectPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SelectResultPayload.TYPE, SelectResultPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RackStatusPayload.TYPE, RackStatusPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(InstanceStatusPayload.TYPE, InstanceStatusPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GatewayTripPayload.TYPE, GatewayTripPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientPackPayload.TYPE, ClientPackPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DiskUpdatesPayload.TYPE, DiskUpdatesPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RackActionPayload.TYPE, RackActionPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ConsoleCommandPayload.TYPE, ConsoleCommandPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RackActionPayload.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof ServerCoreMenu menu && menu.containerId == payload.containerId()) {
				menu.handle(payload.action());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(ConsoleCommandPayload.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof ServerCoreMenu menu && menu.containerId == payload.containerId()) {
				menu.handleCommand(payload.command());
			}
		});
		PayloadTypeRegistry.serverboundPlay().register(EditItemPayload.TYPE, EditItemPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BackupsPayload.TYPE, BackupsPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RestoreBackupPayload.TYPE, RestoreBackupPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RestoreBackupPayload.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof ServerCoreMenu menu && menu.containerId == payload.containerId()) {
				menu.restore(payload.name());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(EditItemPayload.TYPE, (payload, context) -> onEditItem(payload, context.player()));
		ServerPlayNetworking.registerGlobalReceiver(SelectProjectPayload.TYPE,
				(payload, context) -> onSelect(payload, context.server(), context.player()));
	}

	/** Writes an edited Config Card or World Drive back onto the item in the player's hand. */
	private static void onEditItem(EditItemPayload payload, ServerPlayer player) {
		net.minecraft.world.item.ItemStack held = player.getItemInHand(payload.hand());
		if (payload.settings() != null && held.is(dev.serverworks.registry.ModItems.CONFIG_CARD)) {
			held.set(dev.serverworks.registry.ModComponents.SERVER_SETTINGS, payload.settings());
		} else if (payload.drive() != null && held.is(dev.serverworks.registry.ModItems.WORLD_DRIVE)) {
			dev.serverworks.item.WorldDrive drive = payload.drive();
			dev.serverworks.item.WorldDrive old = held.get(dev.serverworks.registry.ModComponents.WORLD_DRIVE);
			// A drive keeps the id (and so the world) it already has; only new drives get one.
			java.util.UUID id = old != null ? old.id() : java.util.UUID.randomUUID();
			java.util.Optional<String> importSave = drive.importSave();
			// Copying a saved world reads this computer's saves, which only its owner may do.
			if (importSave.isPresent() && !dev.serverworks.machine.WorldDrives.canImport(player, importSave.get())) {
				importSave = java.util.Optional.empty();
			}
			held.set(dev.serverworks.registry.ModComponents.WORLD_DRIVE,
					new dev.serverworks.item.WorldDrive(id, drive.name(), drive.seed(), drive.type(), importSave));
		}
	}

	public static void forget(UUID player) {
		LAST_SELECT.remove(player);
	}

	private static void onSelect(SelectProjectPayload payload, MinecraftServer server, ServerPlayer player) {
		if (!(player.containerMenu instanceof DiskWriterMenu menu) || menu.containerId != payload.containerId()) {
			return;
		}
		// Clear the previous choice straight away, so the output slot never offers a disk for
		// a project other than the one the player is now looking at.
		menu.select(null);
		long now = System.currentTimeMillis();
		Long last = LAST_SELECT.put(player.getUUID(), now);
		if (last != null && now - last < MIN_SELECT_INTERVAL_MILLIS) {
			reply(player, payload, Outcome.RATE_LIMITED);
			return;
		}
		try {
			var catalog = Serverworks.catalog();
			var versionLookup = payload.versionId().isEmpty()
					? java.util.concurrent.CompletableFuture.<dev.serverworks.catalog.model.ProjectVersion>completedFuture(null)
					: catalog.version(payload.versionId());
			catalog.project(payload.projectId()).thenCombine(versionLookup, (project, version) -> {
				if (version == null) {
					return DiskLabel.of(project);
				}
				// A pin must name a version of this very project.
				if (!version.projectId().equals(project.id())) {
					throw new CatalogException(CatalogException.Kind.NOT_FOUND, "That version belongs to another project");
				}
				return DiskLabel.of(project).pinnedTo(version.id(), version.versionNumber());
			}).whenComplete((label, error) -> server.execute(() -> {
				// The player may have closed the writer, or opened another, while the catalog answered.
				if (player.hasDisconnected() || player.containerMenu != menu) {
					return;
				}
				if (error != null) {
					reply(player, payload, outcomeOf(error));
					return;
				}
				menu.select(label);
				reply(player, payload, Outcome.READY);
			}));
		} catch (CatalogException e) {
			reply(player, payload, outcomeOf(e));
		}
	}

	private static void reply(ServerPlayer player, SelectProjectPayload request, Outcome outcome) {
		ServerPlayNetworking.send(player, new SelectResultPayload(request.containerId(), request.projectId(), outcome));
	}

	private static Outcome outcomeOf(Throwable error) {
		Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
		if (cause instanceof CatalogException catalog) {
			return switch (catalog.kind()) {
				case NOT_FOUND -> Outcome.NOT_FOUND;
				case RATE_LIMITED -> Outcome.RATE_LIMITED;
				case BLOCKED -> Outcome.REFUSED;
				case NETWORK, BAD_RESPONSE -> Outcome.UNAVAILABLE;
			};
		}
		Serverworks.LOGGER.warn("Unexpected failure while looking up a catalog project", cause);
		return Outcome.UNAVAILABLE;
	}
}
