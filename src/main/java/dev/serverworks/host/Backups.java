package dev.serverworks.host;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Zip backups of a rack server's world, kept in its folder under {@code backups/}.
 *
 * <p>A backup holds the world being played (its folder, and the {@code _nether} and
 * {@code _the_end} folders plugin servers keep beside it). While the server runs, saving is
 * paused and the world flushed to disk first, so the copy is consistent.
 */
public final class Backups {
	public static final String FOLDER = "backups";
	/** Where the world a restore replaced is kept, in case the restore was a mistake. */
	public static final String BEFORE_RESTORE = "before-restore";
	private static final Pattern NAME = Pattern.compile("[a-z0-9-]{1,40}-\\d{8}-\\d{6}\\.zip");
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final long FLUSH_TIMEOUT_MILLIS = 30_000;
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Serverworks-Backups");
		thread.setDaemon(true);
		return thread;
	});

	/** One backup file. */
	public record Backup(String name, long size, long modified) {
	}

	private Backups() {
	}

	/** Backups of this server, newest first. */
	public static List<Backup> list(Path instanceDir) {
		List<Backup> out = new ArrayList<>();
		Path dir = instanceDir.resolve(FOLDER);
		if (!Files.isDirectory(dir)) {
			return out;
		}
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : (Iterable<Path>) files::iterator) {
				String name = file.getFileName().toString();
				if (NAME.matcher(name).matches() && Files.isRegularFile(file)) {
					out.add(new Backup(name, Files.size(file), Files.getLastModifiedTime(file).toMillis()));
				}
			}
		} catch (IOException e) {
			return out;
		}
		out.sort(Comparator.comparingLong(Backup::modified).reversed().thenComparing(Backup::name, Comparator.reverseOrder()));
		return out;
	}

	/** The world the server plays, from its {@code server.properties}; {@code world} when unknown. */
	public static String levelName(Path instanceDir) {
		Path file = instanceDir.resolve("server.properties");
		if (Files.isRegularFile(file)) {
			Properties properties = new Properties();
			try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				properties.load(reader);
				String name = properties.getProperty("level-name", "world").strip();
				if (name.matches("world(-[a-f0-9]{8})?")) {
					return name;
				}
			} catch (IOException e) {
				// Fall back to the default world.
			}
		}
		return "world";
	}

	/**
	 * Backs the server's world up in the background. A running server is asked to flush
	 * the world and pause saving while the files are copied.
	 */
	public static CompletableFuture<Backup> backUp(Path instanceDir, @Nullable ServerInstance running, int keep) {
		return CompletableFuture.supplyAsync(() -> {
			boolean paused = running != null && running.snapshot().state() == ServerInstance.State.RUNNING;
			try {
				if (paused) {
					long from = running.console().nextSequence();
					running.command("save-off");
					running.command("save-all flush");
					waitForSave(running, from);
				}
				Backup made = zip(instanceDir, levelName(instanceDir));
				prune(instanceDir, keep);
				return made;
			} catch (IOException e) {
				throw new java.io.UncheckedIOException(e);
			} finally {
				if (paused) {
					running.command("save-on");
				}
			}
		}, EXECUTOR);
	}

	private static void waitForSave(ServerInstance running, long from) {
		long deadline = System.currentTimeMillis() + FLUSH_TIMEOUT_MILLIS;
		while (System.currentTimeMillis() < deadline) {
			for (String line : running.console().since(from, 200).lines()) {
				if (line.contains("Saved the game") || line.contains("Saved the world")) {
					return;
				}
			}
			try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	/** Zips the world's folders into a new backup. */
	static Backup zip(Path instanceDir, String levelName) throws IOException {
		Path dir = instanceDir.resolve(FOLDER);
		Files.createDirectories(dir);
		String name = levelName + "-" + LocalDateTime.now().format(STAMP) + ".zip";
		Path target = dir.resolve(name);
		Path partial = dir.resolve(name + ".part");
		try (OutputStream out = Files.newOutputStream(partial); ZipOutputStream zip = new ZipOutputStream(out)) {
			for (String folder : worldFolders(levelName)) {
				Path source = instanceDir.resolve(folder);
				if (!Files.isDirectory(source)) {
					continue;
				}
				try (Stream<Path> walk = Files.walk(source)) {
					for (Path file : (Iterable<Path>) walk::iterator) {
						if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)
								|| file.getFileName().toString().equals("session.lock")) {
							continue;
						}
						String entry = instanceDir.relativize(file).toString().replace('\\', '/');
						zip.putNextEntry(new ZipEntry(entry));
						Files.copy(file, zip);
						zip.closeEntry();
					}
				}
			}
		}
		Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		return new Backup(name, Files.size(target), Files.getLastModifiedTime(target).toMillis());
	}

	/**
	 * Puts a backup's world back. The server must be stopped. The world it replaces is moved
	 * to {@code backups/before-restore/} rather than deleted.
	 */
	public static void restore(Path instanceDir, String backupName) throws IOException {
		if (!NAME.matcher(backupName).matches()) {
			throw new IOException("Not a backup name");
		}
		Path backup = instanceDir.resolve(FOLDER).resolve(backupName);
		if (!Files.isRegularFile(backup)) {
			throw new IOException("No such backup");
		}
		String levelName = backupName.substring(0, backupName.length() - "-00000000-000000.zip".length());
		if (!levelName.matches("world(-[a-f0-9]{8})?")) {
			throw new IOException("The backup is not of a rack world");
		}
		List<String> folders = worldFolders(levelName);
		Path aside = instanceDir.resolve(FOLDER).resolve(BEFORE_RESTORE);
		deleteTree(aside);
		Files.createDirectories(aside);
		for (String folder : folders) {
			Path current = instanceDir.resolve(folder);
			if (Files.exists(current)) {
				Files.move(current, aside.resolve(folder));
			}
		}
		try (InputStream in = Files.newInputStream(backup); ZipInputStream zip = new ZipInputStream(in)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				String path = entry.getName();
				String top = path.contains("/") ? path.substring(0, path.indexOf('/')) : path;
				Path target = instanceDir.resolve(path).normalize();
				// Only the world's own folders, and nothing that climbs out of the server folder.
				if (!folders.contains(top) || !target.startsWith(instanceDir.resolve(top)) || entry.isDirectory()) {
					continue;
				}
				Files.createDirectories(target.getParent());
				Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	/** Keeps the newest {@code keep} backups and deletes the rest. */
	static void prune(Path instanceDir, int keep) throws IOException {
		List<Backup> all = list(instanceDir);
		for (int i = Math.max(1, keep); i < all.size(); i++) {
			Files.deleteIfExists(instanceDir.resolve(FOLDER).resolve(all.get(i).name()));
		}
	}

	static List<String> worldFolders(String levelName) {
		return List.of(levelName, levelName + "_nether", levelName + "_the_end");
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(root)) {
			for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		}
	}
}
