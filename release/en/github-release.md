# EchoPins 1.0.0

**Leave a message where it matters.**

The first release of EchoPins — world-anchored asynchronous voice messaging for Minecraft 1.21.1
on NeoForge.

Record a short voice message, anchor it to a chest, a portal, a machine or just a spot in the
world, and your friends hear it when they get there. The author does not need to be online.

## Highlights

- **Voice notes anchored to places** — attach to a block face or drop one in mid-air.
- **Works offline** — that is the whole point.
- **Quiet by design** — a small marker that fades in as you approach, and nothing else.
- **Public or private** — private pins are enforced server-side, and again per listener inside the
  audio channel.
- **Its own volume slider** — a dedicated Simple Voice Chat volume category.
- **Built to scale** — chunk-based spatial index, per-player subscriptions with deltas, all disk
  work off the server thread.

## Features

<details>
<summary>Full feature list</summary>

**Recording**

- Hold-to-record on a rebindable key (default `B`)
- Preview and confirmation before anything is published
- Optional caption, doubling as an accessibility fallback
- Short, default or permanent expiry, subject to server policy
- Visible recording indicator with elapsed time, progress bar and live audio status

**Discovery and playback**

- Distance-faded billboard markers with configurable occlusion, opacity and scale
- Focused-pin label with author, relative age, duration, caption and visibility
- Locational playback with a per-listener access filter
- Inbox with Nearby, Mine, Private and Unread tabs, and per-player read state

**Server**

- Chunk-based spatial index, no per-tick full scans
- Snapshot plus delta synchronisation, recalculated only on chunk crossings
- 32 server options and 16 client options, all range-validated
- Token-bucket request limiting, plus create and playback cooldowns
- Per-player, per-server, per-location and total-storage caps
- Incremental expiry sweeps and orphan-audio collection

**Storage**

- Versioned, CRC-32 checked `.epv` container holding Opus frames verbatim — no transcode
- Atomic writes with stale temp-file sweeping
- Metadata created only after audio is durably on disk
- Per-pin schema versioning and a migration framework from day one

**Other**

- Complete English and Russian translations, including config and command output
- Accessibility: reduce motion, marker opacity, high-contrast indicator, captions, and state
  signalled by shape and text rather than colour alone

</details>

## For server admins

Defaults are chosen so an untouched install cannot fill a disk: pins expire after a week, storage
is capped at 1 GiB, and both creating and playing are rate limited.

```
/echopins admin stats
/echopins admin delete <id>
/echopins admin purge player <player>
/echopins admin purge expired
/echopins admin cleanup
```

Read [docs/SERVER_SETUP.md](docs/SERVER_SETUP.md) before opening a server to the public,
particularly the privacy section — you are storing voice recordings of your users.

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.0+ |
| Java | 21 |
| Required | [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) 2.6.20+ for 1.21.1 |

**Both EchoPins and Simple Voice Chat are required on the client and on the server.**

## Installation

Drop Simple Voice Chat and EchoPins into `mods/` on both sides. Start the server once to generate
`config/echopins-server.toml`.

## Known limitations

- **Push-to-talk users must hold their voice chat key as well as the EchoPins key.** Simple Voice
  Chat's public API only exposes microphone audio once it is already transmitting, and there is no
  supported way to start capture from outside. Reaching into its internals would be fragile and a
  privacy problem, so EchoPins does not. The recording HUD explains this and reports whether audio
  is actually arriving.
- Audio is stored unencrypted inside the world save. Server operators can listen to it. This is
  stated plainly in [PRIVACY.md](PRIVACY.md) rather than glossed over — the server has to decrypt
  audio to play it, so encryption would imply a protection that does not exist.
- Simple Voice Chat is the only supported backend in 1.0, though the adapter seam for others
  exists.
- Images in the README and on the mod pages are labelled **UI mockups**, built from the real
  interface geometry and strings. Genuine in-game screenshots have not been captured yet; the shot
  list is in [branding/SCREENSHOT_PLAN.md](branding/SCREENSHOT_PLAN.md).
- The manual multi-client integration matrix in [docs/TESTING.md](docs/TESTING.md) has not been
  executed end to end. The automated build and unit test suite pass.

## Privacy

EchoPins records voice. Recording only happens while you hold the key, only captures your own
voice, is always visibly indicated, and is stored on the server where the operator can access it.
Full detail in [PRIVACY.md](PRIVACY.md).

## Checksums

<!-- The release workflow appends the SHA-256 of the published jar here automatically. -->
