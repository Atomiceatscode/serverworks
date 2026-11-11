package dev.serverworks.rack;

import java.util.Locale;

/** What a rack's frame is made of. A rack is only as good as its weakest casing. */
public enum CasingTier {
	IRON(4096, 10),
	DIAMOND(8192, 40),
	NETHERITE(16384, 100);

	private final int maxRamMb;
	private final int maxPlayers;

	CasingTier(int maxRamMb, int maxPlayers) {
		this.maxRamMb = maxRamMb;
		this.maxPlayers = maxPlayers;
	}

	public int maxRamMb() {
		return maxRamMb;
	}

	public int maxPlayers() {
		return maxPlayers;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "serverworks.tier." + id();
	}
}
