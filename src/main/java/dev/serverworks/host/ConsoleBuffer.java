package dev.serverworks.host;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** The most recent lines a server printed, each with a sequence number so readers can ask for "what's new". */
public final class ConsoleBuffer {
	public static final int MAX_LINE_LENGTH = 400;

	private final int capacity;
	private final ArrayDeque<String> lines = new ArrayDeque<>();
	private long nextSequence;

	public ConsoleBuffer(int capacity) {
		this.capacity = capacity;
	}

	public synchronized void add(String line) {
		String clean = sanitize(line);
		if (lines.size() == capacity) {
			lines.removeFirst();
		}
		lines.addLast(clean);
		nextSequence++;
	}

	/** The sequence number the next line will get. */
	public synchronized long nextSequence() {
		return nextSequence;
	}

	/** Lines numbered {@code fromSequence} and later, oldest first, at most {@code limit}. */
	public synchronized Page since(long fromSequence, int limit) {
		long oldest = nextSequence - lines.size();
		long start = Math.max(fromSequence, oldest);
		List<String> out = new ArrayList<>();
		long index = oldest;
		for (String line : lines) {
			if (index >= start && out.size() < limit) {
				out.add(line);
			}
			index++;
		}
		return new Page(start, out);
	}

	public synchronized void clear() {
		lines.clear();
	}

	/** Strips colour escape codes and control characters, and clips very long lines. */
	static String sanitize(String line) {
		String stripped = line.replaceAll("\u001B\\[[;\\d?]*[ -/]*[@-~]", "");
		StringBuilder out = new StringBuilder(Math.min(stripped.length(), MAX_LINE_LENGTH));
		for (int i = 0; i < stripped.length() && out.length() < MAX_LINE_LENGTH; i++) {
			char c = stripped.charAt(i);
			if (c == '\t') {
				out.append("  ");
			} else if (c >= ' ' && c != 0x7F && c != '§') {
				out.append(c);
			}
		}
		return out.toString();
	}

	/** @param firstSequence the sequence number of the first line in {@code lines} */
	public record Page(long firstSequence, List<String> lines) {
		public long nextSequence() {
			return firstSequence + lines.size();
		}
	}
}
