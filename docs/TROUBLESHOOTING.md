# Troubleshooting

## "Voice chat isn't connected."

The most common problem, and almost never EchoPins itself.

1. Is Simple Voice Chat installed on **both** the client and the server?
2. Is its UDP port open on the server?
3. Does the voice chat icon show as connected?
4. Is your microphone selected in *Options → Voice Chat*?

EchoPins refuses to open a recording session unless voice chat reports the player as connected, so
this message means the problem is upstream of EchoPins.

## I hold the key but nothing is recorded

Look at the HUD. If it says **"Hold your voice chat key"**, no audio is arriving.

**If you use push-to-talk, that is the answer:** hold the EchoPins key **and** your normal voice
chat push-to-talk key at the same time. EchoPins cannot switch your microphone on — see
[the README](../README.md#voice-recording-behaviour--please-read) for why.

Otherwise, check your microphone works in ordinary proximity chat first. If nobody can hear you
there, EchoPins cannot hear you either.

## "That recording was too short."

Shorter than `minRecordingMillis` (0.7s by default) of **actual audio**, not of key-holding. In
push-to-talk, holding the EchoPins key without also transmitting captures nothing at all.

## The confirmation screen never appears

Audio is stored before you are asked to confirm, so this means the write failed. Check the server
log for `Could not store recording`. Usual causes: the disk is full, the world directory is
read-only, or `maxTotalAudioStorageBytes` has been reached.

## I can't see a friend's pin

In order of likelihood:

1. **Too far away.** Markers appear within `discoveryRadius` — 56 blocks by default.
2. **It is private and you are not a recipient.** The server never sends it, so there is nothing
   for the client to reveal.
3. **Wrong dimension.** Pins only appear in the dimension they were left in.
4. **It expired.** One week by default.
5. **`showMarkers` is off** in your own client config.

## "This recording is damaged and can't be played."

The `.epv` container failed validation — bad checksum, truncated, or missing. Causes:

- The world was restored from a backup containing `echopins.dat` but not `echopins/`.
- The disk filled mid-write or the machine lost power. Writes are atomic, so this normally leaves
  a harmless `.tmp` file instead, but a failing disk can still corrupt a completed file.
- Someone edited the audio directory by hand.

The metadata is intact, so the pin still shows. Remove it:

```
/echopins admin delete <id>
```

Then `/echopins admin cleanup` to reclaim any orphaned files.

## Markers flicker or disappear behind blocks

That is `occlusionMode`. The default only shows a pin through a wall once you are close enough to
interact with it. Set `occlusionMode = "NEVER_OCCLUDE"` in `config/echopins-client.toml` if you
prefer them always visible.

## The recording indicator is in the way

In `config/echopins-client.toml`:

```toml
hudPosition = "TOP_LEFT"
hudOffsetY = 20
```

It cannot be disabled. An indicator that can be hidden is a privacy problem, not a preference.

## "You can't create a pin here."

One of:

- You are further than `maxCreationDistance` (8 blocks) from the block you targeted.
- You targeted air.
- The position is outside world bounds.
- You changed dimension mid-recording, which cancels it.

Look directly at a solid block from close range, or look at open air to drop a free-floating pin.

## "Wait N more second(s)…"

`createCooldownSeconds`, or the general per-player request limiter if a lot of requests are being
sent quickly. Both live in `config/echopins-server.toml`.

## Pins vanished after a restart

Check the log for how many loaded:

```
[EchoPins/Persistence] Loaded 42 EchoPin(s) and 130 read mark(s)
```

If it says `skipped N unreadable entr(ies)`, those failed validation and were skipped so the rest
could load; the reason is logged just above.

If it loaded 0 and you expected more, you are almost certainly pointing at a different world
directory, or `echopins.dat` was restored without `echopins/`.

## Server log messages

| Message | Meaning |
|---|---|
| `Removed N incomplete audio file(s) left by a previous shutdown` | Normal after a crash. Harmless. |
| `Orphan sweep removed N unreferenced audio file(s)` | Normal housekeeping. |
| `Skipping a pin that cannot be migrated` | Written by a newer EchoPins than this one. |
| `EchoPins IO queue is saturated` | Disk cannot keep up. Investigate storage. |
| `Could not register the EchoPins volume category` | Cosmetic. Playback works; the separate volume slider is missing. |
| `Rate limited a request from …` | A client is sending faster than the configured limit. |

## Reporting a bug

Include:

- EchoPins, Simple Voice Chat, NeoForge and Minecraft versions
- Client or server, or both
- `logs/latest.log` from the side that misbehaved
- What you did, what happened, what you expected

https://github.com/DarkJest/echopins/issues
