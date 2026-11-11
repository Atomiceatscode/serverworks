package dev.serverworks.host;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusAndIdleTest {
	@Test
	void pingReadsThePlayerCountFromAStatusResponse() throws Exception {
		String json = "{\"version\":{\"name\":\"26.3\",\"protocol\":800},\"players\":{\"max\":40,\"online\":3},\"description\":\"hi\"}";
		try (ServerSocket server = new ServerSocket(0)) {
			Thread fake = new Thread(() -> {
				try (Socket client = server.accept()) {
					DataInputStream in = new DataInputStream(client.getInputStream());
					int handshakeLength = StatusPinger.readVarInt(in);
					byte[] handshake = in.readNBytes(handshakeLength);
					assertEquals(0x00, handshake[0]);
					assertEquals(1, handshake[handshake.length - 1], "next state must be status");
					assertEquals(1, StatusPinger.readVarInt(in));
					assertEquals(0x00, in.read());

					ByteArrayOutputStream body = new ByteArrayOutputStream();
					StatusPinger.writeVarInt(body, 0x00);
					byte[] text = json.getBytes(StandardCharsets.UTF_8);
					StatusPinger.writeVarInt(body, text.length);
					body.write(text);
					OutputStream out = client.getOutputStream();
					StatusPinger.writeVarInt(out, body.size());
					out.write(body.toByteArray());
					out.flush();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			fake.start();
			StatusPinger.Status status = StatusPinger.ping("127.0.0.1", server.getLocalPort());
			fake.join(5000);
			assertEquals(3, status.online());
			assertEquals(40, status.max());
		}
	}

	@Test
	void pingFailsCleanlyWhenNothingListens() throws Exception {
		int port;
		try (ServerSocket probe = new ServerSocket(0)) {
			port = probe.getLocalPort();
		}
		int closed = port;
		assertThrows(java.io.IOException.class, () -> StatusPinger.ping("127.0.0.1", closed));
		assertThrows(java.io.IOException.class, () -> StatusPinger.parse("not json"));
		assertEquals(-1, StatusPinger.parse("{\"description\":\"x\"}").online());
	}

	@Test
	void serversSleepOnlyAfterBeingEmptyForTheWholePeriod() {
		IdleTracker tracker = new IdleTracker(10_000);
		assertFalse(tracker.observe(0, 0));
		assertFalse(tracker.observe(0, 9_999));
		assertTrue(tracker.observe(0, 10_000));

		tracker.reset();
		assertFalse(tracker.observe(0, 20_000));
		assertFalse(tracker.observe(2, 25_000), "a player joining restarts the clock");
		assertFalse(tracker.observe(0, 26_000));
		assertFalse(tracker.observe(0, 35_000));
		assertTrue(tracker.observe(0, 36_000));

		tracker.reset();
		assertFalse(tracker.observe(0, 40_000));
		assertFalse(tracker.observe(-1, 60_000), "an unknown count never counts as empty");
		assertFalse(tracker.observe(0, 61_000));
	}
}
