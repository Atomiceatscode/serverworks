# Modrinth listing

Everything needed to create the Serverworks project page on Modrinth. Nothing here has
been published.

## Project settings

| Field | Value |
|---|---|
| Name | Serverworks |
| Slug | `serverworks` (free when checked on 2026-11-10) |
| Summary | Build a server rack, load it with mods and plugins from Modrinth, and walk into the real server it runs. |
| Type | Mod |
| Loaders | Fabric |
| Game versions | 26.3 |
| Client side | Required |
| Server side | Required |
| Categories | Technology, Utility, Management |
| License | MIT |
| Icon | `src/main/resources/assets/serverworks/icon.png` (256 × 256) |
| Dependencies | Fabric API (required); Mod Menu, Jade (optional) |
| Source | the project's Git repository, once published |

## Version 1.1.0

- File: `build/libs/serverworks-1.1.0.jar`
- Release channel: Release
- Changelog:
  - One-port mode: on a dedicated server, players reach every rack through the server's
    own port; racks need no ports opened.
  - Config Card: difficulty, game mode, hardcore, PvP, keep inventory, flight, distances,
    spawn protection and MOTD for a rack's server.
  - World Drive: a world per drive, with a name, seed and type, or a copy of a
    singleplayer world. Swap drives to switch worlds.
  - Backups tab: back up while running, restore when stopped, optional automatic backups.
  - Pin a disk to one version; the Server Core flags updates and applies them in one click.
  - Redstone: comparators read how full a rack is, and a signal can start and stop it.
  - Status Display: players' heads, memory and uptime.
  - Disk Writer favourites, and Modrinth collections with `collection:ID`.
  - JEI and REI information pages; new manual pages for everything above.

## Version 1.0.1

- File: `build/libs/serverworks-1.0.1.jar`
- Release channel: Release
- Changelog:
  - Search the Disk Writer by author: type `@name`, or click an author's name.
  - Drag a project onto the Disk Writer's panel to pick it; Mac trackpad taps now work.
  - "Serverworks Showcase" switch on the Create World screen builds a tour of every block.
  - The Operator's Manual draws every crafting recipe.
  - Press B on a rack server to go back; a message on arrival says how.
  - Redrawn Platform Module textures.
  - Fixed: players were refused by the whitelist on Fabric, Forge, NeoForge, Quilt and
    Vanilla racks in offline mode.
  - Fixed: a rack could be given a port another game's rack was using.
  - Fixed: the console hint overflowed the Server Core panel.

## Version 1.0.0

- File: `build/libs/serverworks-1.0.0.jar`
- Release channel: Release
- Changelog: "First release."

## Description

```markdown
**Build a server rack in your world, load it with mods and plugins from Modrinth, switch it
on, and walk into the real Minecraft server it runs.**

Serverworks turns servers into machines. Search Modrinth from the Disk Writer and write
projects onto disks. Build a rack, choose its software with a Platform Module, slot your
disks into its Drive Bays and press Start. The rack downloads everything, starts a real
server on your computer, and its Gateway takes you there. "Return" in the pause menu brings
you back.

### Features
- **The whole catalog in game.** Every mod, plugin, data pack and modpack on Modrinth can
  be written onto a disk that shows the project's name and icon.
- **13 platforms.** Vanilla, Paper, Purpur, Folia, Spigot, CraftBukkit, Sponge, Fabric,
  Quilt, NeoForge, Forge, and Velocity and BungeeCord proxies that join several racks into
  one network with Link Cards.
- **Modpacks.** A Pack Cartridge installs a modpack's server side and hands players the
  pack they need.
- **A rack to build.** Iron, diamond and netherite tiers, vents, memory, drive bays, a
  Status Display showing who is on, and a Rack Wrench that outlines anything missing.
- **Alive.** Spinning fans, blinking drives, a hum, a boot chime and a crash alarm.
- **Operator's Manual and advancements** to guide you, plus Mod Menu settings and Jade
  tooltips if you have them.

### Good to know
- Racks run real servers, so they use real memory (1 to 16 GB each) and need an internet
  connection.
- Only you (in singleplayer) or operators (on a server) can start racks, because starting
  one runs downloaded code on that computer.
- Players can walk in when nothing on the rack has to be installed on their side;
  otherwise the rack gives you a client modpack to share.
- Spigot and CraftBukkit are compiled the first time you use them, which takes a few
  minutes and needs Git.

*Serverworks uses the Modrinth API and is not affiliated with or endorsed by Modrinth.*
```

## Gallery

Upload in this order; the first is the featured image.

| File | Title | Description |
|---|---|---|
| `docs/screenshots/gateway_open.png` | A running rack | Gateway open, Status Display showing the server online. |
| `docs/screenshots/disk_writer_screen.png` | The Disk Writer | Search Modrinth and write a project onto a disk. |
| `docs/screenshots/server_running.png` | The Server Core | Status, limits and the disks a rack is carrying. |
| `docs/screenshots/server_console.png` | Live console | The server's output, and commands typed in game. |
| `docs/screenshots/on_rack_server.png` | Inside | Standing on the rack's own server. |
| `docs/screenshots/rack_neoforge.png` | Platforms | Each platform colours the rack's frame. |
| `docs/screenshots/proxy_core.png` | Proxies | A Velocity rack with a linked Paper rack behind it. |
| `docs/screenshots/wrench_preview.png` | Rack Wrench | Everything still missing, outlined. |
| `docs/screenshots/manual.png` | Operator's Manual | Every feature explained in game. |
| `docs/screenshots/jade.png` | Jade support | Rack details at a glance. |
