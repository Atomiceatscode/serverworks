package dev.serverworks.relay;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.flow.FlowControlHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.Varint21FrameDecoder;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RackRelayTest {
	@Test
	void ticketsComeOffTheHostName() {
		String token = RackRelay.issue(25600);
		String host = "play.example.com" + RackRelay.MARKER + token;
		assertEquals(token, RackRelay.ticketIn(host));
		assertEquals("play.example.com", RackRelay.withoutTicket(host));
		assertNull(RackRelay.ticketIn("play.example.com"));
		assertNull(RackRelay.ticketIn("play.example.com" + RackRelay.MARKER + "not-a-ticket"));
		// Forge and Fabric markers in the host name are not tickets.
		assertNull(RackRelay.ticketIn("play.example.com\0FML3\0"));
	}

	/**
	 * A stand-in for the main server's pipeline at the moment the relay takes over: the
	 * real frame splitter, flow control, then the relay. Packets the player sends after the
	 * handshake must reach the rack whole, and the rack's answers must come back untouched.
	 */
	@Test
	void passesPacketsThroughWithTheirLengths() throws Exception {
		try (ServerSocket rack = new ServerSocket(0)) {
			CompletableFuture<byte[][]> rackReceived = CompletableFuture.supplyAsync(() -> {
				try (Socket socket = rack.accept()) {
					socket.setSoTimeout(5000);
					DataInputStream in = new DataInputStream(socket.getInputStream());
					byte[] handshake = readFrame(in);
					byte[] login = readFrame(in);
					OutputStream out = socket.getOutputStream();
					out.write(new byte[] {3, 0x02, 0x7F, 0x01});
					out.flush();
					return new byte[][] {handshake, login};
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});

			MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
			try {
				Channel main = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
						.childHandler(new ChannelInitializer<>() {
							@Override
							protected void initChannel(Channel channel) {
								channel.pipeline().addLast("splitter", new Varint21FrameDecoder(null))
										.addLast(new FlowControlHandler())
										.addLast("relay", new RelayHandler(rack.getLocalPort(), 775, "play.example.com"));
							}
						}).bind("127.0.0.1", 0).sync().channel();
				int mainPort = ((InetSocketAddress) main.localAddress()).getPort();
				try (Socket player = new Socket("127.0.0.1", mainPort)) {
					player.setSoTimeout(5000);
					// A login-start-like packet: id 0x00 and a name, split across two writes.
					byte[] loginPacket = {0x00, 0x07, 'P', 'l', 'a', 'y', 'e', 'r', '0'};
					OutputStream out = player.getOutputStream();
					out.write(new byte[] {(byte) loginPacket.length, loginPacket[0], loginPacket[1]});
					out.flush();
					Thread.sleep(50);
					out.write(Arrays.copyOfRange(loginPacket, 2, loginPacket.length));
					out.flush();

					byte[][] received = rackReceived.get(10, TimeUnit.SECONDS);
					FriendlyByteBuf handshake = new FriendlyByteBuf(Unpooled.wrappedBuffer(received[0]));
					assertEquals(0x00, handshake.readVarInt());
					assertEquals(775, handshake.readVarInt());
					assertEquals("play.example.com", handshake.readUtf(255));
					assertEquals(rack.getLocalPort(), handshake.readUnsignedShort());
					assertEquals(2, handshake.readVarInt(), "the rack sees a plain login");
					assertArrayEquals(loginPacket, received[1], "packets after the handshake arrive whole");

					byte[] answer = player.getInputStream().readNBytes(4);
					assertArrayEquals(new byte[] {3, 0x02, 0x7F, 0x01}, answer, "the rack's bytes reach the player as they are");
				}
				main.close().sync();
			} finally {
				group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
			}
		}
	}

	private static byte[] readFrame(DataInputStream in) throws Exception {
		int length = 0;
		for (int shift = 0; ; shift += 7) {
			int b = in.readUnsignedByte();
			length |= (b & 0x7F) << shift;
			if ((b & 0x80) == 0) {
				break;
			}
		}
		return in.readNBytes(length);
	}
}
