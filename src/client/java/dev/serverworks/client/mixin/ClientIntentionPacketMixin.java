package dev.serverworks.client.mixin;

import dev.serverworks.client.trip.Trips;
import dev.serverworks.relay.RackRelay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.handshake.ClientIntent;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * In one-port mode, adds the Gateway's ticket to the host name of the next login handshake,
 * so the main server knows to pass this connection to the rack. Status pings never carry it.
 */
@Mixin(ClientIntentionPacket.class)
abstract class ClientIntentionPacketMixin {
	@ModifyArg(method = "write", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/FriendlyByteBuf;writeUtf(Ljava/lang/String;)Lnet/minecraft/network/FriendlyByteBuf;"))
	private String serverworks$addTicket(String hostName) {
		ClientIntentionPacket self = (ClientIntentionPacket) (Object) this;
		if (self.intention() != ClientIntent.LOGIN) {
			return hostName;
		}
		String ticket = Trips.takeTicket();
		return ticket == null ? hostName : hostName + RackRelay.MARKER + ticket;
	}
}
