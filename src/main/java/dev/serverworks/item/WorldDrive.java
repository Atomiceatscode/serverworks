package dev.serverworks.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * What a World Drive holds: a world of its own on any rack it is put in. The id names the
 * world's folder, so a drive keeps its world as it moves between racks' bays and back.
 *
 * @param seed       the seed used when the world is first made; empty for a random one
 * @param importSave a singleplayer save to start from, by folder name; empty for a new world
 */
public record WorldDrive(UUID id, String name, String seed, WorldType type, Optional<String> importSave) {
	public static final int MAX_NAME = 32;
	public static final int MAX_SEED = 32;

	public static final Codec<WorldDrive> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(WorldDrive::id),
			Codec.string(0, MAX_NAME).optionalFieldOf("name", "").forGetter(WorldDrive::name),
			Codec.string(0, MAX_SEED).optionalFieldOf("seed", "").forGetter(WorldDrive::seed),
			Codec.STRING.xmap(WorldType::byName, t -> t.name().toLowerCase(Locale.ROOT))
					.optionalFieldOf("type", WorldType.NORMAL).forGetter(WorldDrive::type),
			Codec.string(1, 128).optionalFieldOf("import").forGetter(WorldDrive::importSave)
	).apply(i, WorldDrive::new));

	public static final StreamCodec<ByteBuf, WorldDrive> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	public WorldDrive {
		name = clean(name, MAX_NAME, false);
		seed = clean(seed, MAX_SEED, true);
		importSave = importSave.filter(WorldDrive::safeSaveName);
	}

	/** The folder this drive's world lives in on a rack's server. */
	public String levelName() {
		return "world-" + id.toString().replace("-", "").substring(0, 8);
	}

	/** Save folder names come from the client, so only plain names are accepted. */
	public static boolean safeSaveName(String name) {
		return !name.isBlank() && name.length() <= 128 && !name.contains("/") && !name.contains("\\") && !name.contains("..")
				&& !name.startsWith(".");
	}

	private static String clean(String text, int max, boolean seed) {
		if (text == null) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length() && out.length() < max; i++) {
			char c = text.charAt(i);
			boolean ok = seed ? Character.isLetterOrDigit(c) && c < 128 || c == '-' || c == '_' || c == ' '
					: c >= 32 && c != 127 && c != '§';
			if (ok) {
				out.append(c);
			}
		}
		return out.toString().strip();
	}

	public enum WorldType {
		NORMAL, FLAT, LARGE_BIOMES, AMPLIFIED;

		public String levelType() {
			return "minecraft:" + name().toLowerCase(Locale.ROOT);
		}

		static WorldType byName(String name) {
			for (WorldType type : values()) {
				if (type.name().equalsIgnoreCase(name)) {
					return type;
				}
			}
			return NORMAL;
		}
	}
}
