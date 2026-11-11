package dev.serverworks.host;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small, careful edits to existing TOML and YAML config files: change the few values the
 * rack controls and leave every other line, comment and setting exactly as it was.
 */
public final class ConfigEdits {
	private static final Pattern TOML_TABLE = Pattern.compile("^\\s*\\[([^\\[\\]]+)]\\s*(#.*)?$");
	private static final Pattern TOML_KEY = Pattern.compile("^\\s*([A-Za-z0-9_\"-]+)\\s*=");
	private static final Pattern YAML_KEY = Pattern.compile("^(\\s*)([A-Za-z0-9_.-]+):(\\s*)(.*)$");

	private ConfigEdits() {
	}

	/** Sets a key that sits before the first table header, adding it there if missing. */
	public static List<String> tomlSetTopLevel(List<String> lines, String key, String value) {
		List<String> out = new ArrayList<>(lines);
		int firstTable = out.size();
		for (int i = 0; i < out.size(); i++) {
			if (TOML_TABLE.matcher(out.get(i)).matches()) {
				firstTable = i;
				break;
			}
			Matcher m = TOML_KEY.matcher(out.get(i));
			if (m.find() && m.group(1).equals(key)) {
				out.set(i, key + " = " + value);
				return out;
			}
		}
		out.add(firstTable, key + " = " + value);
		return out;
	}

	/** Replaces the whole body of a table (everything up to the next header), creating it if missing. */
	public static List<String> tomlReplaceTable(List<String> lines, String table, List<String> body) {
		List<String> out = new ArrayList<>();
		boolean found = false;
		boolean skipping = false;
		for (String line : lines) {
			Matcher header = TOML_TABLE.matcher(line);
			if (header.matches()) {
				skipping = false;
				if (header.group(1).strip().equals(table)) {
					found = true;
					skipping = true;
					out.add(line);
					out.addAll(body);
					continue;
				}
			}
			if (!skipping) {
				out.add(line);
			}
		}
		if (!found) {
			out.add("[" + table + "]");
			out.addAll(body);
		}
		return out;
	}

	/**
	 * Sets a scalar at a path of nested YAML keys (block style, two or more spaces of
	 * indentation), adding any missing keys under the deepest one that exists.
	 */
	public static List<String> yamlSet(List<String> lines, List<String> path, String value) {
		List<String> out = new ArrayList<>(lines);
		List<String> stack = new ArrayList<>();
		List<Integer> indents = new ArrayList<>();
		int deepestMatch = 0;
		int insertAfter = -1;
		int insertIndent = 0;
		for (int i = 0; i < out.size(); i++) {
			String line = out.get(i);
			if (line.isBlank() || line.stripLeading().startsWith("#") || line.stripLeading().startsWith("- ")) {
				if (deepestMatch > 0 && insertAfter >= 0 && !line.isBlank()) {
					insertAfter = i;
				}
				continue;
			}
			Matcher m = YAML_KEY.matcher(line);
			if (!m.matches()) {
				continue;
			}
			int indent = m.group(1).length();
			while (!indents.isEmpty() && indents.getLast() >= indent) {
				indents.removeLast();
				stack.removeLast();
			}
			stack.add(m.group(2));
			indents.add(indent);
			int matched = prefixLength(stack, path);
			if (matched == path.size() && stack.size() == path.size()) {
				out.set(i, m.group(1) + m.group(2) + ": " + value);
				return out;
			}
			if (matched == stack.size() && matched > deepestMatch) {
				deepestMatch = matched;
				insertAfter = i;
				insertIndent = indent;
			} else if (deepestMatch > 0 && stack.size() > deepestMatch && prefixLength(stack, path) >= deepestMatch) {
				insertAfter = i;
			}
		}
		List<String> added = new ArrayList<>();
		int indent = deepestMatch == 0 ? 0 : insertIndent + 2;
		for (int level = deepestMatch; level < path.size(); level++) {
			String key = path.get(level);
			added.add(" ".repeat(indent) + key + ":" + (level == path.size() - 1 ? " " + value : ""));
			indent += 2;
		}
		if (deepestMatch == 0) {
			out.addAll(added);
		} else {
			out.addAll(insertAfter + 1, added);
		}
		return out;
	}

	private static int prefixLength(List<String> stack, List<String> path) {
		int n = 0;
		while (n < stack.size() && n < path.size() && stack.get(n).equals(path.get(n))) {
			n++;
		}
		return n;
	}

	/** A TOML basic string, escaped. */
	public static String tomlString(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	/** A single-quoted YAML string, escaped. */
	public static String yamlString(String value) {
		return "'" + value.replace("'", "''") + "'";
	}
}
