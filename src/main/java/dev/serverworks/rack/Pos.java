package dev.serverworks.rack;

/** A block position, kept free of game classes so rack rules can be tested on their own. */
public record Pos(int x, int y, int z) {
	public Pos offset(int dx, int dy, int dz) {
		return new Pos(x + dx, y + dy, z + dz);
	}
}
