package dev.serverworks.host;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What it takes to run one platform at one Minecraft version.
 *
 * @param serverJar     the file to download: the server jar itself, an installer, or a build tool
 * @param javaVersion   the lowest Java major version that can run it
 * @param build         a human-readable build label; also identifies the installed build
 * @param loaderVersion the mod loader's version on mod platforms, for client packs; null otherwise
 * @param setup         what to do with the download before the server can launch
 * @param setupArgs     arguments for the installer or build tool
 * @param builtJar      for {@link Setup#BUILD_JAR}: the jar the tool produces, relative to its work folder
 * @param readyMarker   text in a console line that means the server has finished starting
 */
public record InstallPlan(Artifact serverJar, int javaVersion, String build, @Nullable String loaderVersion,
		Setup setup, List<String> setupArgs, @Nullable String builtJar, String readyMarker) {

	/** The marker vanilla-derived servers print: {@code Done (1.234s)! For help, type "help"}. */
	public static final String VANILLA_READY = "For help, type";

	public InstallPlan {
		setupArgs = List.copyOf(setupArgs);
	}

	/** A plan for a platform that ships a ready-to-run server jar. */
	public InstallPlan(Artifact serverJar, int javaVersion, String build, @Nullable String loaderVersion) {
		this(serverJar, javaVersion, build, loaderVersion, Setup.NONE, List.of(), null, VANILLA_READY);
	}

	public InstallPlan withReadyMarker(String marker) {
		return new InstallPlan(serverJar, javaVersion, build, loaderVersion, setup, setupArgs, builtJar, marker);
	}

	public enum Setup {
		/** The download is the server jar. */
		NONE,
		/** The download is an installer, run once per build inside the server's own folder. */
		INSTALL_IN_INSTANCE,
		/** The download is a build tool that compiles the server jar once, into the shared cache. */
		BUILD_JAR
	}

	/**
	 * @param fileName where it is cached; unique per build so builds never collide
	 * @param checksum null only when the source publishes none
	 */
	public record Artifact(String url, String fileName, @Nullable Checksum checksum, long size) {
	}
}
