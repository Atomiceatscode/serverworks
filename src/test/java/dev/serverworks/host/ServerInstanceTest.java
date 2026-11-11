package dev.serverworks.host;

import dev.serverworks.host.ServerInstance.Failure;
import dev.serverworks.host.ServerInstance.Prepared;
import dev.serverworks.host.ServerInstance.State;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ServerInstanceTest {
	@TempDir
	Path dir;
	private final ExecutorService executor = Executors.newCachedThreadPool();
	private final List<ServerInstance> started = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		started.forEach(ServerInstance::killNow);
		executor.shutdownNow();
	}

	private ServerInstance instance() {
		ServerInstance instance = new ServerInstance(UUID.randomUUID(), executor);
		started.add(instance);
		return instance;
	}

	private Prepared fake(ServerInstance instance, String mode) {
		String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		return new Prepared(dir, List.of(java, "-D" + ServerInstance.MARKER_PROPERTY + "=" + instance.rackId(), "-cp",
				System.getProperty("java.class.path"), FakeServer.class.getName(), mode));
	}

	private static void await(ServerInstance instance, State wanted) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (System.nanoTime() < deadline) {
			State now = instance.snapshot().state();
			if (now == wanted) {
				return;
			}
			if (now != wanted && now.canStart() && now != State.IDLE && !wanted.active()) {
				break;
			}
			Thread.sleep(20);
		}
		fail("Expected " + wanted + " but instance is " + instance.snapshot() + "\n"
				+ String.join("\n", instance.console().since(0, 100).lines()));
	}

	@Test
	void startsRunsCommandsAndStopsCleanly() throws Exception {
		ServerInstance instance = instance();
		assertTrue(instance.start(25610, progress -> {
			progress.state(State.DOWNLOADING, 40);
			progress.log("preparing");
			return fake(instance, "normal");
		}));
		assertFalse(instance.start(25610, progress -> fake(instance, "normal")), "a second start must be refused");

		await(instance, State.RUNNING);
		assertEquals(25610, instance.snapshot().port());
		assertTrue(Files.exists(dir.resolve(ServerInstance.PID_FILE)));

		assertTrue(instance.command("say hello\nstop"));
		long deadline = System.currentTimeMillis() + 10_000;
		while (System.currentTimeMillis() < deadline
				&& instance.console().since(0, 100).lines().stream().noneMatch(l -> l.contains("echo say hello stop"))) {
			Thread.sleep(20);
		}
		List<String> lines = instance.console().since(0, 100).lines();
		assertTrue(lines.contains("[Serverworks] preparing"), lines.toString());
		assertTrue(lines.stream().anyMatch(l -> l.contains("echo say hello stop")), "newlines must not smuggle a second command: " + lines);
		assertTrue(lines.stream().noneMatch(l -> l.contains("\u001B")), "colour codes must be stripped");

		instance.stop();
		await(instance, State.STOPPED);
		assertEquals(Failure.NONE, instance.snapshot().failure());
		assertFalse(Files.exists(dir.resolve(ServerInstance.PID_FILE)));
		assertFalse(instance.command("list"), "commands are refused once stopped");

		assertTrue(instance.start(25610, progress -> fake(instance, "normal")), "it can be started again");
		await(instance, State.RUNNING);
	}

	@Test
	void unexpectedExitWhileRunningIsACrash() throws Exception {
		ServerInstance instance = instance();
		instance.start(25611, progress -> fake(instance, "normal"));
		await(instance, State.RUNNING);
		instance.command("crash");
		await(instance, State.CRASHED);
		assertEquals(Failure.EXITED, instance.snapshot().failure());
		assertTrue(instance.snapshot().detail().contains("3"), instance.snapshot().detail());
	}

	@Test
	void exitBeforeReadyIsAFailure() throws Exception {
		ServerInstance instance = instance();
		instance.start(25612, progress -> fake(instance, "dies"));
		await(instance, State.FAILED);
		assertEquals(Failure.EXITED, instance.snapshot().failure());
	}

	@Test
	void eulaRefusalIsRecognised() throws Exception {
		ServerInstance instance = instance();
		instance.start(25613, progress -> fake(instance, "eula"));
		await(instance, State.FAILED);
		assertEquals(Failure.EULA, instance.snapshot().failure());
	}

	@Test
	void preparationFailureIsReported() throws Exception {
		ServerInstance instance = instance();
		instance.start(25614, progress -> {
			throw new PrepareException(Failure.DOWNLOAD, "no network");
		});
		await(instance, State.FAILED);
		assertEquals(Failure.DOWNLOAD, instance.snapshot().failure());
		assertEquals("no network", instance.snapshot().detail());
	}

	@Test
	void stoppingDuringPreparationNeverLaunches() throws Exception {
		ServerInstance instance = instance();
		CountDownLatch preparing = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		instance.start(25615, progress -> {
			preparing.countDown();
			try {
				release.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			progress.checkCancelled();
			return fake(instance, "normal");
		});
		assertTrue(preparing.await(10, TimeUnit.SECONDS));
		instance.stop();
		assertEquals(State.STOPPING, instance.snapshot().state());
		release.countDown();
		await(instance, State.STOPPED);
		assertFalse(Files.exists(dir.resolve(ServerInstance.PID_FILE)), "the process must never have started");
	}

	@Test
	void versionChangesWheneverSomethingVisibleDoes() throws Exception {
		ServerInstance instance = instance();
		int before = instance.version();
		instance.start(25616, progress -> fake(instance, "normal"));
		await(instance, State.RUNNING);
		assertTrue(instance.version() > before);
	}
}
