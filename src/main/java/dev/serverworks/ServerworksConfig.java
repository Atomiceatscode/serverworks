package dev.serverworks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Settings for the machine that runs rack servers, in {@code config/serverworks.json}.
 * Edit the file, or use the config screen through Mod Menu. Port changes apply after a restart.
 */
public final class ServerworksConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static volatile ServerworksConfig current;

	/** Minutes a server may sit empty before it goes to sleep; 0 never sleeps. */
	public int idleMinutes = 10;
	/** How many rack servers may run at once on this machine. */
	public int maxRunning = 6;
	/** The ports rack servers use. */
	public int firstPort = 25600;
	public int lastPort = 25699;
	/**
	 * When true, only the singleplayer owner or an operator may start and stop racks. Starting
	 * a rack runs downloaded code on this machine, so think before turning it off.
	 */
	public boolean operatorsOnly = true;
	/** Minutes between automatic backups of each running rack's world; 0 turns them off. */
	public int backupMinutes = 0;
	/** How many backups to keep per rack; older ones are deleted. */
	public int keepBackups = 10;
	/**
	 * On a dedicated server, false (the default) passes Gateway players through this server's
	 * own port, so racks need no ports of their own opened. True lets players connect to each
	 * rack's port directly instead; those ports must then be reachable.
	 */
	public boolean directPorts = false;
	/** With direct ports, the address players use to reach this server, shown with each rack's port. */
	public String publicAddress = "";

	public static ServerworksConfig get() {
		ServerworksConfig config = current;
		if (config == null) {
			synchronized (ServerworksConfig.class) {
				config = current;
				if (config == null) {
					config = load();
					current = config;
				}
			}
		}
		return config;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(Serverworks.MOD_ID + ".json");
	}

	private static ServerworksConfig load() {
		Path file = file();
		ServerworksConfig config = new ServerworksConfig();
		if (Files.exists(file)) {
			try {
				ServerworksConfig read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ServerworksConfig.class);
				if (read != null) {
					config = read;
				}
			} catch (IOException | JsonParseException e) {
				Serverworks.LOGGER.error("Could not read {}; using defaults", file, e);
			}
		}
		config.clamp();
		config.save();
		return config;
	}

	/** Keeps every value in a range the runtime can work with. */
	public void clamp() {
		idleMinutes = Math.clamp(idleMinutes, 0, 24 * 60);
		maxRunning = Math.clamp(maxRunning, 1, 32);
		backupMinutes = Math.clamp(backupMinutes, 0, 7 * 24 * 60);
		keepBackups = Math.clamp(keepBackups, 1, 100);
		firstPort = Math.clamp(firstPort, 1024, 65535);
		lastPort = Math.clamp(lastPort, firstPort, 65535);
		if (publicAddress == null || !publicAddress.matches("[A-Za-z0-9.:\\-\\[\\]]{0,253}")) {
			publicAddress = "";
		}
	}

	public void save() {
		clamp();
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			Serverworks.LOGGER.error("Could not save {}", file(), e);
		}
	}
}
