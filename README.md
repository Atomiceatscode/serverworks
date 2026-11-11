# Serverworks

A Fabric mod for Minecraft 26.3. Build a server rack in your world, load it with mods and
plugins from the live Modrinth catalog, switch it on, and a real Minecraft server starts
that you can walk into through the rack's Gateway.

Powered by the [Modrinth API](https://docs.modrinth.com/). Serverworks is not affiliated
with or endorsed by Modrinth.

| | |
|---|---|
| ![A running rack](docs/screenshots/gateway_open.png) | ![The Disk Writer](docs/screenshots/disk_writer_screen.png) |
| ![The Server Core](docs/screenshots/server_running.png) | ![The live console](docs/screenshots/server_console.png) |

## What it does

- **Disks from the catalog.** The Disk Writer searches Modrinth in game. Write a project
  onto a Blank Disk and the disk takes its name and icon. Modpacks become Pack Cartridges.
- **Racks you build.** A hollow box of Rack Casing, 3 to 7 blocks a side, with a Server
  Core, a Platform Module, Drive Bays, Memory Banks and Vents. Iron, diamond and netherite
  casings set the memory and player limits. The Rack Wrench outlines anything missing.
- **Real servers.** Starting a rack downloads the server software and the newest version
  of each disk's project that suits it, checks every file's hash, and runs the server on
  your computer. The Server Core shows progress, the address and a live console.
- **Walk in.** Use the Gateway and the game saves your world and joins the rack's server.
  "Return to …" in the pause menu takes you back. Status Displays show who is on.
- **Every major platform.** Vanilla, Paper, Purpur, Folia, Spigot, CraftBukkit, Sponge,
  Fabric, Quilt, NeoForge and Forge servers; Velocity and BungeeCord proxies in front of
  other racks, linked with Link Cards.
- **Feels like a machine.** Fans spin, lights blink, the rack hums, chimes when it comes
  up and sounds an alarm if it crashes. The Operator's Manual explains everything, and an
  advancement tab runs from your first disk to a netherite rack.

## Requirements

- Minecraft 26.3, Fabric Loader 0.20.1 or newer, Fabric API.
- Java 25 (what Minecraft 26.3 itself needs). Rack servers reuse the game's Java.
- An internet connection, and memory for the servers you run: each uses its rack's
  memory (1 to 16 GB) on top of the game's.
- `git`, only for Spigot and CraftBukkit racks (they are compiled the first time).
- Optional: [Mod Menu](https://modrinth.com/mod/modmenu) for a settings screen,
  [Jade](https://modrinth.com/mod/jade) for rack details when looking at rack blocks.

## Quick start

1. Craft Blank Disks (iron, paper, redstone) and a Disk Writer. The Operator's Manual is a
   book plus a Blank Disk.
2. Write some disks in the Disk Writer: search, pick a project, take the disk.
3. Build a 3 x 3 x 3 box of Iron Rack Casing. Swap face centres for a Server Core, a
   Platform Module, a Drive Bay, a Memory Bank and a Vent; leave the middle empty. The
   sixth face centre takes a Gateway. For more room, make the rack longer.
4. Put your disks in the Drive Bay, open the Server Core and press Start. Agree to the
   Minecraft EULA the first time.
5. When it says Running, use the Gateway.

## Platforms

| Group | Platforms | Takes |
|---|---|---|
| Vanilla | Vanilla | Data packs |
| Mods | Fabric, Quilt, NeoForge, Forge | Mods, data packs, one Pack Cartridge |
| Plugins | Paper, Purpur, Folia, Spigot, CraftBukkit, Sponge | Plugins, data packs |
| Proxies | Velocity, BungeeCord | Proxy plugins, Link Cards to other racks |

Waterfall is listed but will not start: it is discontinued and cannot connect players on
26.3. Sponge only has release candidates for 26.3.

## Settings

`config/serverworks.json`, created on first start. Mod Menu opens it as a screen.

| Setting | Default | Meaning |
|---|---|---|
| `idleMinutes` | 10 | Minutes an empty server waits before sleeping; 0 never sleeps |
| `maxRunning` | 6 | Rack servers allowed to run at once |
| `firstPort`, `lastPort` | 25600, 25699 | Ports rack servers use (restart to apply) |
| `operatorsOnly` | true | Only the singleplayer owner or operators may start and stop racks |
| `publicAddress` | empty | On a dedicated server, the address shown to players with each rack's port |

## How rack servers run

- Everything lives in `serverworks/` inside the game folder: `cache/` holds downloaded
  server jars and mod files (shared by all racks), `instances/<rack id>/` holds one rack's
  server: its world, configs and logs. Delete an instance folder to reset that server.
- Each rack keeps one port for the session. Servers listen only on `127.0.0.1` in
  singleplayer, and on all addresses on a dedicated server.
- New servers only admit whitelisted players. The Gateway whitelists whoever it sends;
  anyone joining by address must be added by an operator (`whitelist add <name>` in the
  rack's console).
- An empty server sleeps after `idleMinutes`. Anyone can wake it through the Gateway;
  starting it fresh still needs the owner or an operator.
- The Gateway only lets players through when nothing on the rack has to be installed on
  their side. Otherwise it names the mods they need, and the Server Core's Client Pack
  button saves a `.mrpack` to `serverworks/client-packs/` for the Modrinth App or Prism
  Launcher.
- Spigot and CraftBukkit are compiled with Spigot's BuildTools the first time (several
  minutes). NeoForge, Forge and Quilt run their official installer in the server's folder
  the first time.
- Proxies: sneak-use a Link Card on a rack's Server Core, put the card in a proxy rack's
  Drive Bay, and starting the proxy starts the linked racks. They then listen only on this
  computer and trust the proxy for identities (Velocity modern forwarding where every rack
  supports it, BungeeCord legacy forwarding otherwise, none for the rest, in which case
  players appear with offline identities).
- Pack Cartridges must be for this Minecraft version and the rack's platform, and may only
  download from Modrinth. Their settings are copied in once per pack version.
- A dedicated server stops its rack servers when it shuts down. In singleplayer they keep
  running after you leave the world (so the Gateway can take you there) until the game quits.
- Files you add to a server's `mods/` or `plugins/` folder by hand are left alone, and
  `server.properties` edits are kept, except the port, player cap and network settings.

## Security

Starting a rack downloads and runs code on the computer running the game. Serverworks
keeps that contained:

- Only the singleplayer owner, or an operator on a dedicated server, can start or stop a
  rack, accept the EULA, or send console commands (`operatorsOnly`). Other players can look
  at a rack but do not receive its console or error details.
- Downloads come only from a fixed list of official hosts (Modrinth, Mojang, PaperMC,
  Fabric, Quilt, NeoForge, Forge, Sponge, Purpur, Spigot), never follow redirects, and are
  checked against the publisher's hash. Fabric's server launcher and BungeeCord publish no
  hash and rely on HTTPS. Installers that Serverworks runs fetch their own libraries.
- Nothing from a disk, packet or modpack can choose a file path outside the server's folder.
- Proxy forwarding secrets are readable only by your user account.

## Building and testing

Needs JDK 25. On macOS with Homebrew's `openjdk@25`, set `JAVA_HOME=/opt/homebrew/opt/openjdk@25`.

```bash
./gradlew build
```

Unit tests run with `build`. Two slower suites need the internet:

```bash
./gradlew hostSmokeTest
```

installs, starts and stops a real server on every platform, two proxies and a real modpack
(about 1 GB and 10 minutes the first time, cached in `build/smoke/` after). Pick some with
`-Pplatforms=paper,velocity,pack`.

```bash
./gradlew runClientGameTest
```

drives a real game through every feature and saves screenshots to
`build/run/clientGameTest/screenshots/` (about 15 minutes; keep the computer awake). Pick
some with `SERVERWORKS_GAMETEST_ONLY=PolishVisualTest`. `DedicatedVisualTest` starts a
throwaway dedicated test server, so `build.gradle` accepts the Minecraft EULA for that test
server (`eula = true` in `configureTests`).

Art, sound and data files are generated by the scripts in `art/` (needs the packages in
`art/requirements.txt`); edit a script and rerun it rather than editing the output:

```bash
cd art && python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
```

```bash
cd art && .venv/bin/python make_textures.py && .venv/bin/python make_rack_textures.py && .venv/bin/python make_sounds.py && .venv/bin/python make_data.py
```

## Layout

- `src/main/java/dev/serverworks/catalog/`: Modrinth client. No Minecraft classes.
- `src/main/java/dev/serverworks/rack/`: rack rules, platforms, preview. No Minecraft classes.
- `src/main/java/dev/serverworks/host/`: downloading, installing and running servers.
- `src/main/java/dev/serverworks/{item,machine,net,registry}/`: items, blocks, menus, packets.
- `src/client/`: screens, rendering, icons, sounds, trips, Mod Menu and Jade integration.
- `src/test/`, `src/gametest/`: unit tests and in-game walkthroughs.
- `art/`: generators for every texture, sound and JSON resource.

## License

MIT. The jar bundles [TwelveMonkeys ImageIO](https://github.com/haraldk/TwelveMonkeys)
(BSD 3-Clause) to decode WebP icons. All textures and sounds are original, generated by
the scripts in `art/`.
