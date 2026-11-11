package dev.serverworks.host;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** A stand-in for a Minecraft server: prints what a real one prints and obeys "stop". */
public final class FakeServer {
	public static void main(String[] args) throws Exception {
		String mode = args.length > 0 ? args[0] : "normal";
		System.out.println("[00:00:00] [main/INFO]: Starting fake server");
		if (mode.equals("eula")) {
			System.out.println("[00:00:00] [main/INFO]: You need to agree to the EULA in order to run the server. Go to eula.txt for more info.");
			return;
		}
		if (mode.equals("dies")) {
			System.exit(7);
		}
		System.out.println("\u001B[32m[00:00:01] [Server thread/INFO]: Done (0.123s)! For help, type \"help\"\u001B[0m");
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		String line;
		while ((line = in.readLine()) != null) {
			if (line.equals("stop")) {
				System.out.println("[00:00:02] [Server thread/INFO]: Stopping server");
				return;
			}
			if (line.equals("crash")) {
				System.exit(3);
			}
			System.out.println("[00:00:02] [Server thread/INFO]: echo " + line);
		}
	}
}
