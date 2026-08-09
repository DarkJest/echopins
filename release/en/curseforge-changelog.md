# EchoPins 1.1.0

Fabric support. Minecraft 1.21.1, NeoForge **or** Fabric, requires Simple Voice Chat 2.6.20+.

Behaviour on NeoForge is unchanged. Everything fixed below is Fabric-only code or documentation.

### Added

- **A Fabric build**, requiring Fabric Loader 0.19.3+, Fabric API and Simple Voice Chat for Fabric.
  It is compiled from the same sources as the NeoForge jar and speaks the same protocol, so pins,
  worlds and audio files are interchangeable between the two.
- `/echopins admin reload` can now re-read configuration on loaders that cache it.

### Fixed

- Fabric: travelling through a portal did not resynchronise pins, leaving the previous dimension's
  markers on screen until something else corrected them.
- Fabric: `/echopins admin reload` wrote the reloaded values to an object the running server never
  read, so it silently did nothing.
- Fabric: markers were drawn before translucent terrain rather than after, so a pin seen through
  water or glass blended differently than on NeoForge.
- Documentation said the play-nearest key was unbound. It is `R`.

### Changed

- Jars now carry the loader in front of the version: `echopins-neoforge-1.1.0+mc1.21.1.jar` and
  `echopins-fabric-1.1.0+mc1.21.1.jar`.
- Configuration on Fabric lives in `config/echopins-server.json` and `config/echopins-client.json`.
  The options, defaults and ranges are identical to NeoForge's — they come from one shared table in
  the source — but Fabric reads them at start-up rather than live.

### Not verified

The Fabric build has not been run in game. It compiles, passes the same 101 unit tests as the
NeoForge build, and shares all of its logic, but its loader wiring has had no live testing. The
Fabric development environment also cannot load Simple Voice Chat, for a Gradle version conflict
between Loom and ModDevGradle, so the voice integration is exercised only on NeoForge.
