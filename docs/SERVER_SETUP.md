# Server setup

## Before you open it to the public

EchoPins records players' voices and stores those recordings inside your world save. That has
three consequences worth deciding about deliberately:

1. **You can hear everything.** As the operator you can play any pin, including private ones, and
   you can copy the audio files off the server. This is unavoidable — the server has to hold the
   audio in order to serve it.
2. **Your players should know that.** Point them at [PRIVACY.md](../PRIVACY.md), or paste the
   short version into your rules: *"Voice messages are stored on this server. Staff can hear them.
   Don't record anything you wouldn't say in chat."*
3. **You are storing voice recordings of your users.** Depending on where you and your players
   live, that may carry legal obligations. Keeping `defaultExpiryHours` low is the simplest way to
   limit how much you hold at any moment.

## First run

```
mods/
├── voicechat-<loader>-1.20.1-<version>.jar
└── echopins-<loader>-<version>+mc1.20.1.jar
```

Start once, stop, then edit `config/echopins-server.toml` — or `config/echopins-server.json` if
you are running Fabric. Same options, same defaults, different file format.

Confirm in the log:

```
[EchoPins] EchoPins ready: 0 pin(s) loaded, 0 bytes of audio
[EchoPins/AudioStore] Audio store ready at .../world/echopins/audio (0 bytes in use)
[EchoPins/Voice] Voice chat server started; EchoPins playback is available
```

If the third line never appears, Simple Voice Chat is not running — fix that first. EchoPins will
still load, but recording and playback will report "Voice chat isn't connected."

## Recommended starting point for a public server

```toml
[recording]
maxRecordingSeconds = 20
suppressProximityBroadcastWhileRecording = true

[limits]
maxPinsPerPlayer = 24
maxTotalPins = 3000
maxPinsNearby = 8

[expiry]
defaultExpiryHours = 72
allowPermanentPins = false

[rate_limits]
createCooldownSeconds = 20

[storage]
maxTotalAudioStorageBytes = 536870912
```

Loosen from there once you see how it gets used. Tightening later annoys people more than starting
strict does.

## Disk planning

A message costs roughly **2 KB per second** of speech.

| Scenario | Storage |
|---|---|
| One 20-second message | ~40 KB |
| 1,000 messages averaging 10s | ~20 MB |
| `maxTotalPins = 5000` at 30s each | ~300 MB worst case |

`maxTotalAudioStorageBytes` is a hard ceiling checked before each write, so the worst case is
bounded whatever the other settings say.

Audio lives in `<world>/echopins/audio/`. If your backup tooling is size-sensitive, that is the
directory to watch — and the one to *exclude* if you decide voice notes are not worth backing up.

## Backups

`<world>/data/echopins.dat` and `<world>/echopins/` must be backed up **together**. Metadata
without audio produces pins that report a damaged recording; audio without metadata produces
orphans that the sweep deletes.

Copying a world is safe: both parts live inside the world directory, so an ordinary world copy
takes everything.

## Performance

Idle cost is close to nothing. The settings that actually scale with load:

| Setting | Effect |
|---|---|
| `discoveryRadius` | Biggest lever. Halving it roughly quarters the candidate set per player. |
| `maxSyncedPinsPerPlayer` | Hard cap on per-player bandwidth and client draw calls. |
| `syncIntervalTicks` | Raise on a busy server. Recalculation is already skipped when players stand still. |

There is no per-tick scan of all pins. Lookups go through a chunk-based spatial index, and disk
work happens on a dedicated bounded pool, never on the server thread.

Watch it with:

```
/echopins admin stats
```

## Moderation

```
/echopins admin delete <id>              remove one message
/echopins admin purge player <player>    remove everything by one player
```

Deleting removes the audio file too, and stops any in-flight playback of that pin.

To find a message someone reported, stand where they said it was and run `/echopins list` — the
listing includes ids and coordinates.

## Routine maintenance

Automatic by default: expired pins are swept every 60 seconds in batches of 32, and orphan audio
every 30 minutes. The manual equivalents:

```
/echopins admin purge expired
/echopins admin cleanup
```

## Turning it off

```toml
[general]
enabled = false
```

Requests are refused with a clear message and nothing renders. Existing data is untouched, so this
is reversible. To remove the data permanently, stop the server and delete
`<world>/data/echopins.dat` and `<world>/echopins/`.

## Proxies and networks

EchoPins uses ordinary NeoForge payloads and works behind Velocity or BungeeCord like any other
mod. Pins are stored per backend server, since each has its own world.

Simple Voice Chat has its own proxy requirements — follow its documentation for the UDP port.
