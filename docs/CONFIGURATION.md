# Configuration

Two files, generated on first run. The format follows the loader:

| Scope | NeoForge | Fabric |
|---|---|---|
| Limits, security, storage. Authoritative. | `config/echopins-server.toml` | `config/echopins-server.json` |
| Rendering, HUD, accessibility. Per player. | `config/echopins-client.toml` | `config/echopins-client.json` |

**The options, their names, their defaults and their permitted ranges are identical on both
loaders** — they come from one shared table in the source, so the two builds cannot drift. Only the
file syntax differs. The tables below are written as TOML; the Fabric file is flat JSON with the
same key names and no section headers, so `maxRecordingSeconds` under `[recording]` is simply
`"maxRecordingSeconds": 30` at the top level.

An unknown key is ignored and a missing one falls back to its default, so a file written by an
older or newer version still loads. Out-of-range values are clamped rather than rejected.

### When edits take effect

| | NeoForge | Fabric |
|---|---|---|
| Server file | Read live; edits apply immediately | Read at start-up; `/echopins admin reload` re-reads it |
| Client file | Read live | Read at start-up; restart the game |

Run `/echopins admin reload` after editing the server file on **either** loader: on Fabric it
re-reads the file, and on both it re-pushes the derived values clients cache, so nobody is left
looking at a stale limit in their UI.

**A client can never raise a server limit.** The client file only makes a player's own view more
restrictive.

---

## Server configuration

### `[general]`

| Option | Default | Range | Notes |
|---|---|---|---|
| `enabled` | `true` | | Master switch. When false, no requests are accepted and nothing renders. |
| `operatorPermissionLevel` | `2` | 0–4 | Vanilla permission level for admin commands. |
| `operatorBypassLimits` | `true` | | Whether operators skip pin counts, cooldowns and density checks. |

### `[recording]`

| Option | Default | Range | Notes |
|---|---|---|---|
| `maxRecordingSeconds` | `30` | 1–600 | Longest message. Also caps stored frames. |
| `minRecordingMillis` | `700` | 100–10000 | Stops an accidental key tap becoming a pin. |
| `recordingSessionTimeoutSeconds` | `60` | 5–900 | Cancel after this much **silence**. A player holding push-to-talk and thinking still sends frames, so this only fires when a client has genuinely stopped sending. |
| `suppressProximityBroadcastWhileRecording` | `true` | | Keeps the message you are recording from also being heard by bystanders. Turning this off is a privacy-relevant change — tell your players. |

### `[limits]`

| Option | Default | Range | Notes |
|---|---|---|---|
| `maxPinsPerPlayer` | `64` | 1–100000 | |
| `maxTotalPins` | `5000` | 1–1000000 | |
| `maxPinsNearby` | `16` | 1–512 | Pins allowed within the interaction radius of a new one. Stops one spot being carpeted. |
| `maxCaptionLength` | `96` | 0–256 | `0` disables captions entirely. |
| `maxPrivateRecipients` | `16` | 1–64 | |

### `[discovery]`

| Option | Default | Range | Notes |
|---|---|---|---|
| `discoveryRadius` | `56.0` | 8–256 | Where markers start appearing. The single biggest lever on network cost. |
| `interactionRadius` | `6.0` | 1–64 | How close you must be to play or create. |
| `maxCreationDistance` | `8.0` | 1–64 | Server-validated. A client claiming a further anchor is rejected. |
| `playbackAudioDistance` | `16.0` | 2–128 | How far playback carries. |
| `maxSyncedPinsPerPlayer` | `64` | 8–512 | Hard cap on what one player is told about. Bounds both bandwidth and client rendering. |
| `syncIntervalTicks` | `20` | 5–200 | Minimum ticks between subscription recalculations. |

### `[expiry]`

| Option | Default | Range | Notes |
|---|---|---|---|
| `defaultExpiryHours` | `168` | 0–8760 | One week. `0` means pins do not expire unless a player chooses a short one. |
| `allowPermanentPins` | `true` | | When false, a request for a permanent pin falls back to the default lifetime rather than throwing the recording away. |

The "short" option a player can pick is one eighth of the default, floored at one hour.

