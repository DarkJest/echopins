# Security model

## What is being defended

EchoPins accepts voice data and world coordinates from clients and writes both to the server's
disk. The assets worth protecting are:

1. **Private recordings** — a message must reach only the people the author chose.
2. **Server disk** — clients must not be able to fill it.
3. **Server availability** — a malicious client must not be able to stall or crash the server.
4. **The filesystem** — no client-supplied value may ever influence a path.

## Trust boundaries

```
UNTRUSTED  client packets, block targets, captions, recipient lists, pin ids
           ↓  every field validated at the boundary
TRUSTED    server: player identity from the connection, dimension and position from the world,
           timestamps from the server clock
           ↓
TRUSTED    world save (operator already has full access)
```

The core rule: **nothing that identifies who you are, where you are, or when something happened
ever comes from the client.** Payloads carry no author, no dimension and no timestamp. The one
piece of client-supplied world state — the block a player is looking at — is a *hint* that is
re-validated against the player's real level and position.

## Threats and mitigations

### Malicious client forges the author

Serverbound payloads have no author field. `EchoPinsNetwork.server(...)` resolves the sender from
`context.player()` — the connection — and that is the only place identity is established, so no
handler can be written that trusts a client-supplied identity.

### Client claims an anchor in another dimension or 100,000 blocks away

`AnchorResolver` takes the dimension from `player.level()`, re-measures the distance from the
player's actual eye position against `maxCreationDistance`, rejects targets outside world bounds,
and rejects targets that are air. Coordinates are additionally range-checked by the domain value
objects (`WorldPos`, `BlockAnchor`), so there is exactly one definition of a valid anchor and it
cannot be bypassed by reaching a different code path.

### Playing a private pin you were not given

Checked in three independent places:

1. `PinSyncService` never sends a pin the viewer cannot discover, so the client is not told it
   exists.
2. `PlaybackService.requestPlayback` re-runs `AccessPolicy.canPlay` at the moment of playback —
   visibility or operator status may have changed since the marker was shown.
3. The audio channel's own filter runs **per listener inside the voice system**, so audio is only
   delivered to players who pass the check at delivery time.

Hiding a marker on the client is presentation only and is never treated as a security boundary.

### Enumerating private pins

Pin ids are random UUIDs and are never handed out for pins the viewer cannot discover. A guessed
id fails the ACL check. `PinSummary` deliberately omits the recipient list, so a client can learn
that a pin is private but never who else can hear it — the client cannot be used to map out
another player's contacts.

### Deleting someone else's pin

`PinService.delete` runs `AccessPolicy.canDelete` before touching the repository. Operators may
delete anything (moderation); operators deliberately **cannot** edit anything, because silently
rewriting a caption or flipping a private pin to public is a different kind of power from removing
abusive content.

### Path traversal

There is no API in `AudioStore` that accepts a path. Addressing is by `UUID` only.

Two independent layers:

1. Filenames are built solely from `UUID.toString()`, a fixed-shape string of hex and dashes that
   cannot contain a separator, a dot segment or a drive letter.
2. The resolved path is normalised and re-checked with `startsWith(root)`, and a failure throws.

`DimensionId` additionally rejects traversal-shaped segments. Minecraft's own path rules permit
`.` and `/`, which means `../../etc/passwd` is a *syntactically valid* `ResourceLocation` path —
so `.` and `..` segments are rejected explicitly rather than assumed harmless. A regression test
covers this.

### Oversized or malformed packets

Every collection read is bounded by an explicit cap **and** cross-checked against the bytes
actually remaining, so a length prefix that is within the cap but still a lie cannot pre-allocate
a large list. Strings use `readUtf(max)`. Anchors are constructed through the domain's validating
constructors. A violation raises `MalformedPayloadException` at the decode stage.

Wire-level ceilings, independent of config: 512 summaries per packet, 64 recipients, 256 characters
of caption.

### Packet spam

Every request passes through a per-player token bucket (`requestBurstCapacity` /
`requestRefillPerSecond`) before any work is done. Creating and playing have their own cooldowns
on top. Limiter state is dropped on disconnect and pruned for idle players.

### Disk exhaustion

| Layer | Control |
|---|---|
| Per recording | `maxRecordingSeconds`, `maxAudioBytesPerPin`, and a hard 8 MiB container ceiling |
| Per player | `maxPinsPerPlayer` |
| Per server | `maxTotalPins`, `maxTotalAudioStorageBytes` |
| Per location | `maxPinsNearby` |
| Over time | expiry (one week by default) plus the orphan sweep |

The storage total is checked **before** a recording is written, not after.

### Unlimited recording sessions

One session per player, enforced by a keyed map. A second `BeginRecording` is refused. Sessions
end on disconnect, death, dimension change, hitting the maximum length, or the silence timeout.
Unconfirmed recordings are deleted after two minutes.

### Corrupt audio crashing the server

`EpvReader` validates everything before allocating (see [DATA_FORMAT.md](DATA_FORMAT.md)) and a
damaged file surfaces as `EpvFormatException`, which becomes a localized "recording is damaged"
message for the player and a `WARN` in the log. The truncation test covers every byte offset.

### Race between delete and play

Audio is loaded on the IO pool, then playback starts back on the server thread — and the pin's
existence is re-checked at that point. If it was deleted in between, the player gets
`PIN_NOT_FOUND` instead of hearing a deleted message. Deleting also actively stops any in-flight
playback of that pin. Deletion is idempotent.

### Disconnect during IO

Metadata is removed before audio. A crash in between leaves an orphan file, which the sweep
collects. The reverse order could leave a pin whose audio is missing — a broken message, which is
worse than a temporarily wasted file. Temporary `.tmp` files are swept at startup and can never be
listed as audio.

### Symlinks

EchoPins creates only regular files and shard directories under its own root, and never follows a
client-supplied path because none exists. An operator who deliberately symlinks the audio
directory elsewhere is exercising their own authority over their own filesystem; EchoPins does not
try to defeat that.

## Privacy as a security property

Covered fully in [PRIVACY.md](../PRIVACY.md). The enforcement points:

- A microphone frame is stored only if the speaker has an open session of their own, so EchoPins
  cannot record player A into player B's message.
- No session survives a disconnect.
- The recording indicator cannot be turned off.
- By default the recorded audio is not simultaneously broadcast to bystanders.

## Deliberate non-goals

- **No encryption at rest.** The server has to decrypt audio to play it, so encryption would imply
  a protection against the operator that does not exist. Saying so plainly is more honest than
  offering theatre.
- **No protection from the server operator.** They own the world files. This is stated directly in
  the privacy document rather than glossed over.
- **No anti-cheat.** EchoPins assumes clients are hostile and validates accordingly; it makes no
  attempt to detect modified clients.

## Reporting

See [SECURITY.md](../SECURITY.md). Please do not open a public issue for a vulnerability.
