package dev.serverworks.relay;

import dev.serverworks.Serverworks;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import org.jspecify.annotations.Nullable;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * One-port mode: players reach every rack through the main server's own port.
 *
 * <p>When the Gateway sends a player to a rack, it hands out a one-time ticket. The player's
 * game reconnects to the main server with the ticket added to the address in its handshake.
 * The main server sees the ticket, and instead of logging the player in itself, passes the
 * connection's bytes straight to the rack's server on this computer. Logging in, account
 * checks and encryption still happen between the player and the rack's server.
 */
public final class RackRelay {
	/** Put between the address and the ticket in a handshake's host name. */
	public static final String MARKER = "\0serverworks\0";
	private static final long TICKET_MILLIS = TimeUnit.MINUTES.toMillis(2);
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Map<String, Ticket> TICKETS = new ConcurrentHashMap<>();

	private record Ticket(int port, long expires) {
	}

	private RackRelay() {
	}

	/** Hands out a ticket for one connection to the rack server on this port. */
	public static String issue(int port) {
		long now = System.currentTimeMillis();
		TICKETS.values().removeIf(ticket -> ticket.expires() < now);
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		String token = HexFormat.of().formatHex(bytes);
		TICKETS.put(token, new Ticket(port, now + TICKET_MILLIS));
		return token;
	}

	/** The ticket in a handshake host name, if it carries one. */
	static @Nullable String ticketIn(String hostName) {
		int at = hostName.indexOf(MARKER);
		if (at < 0) {
			return null;
		}
		String token = hostName.substring(at + MARKER.length());
		return token.matches("[0-9a-f]{32}") ? token : null;
	}

	/** The host name with the ticket taken off, as the rack's server should see it. */
	static String withoutTicket(String hostName) {
		int at = hostName.indexOf(MARKER);
		return at < 0 ? hostName : hostName.substring(0, at);
	}

	/**
	 * Called with each handshake the main server receives. When it carries a valid ticket,
	 * the connection is handed to the rack's server and true is returned; otherwise the
	 * main server handles it as usual.
	 */
	public static boolean tryRelay(Channel channel, ClientIntentionPacket packet) {
		String token = ticketIn(packet.hostName());
		if (token == null) {
			return false;
		}
		Ticket ticket = TICKETS.remove(token);
		if (ticket == null || ticket.expires() < System.currentTimeMillis()) {
			Serverworks.LOGGER.info("Refused a rack connection with an unknown or expired ticket from {}", channel.remoteAddress());
			channel.close();
			return true;
		}
		ChannelPipeline pipeline = channel.pipeline();
		// From here on the main server only passes bytes along: its own packet handling and
		// encoding are taken out, and the decoder is replaced by the relay. The frame splitter
		// stays, so the relay receives whole packets even if more arrived with the handshake.
		RelayHandler relay = new RelayHandler(ticket.port(), packet.protocolVersion(), withoutTicket(packet.hostName()));
		try {
			// During the handshake the decoder's place is held by a placeholder; either may be there.
			String inbound = pipeline.get("decoder") != null ? "decoder" : "inbound_config";
			pipeline.replace(inbound, "serverworks_relay", relay);
			for (String name : new String[] {"legacy_query", "prepender", "encoder", "outbound_config", "hackfix", "packet_handler"}) {
				if (pipeline.get(name) != null) {
					pipeline.remove(name);
				}
			}
		} catch (RuntimeException e) {
			Serverworks.LOGGER.warn("Could not pass a connection through to a rack server", e);
			channel.close();
			return true;
		}
		Serverworks.LOGGER.info("Passing {} through to the rack server on port {}", channel.remoteAddress(), ticket.port());
		return true;
	}
}
