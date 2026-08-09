# Installation

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1 (exactly) |
| Loader | NeoForge 21.1.0 or newer |
| Java | 21 |
| Required mod | Simple Voice Chat for 1.21.1, version 2.6.20 or newer |

EchoPins is required on **both** the client and the server. So is Simple Voice Chat.

## Client

1. Install NeoForge for Minecraft 1.21.1 from https://neoforged.net/.
2. Download Simple Voice Chat for **1.21.1 / NeoForge**.
3. Download `echopins-<version>+mc1.21.1-neoforge.jar`.
4. Put both jars in `.minecraft/mods/`.
5. Launch the game once, then open *Options → Controls → EchoPins* to see or rebind the keys.

Defaults:

| Action | Key |
|---|---|
| Create EchoPin (hold) | `B` |
| Open EchoPins inbox | `N` |
| Play nearest EchoPin | unbound |

Set your voice chat up first — *Options → Voice Chat* in Simple Voice Chat. If voice chat is not
connected, EchoPins will tell you so rather than silently recording nothing.

## Server

1. Install NeoForge 21.1.x for Minecraft 1.21.1.
2. Put Simple Voice Chat and EchoPins in `mods/`.
3. Start the server once. This generates `config/echopins-server.toml`.
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
- Ship `config/echopins-server.toml` with your pack if you want different limits.
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
