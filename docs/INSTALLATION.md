# Installation

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1 (exactly) |
| Loader | NeoForge 21.1.0 or newer, **or** Fabric Loader 0.19.3 or newer |
| Java | 21 |
| Required mod | Simple Voice Chat for 1.21.1, version 2.6.20 or newer |
| Required on Fabric | Fabric API |

EchoPins is required on **both** the client and the server. So is Simple Voice Chat.

Download the jar that matches your loader. They are separate files and are not interchangeable:

| Loader | File |
|---|---|
| NeoForge | `echopins-neoforge-<version>+mc1.21.1.jar` |
| Fabric | `echopins-fabric-<version>+mc1.21.1.jar` |

Both are built from the same source and speak the same protocol version, but a client and a server
must still run the **same loader as each other** — that is a Minecraft-wide rule, not an EchoPins
one.

## Client

1. Install NeoForge for Minecraft 1.21.1 from https://neoforged.net/, or Fabric Loader from
   https://fabricmc.net/.
2. On Fabric, also download **Fabric API** for 1.21.1.
3. Download Simple Voice Chat for 1.21.1 **for your loader**.
4. Download the EchoPins jar for your loader.
5. Put the jars in `.minecraft/mods/`.
6. Launch the game once, then open *Options → Controls → EchoPins* to see or rebind the keys.

Defaults:

| Action | Key |
|---|---|
| Create EchoPin (hold) | `B` |
| Open EchoPins inbox | `N` |
| Play or stop nearest EchoPin | `R` |

Set your voice chat up first — *Options → Voice Chat* in Simple Voice Chat. If voice chat is not
connected, EchoPins will tell you so rather than silently recording nothing.

## Server

1. Install NeoForge 21.1.x or Fabric Loader for Minecraft 1.21.1.
2. Put Simple Voice Chat and EchoPins in `mods/`, plus Fabric API if you are on Fabric.
3. Start the server once. This generates the config file: `config/echopins-server.toml` on
   NeoForge, `config/echopins-server.json` on Fabric. The options and their defaults are
   identical; only the file format differs, because each loader uses its own.
4. Read [SERVER_SETUP.md](SERVER_SETUP.md) before opening the server to the public — particularly
   the privacy section.

Simple Voice Chat needs a UDP port open. That is its requirement, not EchoPins'; follow its
documentation.

## Verifying it works

On the server console:

```
[EchoPins] EchoPins ready: 0 pin(s) loaded, 0 bytes of audio
[EchoPins/Voice] Simple Voice Chat API detected
[EchoPins/Voice] Voice chat server started; EchoPins playback is available
```

In game, `/echopins stats` should print a count. Hold `B`, say something, release, and the
confirmation screen should appear.

## Modpack authors

- No configuration is needed for EchoPins to work out of the box.
- Ship `config/echopins-server.toml` (NeoForge) or `config/echopins-server.json` (Fabric) with
  your pack if you want different limits.
- Nothing is registered into any game registry — no blocks, items, entities, biomes or
  worldgen — so there is nothing to conflict with and no registry ids to reserve.
- Simple Voice Chat is distributed under its own terms. Bundle it through the normal
  Modrinth/CurseForge channels, not by copying the jar.

## Uninstalling

Remove the jar. Pins stop appearing immediately.

The data stays in the world unless you delete it:

```
<world>/data/echopins.dat
<world>/echopins/
```

Delete both with the server stopped if you want the voice data gone. Take a backup first.
