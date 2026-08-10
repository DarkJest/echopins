# EchoPins 1.1.1

Reliability hotfix for Minecraft 1.21.1 on **Fabric and NeoForge**.

- Atomic audio-storage quota enforcement under concurrent saves.
- Closed recording/playback races around disconnects, death, portals and shutdown.
- Revalidated playback access, range and voice-chat state after disk IO.
- Added server-side ray-trace validation for block-attached pins.
- Preserved world data from newer schemas instead of downgrading it.
- Bounded large-player-list packets and fixed multi-loader release artifact collection.
- Refreshed dual-loader banners, UI mockups and installation documentation.

Choose the jar matching your loader. Fabric also requires Fabric API.
