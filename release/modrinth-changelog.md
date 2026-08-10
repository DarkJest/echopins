# EchoPins 1.1.1

Reliability hotfix for Minecraft 1.21.1 on **Fabric and NeoForge**.

### Fixed

- Atomic total-audio quota enforcement, including concurrent recording saves.
- Stale recording/playback completion after disconnect, reconnect, death or dimension change.
- Playback access, range and voice-connection state changing while audio is loaded asynchronously.
- Client block targets being trusted without a matching server-side ray trace.
- Newer world-data schemas being overwritten by an older mod build.
- Known-player packet overflow on servers with more than 256 players.
- CI/release paths after the project moved to separate Fabric and NeoForge modules.

### Changed

- Added new dual-loader banners and refreshed, clearly labelled UI mockups.
- Corrected Fabric installation instructions and loader badges.

Choose the jar matching your loader. Both sides require EchoPins, Simple Voice Chat and Java 21;
Fabric also requires Fabric API.
