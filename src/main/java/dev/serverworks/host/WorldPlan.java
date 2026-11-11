package dev.serverworks.host;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Which world a rack's server plays, from the World Drive in its bays.
 *
 * @param levelName  the world's folder inside the server folder
 * @param seed       the seed for a new world, or empty for a random one
 * @param levelType  {@code minecraft:normal}, {@code minecraft:flat}, {@code minecraft:large_biomes} or
 *                   {@code minecraft:amplified}
 * @param importFrom a saved world to copy in the first time this world is used, or null
 */
public record WorldPlan(String levelName, String seed, String levelType, @Nullable Path importFrom) {
	// Declared before DEFAULT, which is checked against them as it is made.
	private static final Pattern LEVEL_NAME = Pattern.compile("world(-[a-f0-9]{8})?");
	private static final Pattern SEED = Pattern.compile("-?[A-Za-z0-9 _-]{0,32}");
	/** The world a rack plays without a drive. */
	public static final WorldPlan DEFAULT = new WorldPlan("world", "", "minecraft:normal", null);

	public WorldPlan {
		if (!LEVEL_NAME.matcher(levelName).matches()) {
			throw new IllegalArgumentException("Not a world folder Serverworks makes: " + levelName);
		}
		if (!SEED.matcher(seed).matches()) {
			throw new IllegalArgumentException("Unusable seed");
		}
		if (!levelType.matches("minecraft:(normal|flat|large_biomes|amplified)")) {
			throw new IllegalArgumentException("Unknown world type " + levelType);
		}
	}

	/** Where a file meant for {@code world/...} goes for this world. */
	public String relocate(String directory) {
		if (directory.equals("world") || directory.startsWith("world/")) {
			return levelName + directory.substring("world".length());
		}
		return directory;
	}
}
