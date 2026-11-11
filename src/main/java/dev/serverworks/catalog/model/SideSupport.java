package dev.serverworks.catalog.model;

import java.util.Locale;

/** Whether a project runs on (or is needed by) one side of the connection. */
public enum SideSupport {
	REQUIRED,
	OPTIONAL,
	UNSUPPORTED,
	UNKNOWN;

	public static SideSupport fromApi(String value) {
		if (value == null) {
			return UNKNOWN;
		}
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "required" -> REQUIRED;
			case "optional" -> OPTIONAL;
			case "unsupported" -> UNSUPPORTED;
			default -> UNKNOWN;
		};
	}
}
