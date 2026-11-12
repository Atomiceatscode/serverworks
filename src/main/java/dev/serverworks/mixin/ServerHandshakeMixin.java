package dev.serverworks.mixin;

import dev.serverworks.relay.RackRelay;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands connections that carry a Gateway ticket to the rack's server instead of logging them in here. */
@Mixin(ServerHandshakePacketListenerImpl.class)
abstract class ServerHandshakeMixin {
	@Shadow
	@Final
	private Connection connection;

	@Inject(method = "handleIntention", at = @At("HEAD"), cancellable = true)
	private void serverworks$relayToRack(ClientIntentionPacket packet, CallbackInfo ci) {
		if (RackRelay.tryRelay(((ConnectionAccessor) connection).serverworks$channel(), packet)) {
			ci.cancel();
		}
	}
}
