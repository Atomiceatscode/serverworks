package dev.serverworks.catalog.model;

import java.util.Locale;

/** What kind of thing a catalog project is, which decides where its files go on a server. */
public enum ProjectType {
	MOD("mod"),
	PLUGIN("plugin"),
	DATAPACK("datapack"),
	MODPACK("modpack"),
	RESOURCEPACK("resourcepack"),
	SHADER("shader"),
	OTHER("project");

	private final String apiName;

	ProjectType(String apiName) {
		this.apiName = apiName;
	}

	/** The value used in Modrinth facets and responses. */
	public String apiName() {
		return apiName;
	}

	public static ProjectType fromApi(String value) {
		if (value == null) {
			return OTHER;
		}
		String v = value.toLowerCase(Locale.ROOT);
		for (ProjectType type : values()) {
			if (type.apiName.equals(v)) {
				return type;
			}
		}
		return OTHER;
	}
}
