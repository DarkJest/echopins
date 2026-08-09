# Testing

## Automated

```bash
./gradlew build
```

Runs compilation and the JUnit 5 suite. The suite covers the pure-Java layers, which is where the
logic that actually matters lives:

| Area | Covered |
|---|---|
| EPV container | round trip, empty recording, bad magic, unknown version, checksum detection, truncation **at every byte offset**, trailing garbage, implausible frame count, overlong and zero-length frames, builder limits |
| Audio store | store/load, missing id, idempotent delete, byte accounting across restart, damaged and foreign files, stale `.tmp` sweep, path containment |
| Access control | public, private, recipient, author, operator moderation vs editing, recipient normalisation, corrupt visibility failing closed |
| Rate limiting | burst, refill, per-key isolation, wait hints, backwards clock, cooldown gaps, pruning |
| Repository & index | radius filtering including Y, dimension isolation, negative coordinates, index cleanup on remove and re-save, author index, bounded expiry sweep, 5,000-pin query |
| Expiry policy | default, short, permanent, permanent-denied fallback, zero-default, live config re-read |
| Value objects | dimension parsing, traversal rejection, caption sanitisation and truncation, author names, anchor geometry, coordinate bounds, chunk maths |

**Not covered automatically:** anything requiring a running Minecraft server or a live Simple Voice
Chat connection. That is what the matrix below is for.

## Manual integration test matrix

Legend: ☐ untested · ✅ pass · ❌ fail

### Client / server topologies

| Scenario | Status |
|---|---|
| Single player | ☐ |
| Dedicated NeoForge server, 1 client | ☐ |
| Dedicated server, 2 clients | ☐ |
| Dedicated server, 3+ clients | ☐ |
| Client without EchoPins joining an EchoPins server | ☐ |
| Client without Simple Voice Chat | ☐ |

Expected for the last two: a clear protocol-mismatch / missing-dependency screen, never a crash.

### Recording

| Scenario | Expected | Status |
|---|---|---|
| Voice activation mode | Records normally | ☐ |
| Push-to-talk mode | Records while both keys held; HUD shows the hint | ☐ |
| Microphone muted | "Voice chat isn't connected" or nothing recorded | ☐ |
| Voice chat disconnected | Refused with a clear message | ☐ |
| Cancel with Escape | Audio discarded, no pin | ☐ |
| Hold past `maxRecordingSeconds` | Auto-stops, keeps what was said | ☐ |
| Tap the key briefly | "Recording too short", no pin | ☐ |
| Disconnect while recording | Session dropped, no orphan pin | ☐ |
| Die while recording | Session cancelled | ☐ |
| Change dimension while recording | Session cancelled with a message | ☐ |
| Server stops while recording | Clean shutdown, unconfirmed audio deleted | ☐ |
| Two players record simultaneously | Each captures only their own voice | ☐ |
| Bystander during recording | Does **not** hear the recorded message (default config) | ☐ |

### Playback

| Scenario | Expected | Status |
|---|---|---|
| Same dimension, in range | Plays, sounds locational | ☐ |
| Different dimension | Marker absent, playback refused | ☐ |
| Out of interaction range | Refused | ☐ |
| Private pin, recipient | Plays | ☐ |
| Private pin, non-recipient | Marker never sent; direct request refused | ☐ |
| Private pin, operator | Plays | ☐ |
| Deleted mid-playback | Playback stops | ☐ |
| Expired pin | Gone; playback refused | ☐ |
| Corrupted audio file | "Recording is damaged", server stays up | ☐ |
| Disconnect during playback | Cleaned up server-side | ☐ |
| Simultaneous playbacks up to the cap | All play | ☐ |
| One past the cap | Refused | ☐ |
| Bystander who lacks access | Hears nothing while another player plays it | ☐ |

The last row is the important one: the audience filter is evaluated per listener inside the voice
system, so a private message must be inaudible to a non-recipient standing right next to the
person playing it.

### Persistence

| Scenario | Expected | Status |
|---|---|---|
| Restart the server | Pins survive with audio | ☐ |
| Copy the world elsewhere | Pins survive | ☐ |
| Delete an audio file by hand | "Damaged"; server stays up | ☐ |
| Add a stray `.epv` by hand | Removed by `admin cleanup` | ☐ |
| Leave a `.tmp` file | Removed at next startup | ☐ |
| Corrupt one pin's NBT | That pin skipped and logged; others load | ☐ |
| Kill the server mid-recording | No half-written `.epv` under a real name | ☐ |

### Limits and abuse

| Scenario | Expected | Status |
|---|---|---|
| Exceed `maxPinsPerPlayer` | Refused with a clear message | ☐ |
| Exceed `maxPinsNearby` | Refused | ☐ |
| Spam create | Cooldown message | ☐ |
| Spam packets | Rate limited, server unaffected | ☐ |
| Fill `maxTotalAudioStorageBytes` | Refused before writing | ☐ |
| Operator with `operatorBypassLimits` | Bypasses counts and cooldowns | ☐ |

### Interface

| Scenario | Expected | Status |
|---|---|---|
| GUI scale 1 through 4 | HUD and screens laid out correctly | ☐ |
| 16:9, 16:10, ultrawide, vertical split | No clipping or off-screen panels | ☐ |
| `reduceMotion = true` | No pulsing or bobbing | ☐ |
| `highContrastRecordingIndicator = true` | Outline visible | ☐ |
| Russian locale | No overflow; the HUD hint fits | ☐ |
| 50+ pins in view | Capped at `maxRenderedMarkers`, no frame drop | ☐ |

### Modpack compatibility

| Scenario | Status |
|---|---|
| Alongside a large NeoForge 1.21.1 pack (100+ mods) | ☐ |
| Alongside another Simple Voice Chat plugin | ☐ |
| With shaders (Iris/Oculus) | ☐ |
| With a minimap mod | ☐ |

The shader row matters most: marker rendering hooks `RenderLevelStageEvent.AFTER_PARTICLES` and
should be verified against at least one shader pack.

## Performance checks

With ~5,000 pins on a server:

| Check | Target |
|---|---|
| Idle server tick time | No measurable change vs. without EchoPins |
| Player running through a dense area | No sustained spike from sync |
| `/echopins admin stats` | Returns instantly |
| Client FPS with 32 markers visible | No measurable change |

The 5,000-pin case is covered as a unit test for query correctness; the timing numbers above still
need to be measured on real hardware.

## How to record results

Copy this file into a release checklist, replace ☐ with ✅ or ❌, and attach it to the release PR.
Do not mark a row ✅ that was not actually run — an untested row is more useful than a wrong one.
