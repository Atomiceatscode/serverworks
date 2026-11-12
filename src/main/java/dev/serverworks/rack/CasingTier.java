package dev.serverworks.rack;

import java.util.Locale;

/**
 * What a rack's frame is made of. Stronger casing holds a bigger rack, with room for more
 * bays and memory; a rack is only as big as its weakest casing allows.
 */
public enum CasingTier {
	IRON(5),
	DIAMOND(7),
	NETHERITE(9);

	private final int maxSize;

	CasingTier(int maxSize) {
		this.maxSize = maxSize;
	}

	/** The most blocks a rack of this casing may measure along any side. */
	public int maxSize() {
		return maxSize;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "serverworks.tier." + id();
	}
}
