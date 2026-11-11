package dev.serverworks.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What a Config Card sets on a rack's server. Values are checked on the way in, so nothing a
 * client sends can put arbitrary text into {@code server.properties} or the console.
 */
public record ServerSettings(
		Difficulty difficulty,
		GameMode gameMode,
		boolean hardcore,
		boolean pvp,
		boolean keepInventory,
		boolean allowFlight,
		int viewDistance,
		int simulationDistance,
		int spawnProtection,
		String motd) {

	public static final int MAX_MOTD = 59;
	public static final ServerSettings DEFAULT = new ServerSettings(Difficulty.EASY, GameMode.SURVIVAL, false, true, false,
			false, 10, 10, 16, "");

	public static final Codec<ServerSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
			enumCodec(Difficulty.class).optionalFieldOf("difficulty", DEFAULT.difficulty).forGetter(ServerSettings::difficulty),
			enumCodec(GameMode.class).optionalFieldOf("gamemode", DEFAULT.gameMode).forGetter(ServerSettings::gameMode),
			Codec.BOOL.optionalFieldOf("hardcore", false).forGetter(ServerSettings::hardcore),
			Codec.BOOL.optionalFieldOf("pvp", true).forGetter(ServerSettings::pvp),
			Codec.BOOL.optionalFieldOf("keep_inventory", false).forGetter(ServerSettings::keepInventory),
			Codec.BOOL.optionalFieldOf("allow_flight", false).forGetter(ServerSettings::allowFlight),
			Codec.intRange(2, 32).optionalFieldOf("view_distance", 10).forGetter(ServerSettings::viewDistance),
			Codec.intRange(2, 32).optionalFieldOf("simulation_distance", 10).forGetter(ServerSettings::simulationDistance),
			Codec.intRange(0, 64).optionalFieldOf("spawn_protection", 16).forGetter(ServerSettings::spawnProtection),
			Codec.string(0, MAX_MOTD).optionalFieldOf("motd", "").forGetter(ServerSettings::motd)
	).apply(i, ServerSettings::new));

	public static final StreamCodec<ByteBuf, ServerSettings> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	public ServerSettings {
		viewDistance = Math.clamp(viewDistance, 2, 32);
		simulationDistance = Math.clamp(simulationDistance, 2, 32);
		spawnProtection = Math.clamp(spawnProtection, 0, 64);
		motd = cleanMotd(motd);
	}

	/** Keeps a message of the day to printable characters on one line, without formatting codes. */
	public static String cleanMotd(String motd) {
		if (motd == null) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < motd.length() && out.length() < MAX_MOTD; i++) {
			char c = motd.charAt(i);
			if (c >= 32 && c != 127 && c != '§' && c != '\\') {
				out.append(c);
			}
		}
		return out.toString().strip();
	}

	/** The {@code server.properties} values this card sets. */
	public Map<String, String> properties() {
		Map<String, String> out = new LinkedHashMap<>();
		out.put("difficulty", difficulty.name().toLowerCase(Locale.ROOT));
		out.put("gamemode", gameMode.name().toLowerCase(Locale.ROOT));
		out.put("force-gamemode", "true");
		out.put("hardcore", Boolean.toString(hardcore));
		out.put("allow-flight", Boolean.toString(allowFlight));
		out.put("view-distance", Integer.toString(viewDistance));
		out.put("simulation-distance", Integer.toString(simulationDistance));
		out.put("spawn-protection", Integer.toString(spawnProtection));
		if (!motd.isEmpty()) {
			out.put("motd", motd);
		}
		return out;
	}

	/** Console commands for the settings that are game rules rather than properties. */
	public List<String> gameRuleCommands() {
		List<String> out = new ArrayList<>();
		out.add("gamerule pvp " + pvp);
		out.add("gamerule keep_inventory " + keepInventory);
		return out;
	}

	private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
		return Codec.STRING.xmap(name -> {
			try {
				return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return type.getEnumConstants()[0];
			}
		}, value -> value.name().toLowerCase(Locale.ROOT));
	}

	public enum Difficulty {
		PEACEFUL, EASY, NORMAL, HARD
	}

	public enum GameMode {
		SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR
	}
}
