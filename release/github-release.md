# EchoPins 1.1.1

A reliability hotfix for the Fabric and NeoForge builds of EchoPins on Minecraft 1.21.1.

## Fixed

- Audio storage limits are now atomic and include the incoming recording, so concurrent saves
  cannot exceed the configured quota.
- Disconnect, reconnect, death, dimension-change and shutdown races can no longer revive stale
  recordings or playback requests.
- Playback rechecks access, distance, dimension and voice-chat connection after async disk IO.
- Block-attached pins are validated with the server's own ray trace instead of trusting the client.
- Worlds written by a newer EchoPins schema are preserved verbatim instead of being downgraded on
  autosave.
- Known-player packets stay inside their 256-entry wire limit on large servers.
- CI and release workflows now collect both loader jars from the multi-project build.

## Presentation

- New Fabric + NeoForge banners.
- Refreshed UI mockups with an explicitly labelled AI-assisted promotional backdrop.
- Corrected loader badges and Fabric installation instructions.

## Downloads

Choose exactly one EchoPins jar for your loader:

- `echopins-fabric-1.1.1+mc1.21.1.jar` — requires Fabric Loader 0.19.3+, Fabric API and Simple Voice Chat.
- `echopins-neoforge-1.1.1+mc1.21.1.jar` — requires NeoForge 21.1.0+ and Simple Voice Chat.

Both client and server need EchoPins and Simple Voice Chat. Java 21 is required.

## Verification

The clean build runs the same automated suite against both loaders. Full multi-client gameplay
testing with Simple Voice Chat is still a manual step; the repository keeps that limitation
explicit rather than presenting the promotional UI mockups as gameplay screenshots.

The release workflow appends SHA-256 checksums for both published jars below.
