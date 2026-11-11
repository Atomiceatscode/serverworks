package dev.serverworks.host;

import dev.serverworks.host.ServerInstance.Failure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs an installer or build tool to completion, showing its output in the rack's console. */
final class SetupRunner {
	private SetupRunner() {
	}

	static void run(List<String> command, Path directory, long timeoutMinutes, ServerInstance.Progress progress)
			throws PrepareException {
		Process process;
		try {
			process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
			process.getOutputStream().close();
		} catch (IOException e) {
			throw new PrepareException(Failure.INSTALL, "Could not run the installer: " + e.getMessage(), e);
		}
		Thread reader = new Thread(() -> {
			try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = in.readLine()) != null) {
					if (!line.isBlank()) {
						progress.log("  " + line);
					}
				}
			} catch (IOException ignored) {
				// The stream closes when the process ends.
			}
		}, "Serverworks-Setup");
		reader.setDaemon(true);
		reader.start();

		long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(timeoutMinutes);
		try {
			while (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
				try {
					progress.checkCancelled();
				} catch (PrepareException cancelled) {
					process.descendants().forEach(ProcessHandle::destroyForcibly);
					process.destroyForcibly();
					throw cancelled;
				}
				if (System.nanoTime() > deadline) {
					process.descendants().forEach(ProcessHandle::destroyForcibly);
					process.destroyForcibly();
					throw new PrepareException(Failure.INSTALL, "The installer took longer than " + timeoutMinutes + " minutes");
				}
			}
			reader.join(5000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			throw new PrepareException(Failure.INSTALL, "Interrupted");
		}
		if (process.exitValue() != 0) {
			throw new PrepareException(Failure.INSTALL, "The installer failed (exit code " + process.exitValue()
					+ "). See the console.");
		}
	}
}
