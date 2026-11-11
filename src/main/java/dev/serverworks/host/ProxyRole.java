package dev.serverworks.host;

import java.util.List;

/** How a server relates to a proxy, decided when the proxy starts. */
public final class ProxyRole {
	private ProxyRole() {
	}

	/** This server sits behind a proxy, which only it may be joined through. */
	public record Backend(Forwarding mode, String secret) {
	}

	/** This server is the proxy, in front of these servers. The first one is where players land. */
	public record Proxy(Forwarding mode, String secret, List<Target> targets) {
		public Proxy {
			targets = List.copyOf(targets);
		}
	}

	/** @param name the proxy's name for the server, used in commands such as {@code /server} */
	public record Target(String name, int port) {
	}
}