### `[rate_limits]`

| Option | Default | Range | Notes |
|---|---|---|---|
| `createCooldownSeconds` | `5` | 0–3600 | `0` disables. |
| `playbackCooldownMillis` | `400` | 0–60000 | `0` disables. |
| `maxConcurrentPlaybacksPerPlayer` | `3` | 1–16 | |
| `requestBurstCapacity` | `30` | 1–500 | General per-player limiter, applied to **every** EchoPins packet before any work is done. |
| `requestRefillPerSecond` | `10.0` | 0.1–200 | |

### `[storage]`

| Option | Default | Range | Notes |
|---|---|---|---|
| `maxAudioBytesPerPin` | `262144` | 1 KiB – 8 MiB | 256 KiB is far more than a 30-second Opus message needs. |
| `maxTotalAudioStorageBytes` | `1073741824` | 1 MiB – 1 TiB | 1 GiB. Checked **before** a recording is written. |

### `[cleanup]`

| Option | Default | Range | Notes |
|---|---|---|---|
| `expiredPinCleanup` | `true` | | |
| `expiredPinCleanupIntervalSeconds` | `60` | 5–3600 | |
| `expiredPinCleanupBatch` | `32` | 1–1000 | Keeps cleanup incremental. |
| `orphanCleanup` | `true` | | Deletes audio no pin references. |
| `orphanCleanupIntervalMinutes` | `30` | 1–1440 | Walks the audio directory, so it runs far less often than the expiry sweep. |

---

## Client configuration

### `[markers]`

| Option | Default | Notes |
|---|---|---|
| `showMarkers` | `true` | |
| `markerOpacity` | `0.85` | 0.05–1.0 |
| `markerScale` | `1.0` | 0.25–2.5 |
| `markerRenderDistance` | `48.0` | Always clamped to the server's `discoveryRadius`. Raising it cannot reveal pins the server did not send. |
| `maxRenderedMarkers` | `32` | Nearest first. |
| `occlusionMode` | `SHOW_THROUGH_WALLS_NEARBY` | `ALWAYS_OCCLUDE`, `SHOW_THROUGH_WALLS_NEARBY`, `NEVER_OCCLUDE`. The default only shows a pin through a wall once you are close enough to interact with it, so markers never act as an x-ray tool. |
| `showLabels` | `true` | The author/age/length label when you look at a pin. |

### `[hud]`

| Option | Default | Notes |
|---|---|---|
| `hudPosition` | `TOP_CENTER` | Six anchors. |
| `hudOffsetX` / `hudOffsetY` | `0` | -400–400. Clamped so a saved offset from a larger window cannot push the panel off-screen. |

### `[playback]`

| Option | Default | Notes |
|---|---|---|
| `autoPlayNearby` | `false` | Off deliberately: unexpected audio is disruptive, and playing a message should be the listener's choice. |
| `notificationSounds` | `true` | Soft cue when a new pin comes into range. |
| `recordingCues` | `true` | Start/stop cues. The **visual** indicator stays regardless — recording state is never unsignalled. |

### `[accessibility]`

| Option | Default | Notes |
|---|---|---|
| `reduceMotion` | `false` | Disables the indicator pulse and the unread-marker bob. |
| `showCaptions` | `true` | Shows pin captions as text. The accessible alternative to the audio. |
| `highContrastRecordingIndicator` | `false` | Adds an outline to the recording panel. |

---

## Tuning recipes

**Small private server, keep everything**

```toml
defaultExpiryHours = 0
allowPermanentPins = true
maxPinsPerPlayer = 256
maxTotalPins = 20000
```

**Large public server, keep it tidy**

```toml
defaultExpiryHours = 72
allowPermanentPins = false
maxPinsPerPlayer = 16
maxPinsNearby = 6
discoveryRadius = 40.0
maxSyncedPinsPerPlayer = 32
createCooldownSeconds = 30
maxTotalAudioStorageBytes = 268435456
```

**Minimum footprint**

```toml
maxRecordingSeconds = 10
maxAudioBytesPerPin = 65536
defaultExpiryHours = 24
```

**Turn it off without removing the mod**

```toml
enabled = false
```
