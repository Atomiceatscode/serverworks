package dev.serverworks.host;

import dev.serverworks.rack.Platform;

/** Knows where one platform's server comes from and how to pick a build of it. */
public interface PlatformProvider {
	Platform platform();

	/** Looks up the newest suitable build. Does network requests, so never call it on the game thread. */
	InstallPlan plan(Fetcher fetcher, String minecraftVersion) throws PrepareException;
}
