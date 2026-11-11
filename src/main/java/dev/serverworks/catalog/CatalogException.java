package dev.serverworks.catalog;

/** A catalog request that did not produce a usable answer. */
public final class CatalogException extends RuntimeException {
	private final Kind kind;

	public CatalogException(Kind kind, String message) {
		super(message);
		this.kind = kind;
	}

	public CatalogException(Kind kind, String message, Throwable cause) {
		super(message, cause);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	public enum Kind {
		/** The project or version does not exist. */
		NOT_FOUND,
		/** The catalog asked us to slow down and retries ran out. */
		RATE_LIMITED,
		/** Could not reach the catalog at all. */
		NETWORK,
		/** The catalog answered with something we could not read, or with a server error. */
		BAD_RESPONSE,
		/** The request would have gone somewhere other than the catalog. */
		BLOCKED
	}
}
