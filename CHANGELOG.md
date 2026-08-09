# Changelog

All notable changes to EchoPins are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Nothing yet.

## [1.0.0] — 2026-08-09

First release. Minecraft 1.21.1, NeoForge, requires Simple Voice Chat.

**Artifact**

```
echopins-1.0.0+mc1.21.1-neoforge.jar
SHA-256: 2c6e7084e983db52e1e5539bd72eb276fc852c228be352726d5a8af47a81ba93
```

Built against NeoForge 21.1.248 and `voicechat-api` 2.6.20. The build is reproducible: two clean
builds of this tree produce a byte-identical jar. Re-verify the checksum against whatever the
release workflow publishes, since that is built from the tagged commit.

### Added

**Voice pins**

- Record a short voice message and anchor it to a block face or a free position in the world.
- Hold-to-record on a rebindable key (default `B`), with a preview and confirmation step before
  anything is published.
- Optional text caption on each pin, which doubles as the accessibility fallback.
- Public and private visibility, with a recipient picker that selects by UUID.
- Choose a short, default, or permanent lifetime, subject to server policy.

**Discovery and playback**

- Compact world markers that fade in within the discovery radius, with distance-based scaling and
  configurable occlusion.
- A label showing author, age, length, caption and visibility when you look at a pin.
- Locational playback through Simple Voice Chat, audible only to players allowed to hear the pin.
- A dedicated **EchoPins** volume category, so playback volume is adjustable separately.
- Inbox screen with Nearby, Mine, Private and Unread tabs, and server-side paging.
- Per-player read state, so unheard messages are marked.

**Server**

- Chunk-based spatial index; no per-tick scan of all pins.
- Per-player subscriptions with snapshot plus deltas, recalculated only on chunk crossings.
- Full command tree with player and admin branches.
- 32 server options and 16 client options, every one range-validated.
- Token-bucket request limiting plus separate create and playback cooldowns.
- Per-player, per-server, per-location and total-storage caps.
- Incremental expiry sweeps and orphan-audio collection.

**Storage**

- `.epv` container: versioned, CRC-32 checked, bounds-checked Opus frames stored verbatim with no
  transcode.
- Atomic writes — temp file, fsync, atomic move — with stale temp files swept at startup.
- Metadata created only after audio is durably on disk.
- Per-pin schema versioning and a migration framework, present from v1 so future changes cannot
  strand existing worlds.

**Privacy**

- Recording only during an explicit, visible session; no background or hidden capture.
- Only the recording player's own voice is captured, never bystanders.
- Recorded audio is not simultaneously broadcast to nearby players, by default.
- Sessions end on disconnect, death, dimension change, length limit, or silence timeout.
- Unconfirmed recordings are deleted automatically.

**Other**

- Complete English and Russian localization, including config and command output.
- Accessibility: reduce-motion, adjustable marker opacity, high-contrast indicator, captions, and
  recording state signalled by shape and text rather than colour alone.
- Client-only rendering classes isolated behind a `Dist.CLIENT` entrypoint, so dedicated servers
  never load them.

### Known limitations

- **Push-to-talk users must hold their voice chat key as well as the EchoPins key.** Simple Voice
  Chat's public API exposes microphone audio only once it is already transmitting, and there is no
  supported way to start capture from outside. The HUD says so while recording and reports whether
  audio is arriving. See the README for the full explanation.
- Audio is stored unencrypted inside the world save. Server operators can read it. This is
  documented rather than papered over — see `PRIVACY.md`.
- Simple Voice Chat is the only supported backend in 1.0, though the adapter seam for others
  exists.
- Screenshots shipped with this release are labelled UI mockups; real in-game captures are still
  to be taken.
- The manual integration matrix in `docs/TESTING.md` has not been executed end to end on a live
  multi-client server.

[Unreleased]: https://github.com/DarkJest/echopins/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/DarkJest/echopins/releases/tag/v1.0.0
