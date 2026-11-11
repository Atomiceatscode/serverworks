package dev.serverworks;

/** Client-only actions that common code may trigger. The client fills these in at start-up. */
public final class ClientHooks {
	/** Opens the Operator's Manual. Does nothing on a dedicated server. */
	public static Runnable openManual = () -> { };

	private ClientHooks() {
	}
}
