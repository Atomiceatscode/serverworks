package dev.serverworks.host;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Works out how to launch a server an installer has laid out. Installers change their
 * output between versions, so this looks at what is actually there rather than assuming.
 */
final class Launchers {
	private Launchers() {
	}

	/** Java arguments, after the memory settings, that start the server in {@code dir}. */
	static List<String> discover(Path dir) throws IOException {
		boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
		String argsFile = windows ? "win_args.txt" : "unix_args.txt";
		Optional<Path> args = find(dir.resolve("libraries"), argsFile);
		if (args.isPresent()) {
			// NeoForge and modern Forge: the JVM reads its class path and main class from argument files.
			List<String> out = new ArrayList<>();
			if (Files.exists(dir.resolve("user_jvm_args.txt"))) {
				out.add("@user_jvm_args.txt");
			}
			out.add("@" + dir.relativize(args.get()).toString().replace('\\', '/'));
			out.add("nogui");
			return out;
		}
		for (String launcher : List.of("quilt-server-launch.jar", "fabric-server-launch.jar")) {
			if (Files.exists(dir.resolve(launcher))) {
				return List.of("-jar", launcher, "nogui");
			}
		}
		try (Stream<Path> files = Files.list(dir)) {
			Optional<Path> shim = files.filter(p -> p.getFileName().toString().endsWith("-shim.jar")).findFirst();
			if (shim.isPresent()) {
				return List.of("-jar", shim.get().getFileName().toString(), "nogui");
			}
		}
		if (Files.exists(dir.resolve(Deployment.SERVER_JAR))) {
			return List.of("-jar", Deployment.SERVER_JAR, "nogui");
		}
		throw new IOException("The installer finished, but no way to start the server was found");
	}

	private static Optional<Path> find(Path root, String name) throws IOException {
		if (!Files.isDirectory(root)) {
			return Optional.empty();
		}
		try (Stream<Path> files = Files.walk(root, 8)) {
			return files.filter(p -> p.getFileName().toString().equals(name)).findFirst();
		}
	}
}
