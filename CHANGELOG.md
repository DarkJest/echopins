# Changelog

All notable changes to EchoPins are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Nothing yet.

## [1.1.0] — 2026-08-10

Fabric support. The mod's behaviour on NeoForge is unchanged: every fix below is in code that has
only ever run on Fabric, or in documentation.

### Added

- **Fabric build.** `echopins-fabric-1.1.0+mc1.21.1.jar`, requiring Fabric Loader 0.19.3+, Fabric
  API and Simple Voice Chat for Fabric. It is built from the same sources as the NeoForge jar and
  speaks the same protocol version.
- The repository is now a two-loader monorepo: `common/` holds the 90 loader-agnostic source files,
  `neoforge/` and `fabric/` hold entry points, config and the networking binding. `common` is
  compiled by each loader project against its own remapped Minecraft; both use Mojang mappings,
  which is what makes one source tree valid for each.
- `ServerLimits.reload()`, so a loader that caches its configuration can re-read it from
  `/echopins admin reload`. NeoForge reads its config live and takes the no-op default.

### Fixed

- **Fabric: changing dimension did not resynchronise pins.** Fabric does not fold dimension changes
  into its respawn callback the way NeoForge does, so travelling through a portal left the previous
  dimension's markers on screen until a delta sync happened to correct it. Now wired to
  `AFTER_PLAYER_CHANGE_WORLD`.
- **Fabric: `/echopins admin reload` did not reload anything.** The running server captures one
  `ServerLimits` at start-up and holds it for its life, and the Fabric implementation replaced its
  singleton rather than mutating it, so the reloaded values were written to an object nobody read.
  The implementation is now a stable facade over a volatile snapshot.
- **Fabric: markers were drawn a stage too early**, before translucent terrain rather than after,
  so a pin seen through water or glass blended differently than on NeoForge.
- Documentation claimed the play-nearest key was unbound. It has been `R` since 1.0.1.
- Documentation claimed the protocol version is enforced on both loaders. Only NeoForge negotiates
  it; Fabric has no equivalent, and a mismatch there surfaces as a decode error instead.

### Changed

- **Artifact names now carry the loader in front of the version**:
  `echopins-neoforge-1.1.0+mc1.21.1.jar`, `echopins-fabric-1.1.0+mc1.21.1.jar`. Up to 1.0.1 the
  NeoForge jar was `echopins-1.0.1+mc1.21.1-neoforge.jar`; a trailing loader suffix reads as
  ambiguous once there are two.
- Configuration on Fabric is `config/echopins-server.json` and `config/echopins-client.json`.
  Same options, same defaults, same ranges — they come from one shared table in the source — but
  Fabric has no config system of its own, so the file is JSON rather than TOML. Unlike NeoForge,
  the Fabric files are read at start-up rather than live; see `docs/CONFIGURATION.md`.

## [1.0.1] — 2026-08-09

The first build published to Modrinth and CurseForge. `1.0.0` was tagged but never released;
everything below was found by playing the mod and fixed before anything shipped.

### Fixed

**Private pins**

- Operator status no longer bypasses discovery or playback. A private pin is readable only by its
  author and the recipients they named. The previous bypass made private pins meaningless wherever
  staff are online, and entirely meaningless in single player, where the host always holds
  permission level 4. Moderation is now shaped as removal: an operator can delete any pin without
  being able to listen to it.
- The per-listener audience filter no longer granted bystanders the requesting player's privileges.

**Playback**

- The concurrency limit was checked before the audio load was registered, so repeated presses
  queued an unbounded number of playbacks. Slots are now reserved synchronously.
- Playback can be stopped: the play key toggles, and the inbox offers Stop.
- Expired pins are now retracted from clients. The removal list is derived from what the client
  was told about, and the pin was being dropped from that set before the delta was computed, so
  markers for expired pins stayed on screen until the player reconnected.
- Delta synchronisation never ran at all: the "never synced" sentinel was `Long.MIN_VALUE`, and
  subtracting it overflowed, so the throttle always concluded no time had passed. New pins only
  appeared after a relog.
- Out-of-range playback reported "you can't create a pin here"; it now has its own message.
- A player could not replay their own message, because the play action filtered on unread state
  and creating a pin marks it read for its author.
- Playback completion is reported to the client, so the indicator no longer lingers.

**Interface**

- World markers never rendered: a negative axis scale reversed triangle winding, and the render
  type does not disable culling, so every marker quad was silently discarded.
- Screens painted their panel after `Screen.render` had already drawn every widget, leaving
  buttons invisible though still clickable.
- Inbox rows overflowed onto the pager and Done button and swallowed their clicks.
- Inbox rows now show the caption, and the distance to each pin; Play is greyed out for anything
  out of reach.
- Indicator dots overlapped the text beside them. Both HUDs now size themselves from their
  content, which also stops longer translations running past the panel edge.

**Other**

- A recording is no longer destroyed when the create cooldown refuses it, and the audio for a
  rejected pin is actually deleted rather than left for the orphan sweep.
- A recording stored while its author was disconnecting is discarded instead of leaking.
- A voice chat disconnect now ends an open recording immediately instead of after the silence
  timeout.
- The general request limiter reported the create-cooldown message for every throttled action.
- `error.create_cooldown` was sent without the argument its translation expects, so players saw a
  literal `%s`.

### Added

- A "now playing" indicator showing which pin is playing and how long is left.
- Markers show playback with a size change and an expanding ripple.
- Default binding for the play key (`R`); it was previously unbound while the interface told
  players to press it.

## [1.0.0] — 2026-08-09

Tagged but never published. Superseded by 1.0.1.

First release content. Minecraft 1.21.1, NeoForge, requires Simple Voice Chat.

**Artifact**

```
echopins-1.0.1+mc1.21.1-neoforge.jar
SHA-256: b9632788f1357239a19e97cd7d99e976e20bca60636a0cac3cc7fbc3b46e553f
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
- Audio is stored unencrypted inside the world save. Operator status grants no in-game access to a
  private pin, but anyone who can read the server's files can read the audio. Documented rather
  than papered over — see `PRIVACY.md`.
- Simple Voice Chat is the only supported backend in 1.0, though the adapter seam for others
  exists.
- Screenshots shipped with this release are labelled UI mockups; real in-game captures are still
  to be taken.
- The manual integration matrix in `docs/TESTING.md` has not been executed end to end on a live
  multi-client server.

[Unreleased]: https://github.com/DarkJest/echopins/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/DarkJest/echopins/releases/tag/v1.0.1
[1.0.0]: https://github.com/DarkJest/echopins/releases/tag/v1.0.0
