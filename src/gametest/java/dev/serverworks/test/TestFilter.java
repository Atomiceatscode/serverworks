package dev.serverworks.test;

import java.util.List;

/**
 * Runs only some game tests when SERVERWORKS_GAMETEST_ONLY lists them (comma-separated class
 * names, such as {@code PolishVisualTest}). Unset, every test runs except the opt-in ones.
 */
final class TestFilter {
	private TestFilter() {
	}

	static boolean skip(Class<?> test) {
		String only = System.getenv("SERVERWORKS_GAMETEST_ONLY");
		boolean named = only != null && List.of(only.split(",")).contains(test.getSimpleName());
		if (OPT_IN.contains(test.getSimpleName())) {
			return !named;
		}
		return only != null && !only.isBlank() && !named;
	}

	/** Run only when named: ShowcaseWorldTest makes a world to play in rather than testing anything. */
	private static final List<String> OPT_IN = List.of("ShowcaseWorldTest");
}
