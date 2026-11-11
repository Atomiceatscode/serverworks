package dev.serverworks.host;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One child server and its life: prepared, launched, watched, and stopped.
 *
 * <p>All methods are safe to call from any thread and return at once; the slow work
 * happens on the executor given at construction. Read {@link #snapshot()} to see where
 * things stand; its version number changes whenever anything visible does.
 */
public final class ServerInstance {
	/** A system property on the child's command line that identifies it as belonging to a rack. */
	public static final String MARKER_PROPERTY = "serverworks.rack";
	static final String PID_FILE = "serverworks.pid";
	private static final long STOP_GRACE_SECONDS = 30;

	private final UUID rackId;
	private final Executor executor;
	private final ConsoleBuffer console = new ConsoleBuffer(500);
	private final AtomicInteger version = new AtomicInteger();

	private State state = State.IDLE;
	private int progress;
	private Failure failure = Failure.NONE;
	private String detail = "";
	private int port;
	private Process process;
	private OutputStream stdin;
	private boolean cancelRequested;
	private boolean sawEulaComplaint;
	private int playersOnline = -1;
	private int playersMax = -1;
	private boolean hibernating;
	private String readyMarker = InstallPlan.VANILLA_READY;
	private boolean hibernated;

	/** The running server's folder, once it has been launched. */
	private Path directory;

	public ServerInstance(UUID rackId, Executor executor) {
		this.rackId = rackId;
		this.executor = executor;
	}

	public enum State {
		IDLE, RESOLVING, DOWNLOADING, INSTALLING, STARTING, RUNNING, STOPPING, STOPPED, CRASHED, FAILED;

		/** True while a start can be requested. */
		public boolean canStart() {
			return this == IDLE || this == STOPPED || this == CRASHED || this == FAILED;
		}

		/** True from the moment a start is accepted until the server is fully gone. */
		public boolean active() {
			return !canStart();
		}

		public String translationKey() {
			return "serverworks.instance.state." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** Why the last run did not work, in terms the game can explain. */
	public enum Failure {
		NONE, EULA, DOWNLOAD, INSTALL, LAUNCH, CONTENT, UNSUPPORTED, EXITED;

		public String translationKey() {
			return "serverworks.instance.failure." + name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * @param detail        extra words for a failure, in English, for the console and tooltips
	 * @param playersOnline -1 until the running server has answered a status request
	 * @param hibernated    true when the server was stopped because nobody was on it
	 */
	public record Snapshot(State state, int progress, Failure failure, String detail, int port, int version,
			int playersOnline, int playersMax, boolean hibernated) {
		public static final Snapshot IDLE = new Snapshot(State.IDLE, 0, Failure.NONE, "", 0, 0, -1, -1, false);
	}

	/** The slow part before launch, supplied by the caller. */
	public interface Preparation {
		Prepared prepare(Progress progress) throws PrepareException;
	}

	/** @param readyMarker text in a console line that means the server has finished starting */
	public record Prepared(Path directory, List<String> command, String readyMarker) {
		public Prepared(Path directory, List<String> command) {
			this(directory, command, InstallPlan.VANILLA_READY);
		}
	}

	/** How a {@link Preparation} reports back while it works. */
	public interface Progress {
		void state(State state, int percent);

		void log(String line);

		/** Throws when the player asked to stop, so preparation ends promptly. */
		void checkCancelled() throws PrepareException;
	}

	public UUID rackId() {
		return rackId;
	}

	public ConsoleBuffer console() {
		return console;
	}

	public synchronized Snapshot snapshot() {
		return new Snapshot(state, progress, failure, detail, port, version.get(), playersOnline, playersMax, hibernated);
	}

	public int version() {
		return version.get();
	}

	/**
	 * Begins starting the server. Returns false, and does nothing, when it is already
	 * active.
	 */
	public synchronized boolean start(int port, Preparation preparation) {
		if (!state.canStart()) {
			return false;
		}
		this.port = port;
		this.failure = Failure.NONE;
		this.detail = "";
		this.cancelRequested = false;
		this.sawEulaComplaint = false;
		this.hibernating = false;
		this.hibernated = false;
		this.playersOnline = -1;
		this.playersMax = -1;
		set(State.RESOLVING, 0);
		executor.execute(() -> run(preparation));
		return true;
	}

	private void run(Preparation preparation) {
		Progress sink = new Progress() {
			@Override
			public void state(State next, int percent) {
				synchronized (ServerInstance.this) {
					if (state.active() && state != State.STOPPING) {
						set(next, percent);
					}
				}
			}

			@Override
			public void log(String line) {
				console.add("[Serverworks] " + line);
				version.incrementAndGet();
			}

			@Override
			public void checkCancelled() throws PrepareException {
				synchronized (ServerInstance.this) {
					if (cancelRequested) {
						throw new PrepareException(Failure.NONE, "Stopped before launch");
					}
				}
			}
		};
		try {
			Prepared prepared = preparation.prepare(sink);
			sink.checkCancelled();
			launch(prepared);
		} catch (PrepareException e) {
			synchronized (this) {
				if (e.failure() == Failure.NONE) {
					set(State.STOPPED, 0);
				} else {
					fail(e.failure(), e.getMessage());
				}
			}
		} catch (RuntimeException e) {
			synchronized (this) {
				fail(Failure.INSTALL, "Unexpected error: " + e);
			}
		}
	}

	private void launch(Prepared prepared) throws PrepareException {
		reapOrphan(prepared.directory());
		Process started;
		try {
			started = new ProcessBuilder(prepared.command())
					.directory(prepared.directory().toFile())
					.redirectErrorStream(true)
					.start();
			Files.writeString(prepared.directory().resolve(PID_FILE), Long.toString(started.pid()), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new PrepareException(Failure.LAUNCH, "Could not start Java: " + e.getMessage(), e);
		}
		synchronized (this) {
			process = started;
			stdin = started.getOutputStream();
			readyMarker = prepared.readyMarker();
			directory = prepared.directory();
			if (cancelRequested) {
				requestStopLocked();
			} else {
				set(State.STARTING, 0);
			}
		}
		Thread reader = new Thread(() -> pump(started, prepared.directory()), "Serverworks-Console-" + rackId);
		reader.setDaemon(true);
		reader.start();
	}

	/** Reads the server's output until it exits, then records how it ended. */
	private void pump(Process running, Path directory) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(running.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				console.add(line);
				version.incrementAndGet();
				onLine(line);
			}
		} catch (IOException ignored) {
			// The stream closes when the process dies; the exit code below says the rest.
		}
		int exit;
		try {
			exit = running.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			exit = -1;
		}
		try {
			Files.deleteIfExists(directory.resolve(PID_FILE));
		} catch (IOException ignored) {
			// Stale pid files are checked before being trusted.
		}
		synchronized (this) {
			process = null;
			stdin = null;
			playersOnline = -1;
			playersMax = -1;
			if (state == State.STOPPING) {
				hibernated = hibernating;
				hibernating = false;
				set(State.STOPPED, 0);
			} else if (sawEulaComplaint) {
				fail(Failure.EULA, "The server needs the Minecraft EULA to be accepted");
			} else if (state == State.RUNNING) {
				failure = Failure.EXITED;
				detail = "The server stopped unexpectedly (exit code " + exit + ")";
				set(State.CRASHED, 0);
			} else {
				fail(Failure.EXITED, "The server exited while starting (exit code " + exit + ")");
			}
		}
	}

	private void onLine(String line) {
		synchronized (this) {
			if (state == State.STARTING && line.contains(readyMarker)) {
				set(State.RUNNING, 100);
			} else if (line.contains("agree to the EULA")) {
				sawEulaComplaint = true;
			}
		}
	}

	/** Asks the server to stop cleanly, and forces it if it takes too long. Safe in any state. */
	public synchronized void stop() {
		if (state.canStart() || state == State.STOPPING) {
			return;
		}
		cancelRequested = true;
		if (process != null) {
			requestStopLocked();
		} else {
			// Still preparing: show "stopping" now; preparation notices at its next checkpoint.
			set(State.STOPPING, 0);
		}
	}

	private void requestStopLocked() {
		Process running = process;
		set(State.STOPPING, 0);
		write("stop");
		Thread watchdog = new Thread(() -> {
			try {
				if (!running.waitFor(STOP_GRACE_SECONDS, TimeUnit.SECONDS)) {
					running.destroyForcibly();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				running.destroyForcibly();
			}
		}, "Serverworks-Stop-" + rackId);
		watchdog.setDaemon(true);
		watchdog.start();
	}

	/** Stops a server because it has been empty for a while. It is reported as sleeping, not stopped. */
	public synchronized void hibernate() {
		if (state != State.RUNNING) {
			return;
		}
		console.add("[Serverworks] Nobody has been on for a while, so the server is going to sleep");
		hibernating = true;
		stop();
	}

	/** Records the latest player count from a status request. */
	public synchronized void reportPlayers(int online, int max) {
		if (state == State.RUNNING && (online != playersOnline || max != playersMax)) {
			playersOnline = online;
			playersMax = max;
			version.incrementAndGet();
		}
	}

	/**
	 * Lets a player in: writes them onto the whitelist with the id this server will give them,
	 * then tells the server to reload it. Returns false when the server is not running.
	 */
	public synchronized boolean admit(String name, UUID accountId) {
		if (state != State.RUNNING || directory == null) {
			return false;
		}
		try {
			Whitelist.admit(directory, name, accountId);
		} catch (java.io.IOException e) {
			console.add("Could not update whitelist.json: " + e.getMessage());
			version.incrementAndGet();
			return false;
		}
		return command("whitelist reload");
	}

	/** Sends a console command. Returns false when the server is not running. */
	public synchronized boolean command(String command) {
		if (state != State.RUNNING) {
			return false;
		}
		String clean = command.replace('\n', ' ').replace('\r', ' ').strip();
		if (clean.isEmpty()) {
			return false;
		}
		console.add("> " + clean);
		version.incrementAndGet();
		return write(clean);
	}

	private boolean write(String line) {
		if (stdin == null) {
			return false;
		}
		try {
			stdin.write((line + "\n").getBytes(StandardCharsets.UTF_8));
			stdin.flush();
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	/** Ends the process immediately. For game shutdown, when there is no time to wait. */
	public void killNow() {
		Process running;
		synchronized (this) {
			running = process;
			cancelRequested = true;
			if (running != null && state != State.STOPPING) {
				set(State.STOPPING, 0);
				write("stop");
			}
		}
		if (running == null) {
			return;
		}
		try {
			if (!running.waitFor(8, TimeUnit.SECONDS)) {
				running.destroyForcibly();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			running.destroyForcibly();
		}
	}

	/**
	 * If a previous game session died and left this rack's server running, end it. The
	 * process is only touched when its command line carries this rack's marker, so a
	 * reused process id can never cause something unrelated to be killed.
	 */
	private void reapOrphan(Path directory) {
		Path pidFile = directory.resolve(PID_FILE);
		if (!Files.exists(pidFile)) {
			return;
		}
		try {
			long pid = Long.parseLong(Files.readString(pidFile, StandardCharsets.UTF_8).strip());
			Optional<ProcessHandle> handle = ProcessHandle.of(pid).filter(ProcessHandle::isAlive);
			if (handle.isPresent() && isOurs(handle.get())) {
				console.add("[Serverworks] Stopping a leftover server from an earlier session");
				handle.get().destroyForcibly();
				handle.get().onExit().get(10, TimeUnit.SECONDS);
			}
			Files.deleteIfExists(pidFile);
		} catch (Exception ignored) {
			// If the old process cannot be identified it is left alone; a busy port will say so.
		}
	}

	private boolean isOurs(ProcessHandle handle) {
		String marker = "-D" + MARKER_PROPERTY + "=" + rackId;
		Optional<String[]> arguments = handle.info().arguments();
		if (arguments.isPresent()) {
			for (String argument : arguments.get()) {
				if (marker.equals(argument)) {
					return true;
				}
			}
			return false;
		}
		return handle.info().commandLine().map(line -> line.contains(marker)).orElse(false);
	}

	private void fail(Failure why, String message) {
		failure = why;
		detail = message == null ? "" : message;
		console.add("[Serverworks] " + detail);
		set(State.FAILED, 0);
	}

	private void set(State next, int percent) {
		state = next;
		progress = percent;
		version.incrementAndGet();
	}
}
