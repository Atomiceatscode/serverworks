package dev.serverworks.host;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Asks a Minecraft server how many players it has, the same way the multiplayer screen
 * does. Works with every server software, because all of them answer this.
 */
public final class StatusPinger {
	private static final int TIMEOUT_MILLIS = 2000;
	private static final int MAX_RESPONSE = 256 * 1024;

	private StatusPinger() {
	}

	/** @param online -1 when the answer did not say */
	public record Status(int online, int max, java.util.List<Player> sample) {
		public Status(int online, int max) {
			this(online, max, java.util.List.of());
		}
	}

	/** One of the players a server lists in its status; servers list up to a dozen. */
	public record Player(String name, java.util.UUID id) {
	}



	public static Status ping(String host, int port) throws IOException {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, port), TIMEOUT_MILLIS);
			socket.setSoTimeout(TIMEOUT_MILLIS);
			OutputStream out = socket.getOutputStream();

			ByteArrayOutputStream handshake = new ByteArrayOutputStream();
			writeVarInt(handshake, 0x00);
			writeVarInt(handshake, -1); // Protocol version; any value is accepted for a status request.
			writeString(handshake, host);
			handshake.write((port >> 8) & 0xFF);
			handshake.write(port & 0xFF);
			writeVarInt(handshake, 1); // Next state: status.
			writePacket(out, handshake.toByteArray());
			writePacket(out, new byte[] {0x00}); // Status request.
			out.flush();

			DataInputStream in = new DataInputStream(socket.getInputStream());
			int length = readVarInt(in);
			if (length <= 0 || length > MAX_RESPONSE) {
				throw new IOException("Unexpected status length " + length);
			}
			byte[] body = in.readNBytes(length);
			DataInputStream packet = new DataInputStream(new java.io.ByteArrayInputStream(body));
			if (readVarInt(packet) != 0x00) {
				throw new IOException("Unexpected status packet");
			}
			int jsonLength = readVarInt(packet);
			if (jsonLength < 0 || jsonLength > body.length) {
				throw new IOException("Bad status text length");
			}
			String json = new String(packet.readNBytes(jsonLength), StandardCharsets.UTF_8);
			return parse(json);
		}
	}

	static Status parse(String json) throws IOException {
		try {
			JsonElement root = JsonParser.parseString(json);
			JsonObject players = root.getAsJsonObject().getAsJsonObject("players");
			if (players == null) {
				return new Status(-1, -1);
			}
			int online = players.has("online") ? players.get("online").getAsInt() : -1;
			int max = players.has("max") ? players.get("max").getAsInt() : -1;
			java.util.List<Player> sample = new java.util.ArrayList<>();
			if (players.has("sample") && players.get("sample").isJsonArray()) {
				for (JsonElement entry : players.getAsJsonArray("sample")) {
					try {
						JsonObject player = entry.getAsJsonObject();
						String name = player.get("name").getAsString();
						if (name.matches("[A-Za-z0-9_]{1,16}") && sample.size() < 12) {
							sample.add(new Player(name, java.util.UUID.fromString(player.get("id").getAsString())));
						}
					} catch (RuntimeException e) {
						// Some servers put decorative lines in the sample; they are not players.
					}
				}
			}
			return new Status(online, max, sample);
		} catch (RuntimeException e) {
			throw new IOException("Unreadable status", e);
		}
	}

	private static void writePacket(OutputStream out, byte[] payload) throws IOException {
		ByteArrayOutputStream framed = new ByteArrayOutputStream();
		writeVarInt(framed, payload.length);
		framed.write(payload);
		out.write(framed.toByteArray());
	}

	static void writeVarInt(OutputStream out, int value) throws IOException {
		while ((value & ~0x7F) != 0) {
			out.write((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.write(value);
	}

	private static void writeString(OutputStream out, String value) throws IOException {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		writeVarInt(out, bytes.length);
		out.write(bytes);
	}

	static int readVarInt(InputStream in) throws IOException {
		int value = 0;
		for (int shift = 0; shift < 35; shift += 7) {
			int b = in.read();
			if (b < 0) {
				throw new IOException("Connection closed");
			}
			value |= (b & 0x7F) << shift;
			if ((b & 0x80) == 0) {
				return value;
			}
		}
		throw new IOException("VarInt too long");
	}
}
