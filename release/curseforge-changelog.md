# EchoPins 1.1.1

Reliability hotfix for Minecraft 1.21.1 on **Fabric and NeoForge**.

### Fixed

- Atomic audio-storage cap under concurrent saves.
- Recording and playback races around disconnects, death, portals and shutdown.
- Playback now revalidates access, range and voice-chat state after disk IO.
- Server-side ray-trace validation for block-attached pins.
- Non-destructive handling of world data from newer EchoPins schemas.
- Packet bounds for large player lists.
- Multi-loader CI and release artifact collection.

### Changed

- Refreshed dual-loader banners, UI mockups and installation documentation.

Install the jar for your loader on both client and server together with Simple Voice Chat. Fabric
installations also require Fabric API. Java 21 is required.
