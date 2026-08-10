# EchoPins 1.1.1

A reliability hotfix for the Fabric and NeoForge builds on Minecraft 1.21.1.

## Fixed

- Atomic total-audio quota enforcement, including concurrent saves and the incoming recording.
- Recording and playback races around disconnects, reconnects, death, portals and shutdown.
- Playback now revalidates access, range, dimension and voice-chat state after async disk IO.
- Server-side ray-trace validation for block-attached pins.
- Non-destructive handling of world data written by newer EchoPins schemas.
- Packet bounds on servers with more than 256 known players.
- CI and release collection of both loader jars.

## Presentation

- New Fabric + NeoForge banners and refreshed, explicitly labelled UI mockups.
- Corrected loader badges and Fabric installation copy.

Download exactly one EchoPins jar for your loader. Both sides need EchoPins, Simple Voice Chat and
Java 21; Fabric also requires Fabric API. The release workflow appends checksums for both jars.
