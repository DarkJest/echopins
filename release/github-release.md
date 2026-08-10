# EchoPins 1.2.0-beta.1

Beta support line for **Minecraft 1.20.1** on Fabric, Forge and NeoForge. The existing Minecraft
1.21.1 release remains supported and is not replaced by this beta.

## Supported versions

| Minecraft | Fabric | Forge | NeoForge | Java | Download |
|---|---:|---:|---:|---:|---|
| **1.21.1** | ✅ | — | ✅ | 21 | [Stable v1.1.1](https://github.com/DarkJest/echopins/releases/tag/v1.1.1) |
| **1.20.1** | ✅ | ✅ | ✅ | 17 | Files attached to this prerelease |

## Added

- Dedicated Minecraft 1.20.1 builds for Fabric, Forge and NeoForge.
- A Forge artifact alongside the Fabric and NeoForge jars.
- Automated metadata checks for loader and Simple Voice Chat plugin discovery.

## Changed

- Ported networking, saved data, HUD overlays and rendering to the Minecraft 1.20.1 APIs.
- Development launches now use Java 17 and automatically prepare Simple Voice Chat on Fabric.
- CI and release automation now build and verify all three 1.20.1 loader artifacts.

## Fixed

- Corrected the Fabric Simple Voice Chat entrypoint. The plugin is now discovered, initialized and
  registers the EchoPins recording and playback events.

## Downloads for Minecraft 1.20.1

Choose exactly one EchoPins jar for your loader:

- `echopins-fabric-1.2.0-beta.1+mc1.20.1.jar` — Fabric Loader 0.19.3+, Fabric API.
- `echopins-forge-1.2.0-beta.1+mc1.20.1.jar` — Forge 47.4.0+.
- `echopins-neoforge-1.2.0-beta.1+mc1.20.1.jar` — NeoForge 47.1.0+.

All loaders require Simple Voice Chat 1.20.1. Both client and server need EchoPins and Simple
Voice Chat. Java 17 is required for this release line.

## Verification

- Clean build completed for all three loaders.
- 318 automated tests passed (106 per loader).
- Dedicated-server smoke tests completed on Fabric, Forge and NeoForge with Simple Voice Chat.
- Fabric, Forge and NeoForge jars target Java 17 bytecode.

This is a beta because a full multi-client gameplay session has not yet been completed. The release
workflow appends SHA-256 checksums for every published jar below.
