package dev.serverworks.relay;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Sits in a player's connection to the main server in place of the packet decoder, and
 * passes everything to and from a rack's server on this computer.
 *
 * <p>It receives whole packets from the frame splitter, so it puts each one's length back in
 * front before sending it on. What the rack's server sends back is written to the player
 * as it is.
 */
final class RelayHandler extends ChannelInboundHandlerAdapter {
	private final int port;
	private final int protocolVersion;
	private final String hostName;
	private final Deque<ByteBuf> waiting = new ArrayDeque<>();
	private Channel backend;
	private boolean connected;

	RelayHandler(int port, int protocolVersion, String hostName) {
		this.port = port;
		this.protocolVersion = protocolVersion;
		this.hostName = hostName;
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) {
		Channel player = ctx.channel();
		player.config().setAutoRead(false);
		backend = new Bootstrap()
				.group(player.eventLoop())
				.channel(socketChannelClass(player))
				.option(ChannelOption.TCP_NODELAY, true)
				.handler(new ChannelInitializer<>() {
					@Override
					protected void initChannel(Channel channel) {
						channel.pipeline().addLast(new Backend(ctx));
					}
				})
				.connect("127.0.0.1", port)
				.addListener((ChannelFutureListener) future -> {
					if (!future.isSuccess()) {
						player.close();
						return;
					}
					connected = true;
					future.channel().write(frame(handshake()));
					while (!waiting.isEmpty()) {
						future.channel().write(waiting.poll());
					}
					future.channel().flush();
					player.config().setAutoRead(true);
					player.read();
				})
				.channel();
	}

	@SuppressWarnings("unchecked")
	private static Class<? extends Channel> socketChannelClass(Channel player) {
		// The accepted player connection is the same kind of socket a client connection uses
		// (NIO, epoll or kqueue), so a connection of that class works on this event loop.
		return (Class<? extends Channel>) player.getClass();
	}

	/** A handshake for the rack's server: the player's own, with the rack's port and a plain login. */
	private ByteBuf handshake() {
		FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
		out.writeVarInt(0x00);
		out.writeVarInt(protocolVersion);
		out.writeUtf(hostName, 255);
		out.writeShort(port);
		out.writeVarInt(2);
		return out;
	}

	/** Puts a packet's length back in front of it, as the frame splitter took it off. */
	private static ByteBuf frame(ByteBuf packet) {
		FriendlyByteBuf framed = new FriendlyByteBuf(Unpooled.buffer(packet.readableBytes() + 5));
		framed.writeVarInt(packet.readableBytes());
		framed.writeBytes(packet);
		packet.release();
		return framed;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) {
		if (!(msg instanceof ByteBuf packet)) {
			ReferenceCountUtil.release(msg);
			return;
		}
		ByteBuf framed = frame(packet);
		if (connected) {
			backend.writeAndFlush(framed);
			if (!backend.isWritable()) {
				ctx.channel().config().setAutoRead(false);
			}
		} else {
			waiting.add(framed);
		}
	}

	@Override
	public void channelWritabilityChanged(ChannelHandlerContext ctx) {
		// The player is reading slowly: stop reading from the rack's server until they catch up.
		if (backend != null) {
			backend.config().setAutoRead(ctx.channel().isWritable());
		}
		ctx.fireChannelWritabilityChanged();
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		close();
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) {
		close();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		ctx.close();
		close();
	}

	private void close() {
		while (!waiting.isEmpty()) {
			waiting.poll().release();
		}
		if (backend != null) {
			backend.close();
		}
	}

	/** The connection to the rack's server: everything it sends goes to the player. */
	private static final class Backend extends ChannelInboundHandlerAdapter {
		private final ChannelHandlerContext player;

		Backend(ChannelHandlerContext player) {
			this.player = player;
		}

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) {
			// Written from the relay's place in the pipeline, past where the encoders were.
			player.writeAndFlush(msg);
			if (!player.channel().isWritable()) {
				ctx.channel().config().setAutoRead(false);
			}
		}

		@Override
		public void channelWritabilityChanged(ChannelHandlerContext ctx) {
			player.channel().config().setAutoRead(ctx.channel().isWritable());
			ctx.fireChannelWritabilityChanged();
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) {
			player.channel().close();
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
			ctx.close();
			player.channel().close();
		}
	}
}
