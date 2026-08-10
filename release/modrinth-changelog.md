# EchoPins 1.2.0-beta.1

Beta support for Minecraft **1.20.1** on Fabric, Forge and NeoForge. Minecraft 1.21.1 remains
available as the stable v1.1.1 release for Fabric and NeoForge.

### Added

- Minecraft 1.20.1 builds for Fabric, Forge and NeoForge.
- Dedicated Forge artifact.
- Loader metadata regression tests.

### Fixed

- Fabric now registers the correct Simple Voice Chat plugin entrypoint, so recording and playback
  events are available.

### Verification

- 318 automated tests passed.
- All three dedicated-server smoke tests loaded EchoPins with Simple Voice Chat.

Install the file matching your loader. Both client and server require EchoPins and Simple Voice
Chat. Fabric also requires Fabric API. Java 17 is required.
