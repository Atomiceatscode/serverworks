package dev.serverworks.host;

/** Getting a server ready to launch failed, with a category the game can explain to the player. */
public final class PrepareException extends Exception {
	private final ServerInstance.Failure failure;

	public PrepareException(ServerInstance.Failure failure, String detail) {
		super(detail);
		this.failure = failure;
	}

	public PrepareException(ServerInstance.Failure failure, String detail, Throwable cause) {
		super(detail, cause);
		this.failure = failure;
	}

	public ServerInstance.Failure failure() {
		return failure;
	}
}
