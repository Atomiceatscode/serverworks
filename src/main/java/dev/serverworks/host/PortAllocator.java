package dev.serverworks.host;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;

/** Hands out ports from a fixed range, skipping ones already promised or in use on this machine. */
public final class PortAllocator {
	private final int first;
	private final int last;
	private final Set<Integer> reserved = new HashSet<>();

	public PortAllocator(int first, int last) {
		if (first < 1024 || last > 65535 || first > last) {
			throw new IllegalArgumentException("Bad port range " + first + "-" + last);
		}
		this.first = first;
		this.last = last;
	}

	/** Reserves a free port, or returns -1 when the whole range is taken. */
	public synchronized int acquire() {
		for (int port = first; port <= last; port++) {
			if (!reserved.contains(port) && isFree(port)) {
				reserved.add(port);
				return port;
			}
		}
		return -1;
	}

	public synchronized void release(int port) {
		reserved.remove(port);
	}

	private static boolean isFree(int port) {
		try (ServerSocket socket = new ServerSocket(port, 1, (InetAddress) null)) {
			socket.setReuseAddress(true);
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
