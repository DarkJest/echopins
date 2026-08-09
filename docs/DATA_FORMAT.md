# Data format

EchoPins stores two things: pin metadata in the world's NBT save data, and audio in its own
binary container.

## Why audio is not in NBT

Voice data would bloat a single `SavedData` blob that gets rewritten on every autosave. Keeping
audio in separate files means `echopins.dat` stays small — a few hundred bytes per pin — and the
audio is written once, atomically, and never touched again until it is deleted.

## Layout on disk

```
<world>/
├── data/
│   └── echopins.dat                 pin metadata + read state (vanilla SavedData, gzipped NBT)
└── echopins/
    └── audio/
        ├── 0a/0a3f...c1.epv
        ├── 7b/7b91...4d.epv
        └── ...
```

Files are named only from a random `UUID`, and are sharded by the first two hex characters so no
single directory accumulates thousands of entries. Paths are built with `java.nio.file.Path` and
never with hard-coded separators.

## The EPV container

**EchoPin Voice**, `.epv`. A minimal versioned container for Opus frames exactly as Simple Voice
Chat produced them.

All integers are **big-endian**.

```
offset  size  field            notes
------  ----  ---------------  ---------------------------------------------
0       4     magic            'E' 'P' 'V' 0x1A
4       1     formatVersion    currently 1
5       1     codec            1 = Opus
6       4     sampleRate       48000
10      1     channels         1
11      1     frameDuration    milliseconds per frame, 20
12      4     frameCount       number of frames that follow
16      ...   frames           frameCount x { uint16 length; length bytes }
end-4   4     crc32            CRC-32 of every preceding byte in the file
```

The `0x1A` in the magic is the historical "stop displaying" byte, which keeps a stray `cat` or
`type` of the file from spraying terminal control codes.

### Size

```
size = 16 + frameCount * 2 + totalPayloadBytes + 4
```

A 10-second message is 500 frames, so roughly `16 + 1000 + ~40,000 + 4` ≈ 40 KB at a typical Opus
bitrate. Ten seconds of raw 48 kHz 16-bit PCM would be 960 KB — about 24x larger — which is why
raw PCM is never persisted.

### Validation

Reading is total and defensive. Every one of these is rejected:

| Condition | Result |
|---|---|
| Buffer shorter than header + CRC | rejected before any field is read |
| Wrong magic | rejected |
| Unknown `formatVersion` | rejected — never guessed at |
| Unknown `codec` | rejected |
| Audio shape other than 48 kHz / 1 channel / 20 ms | rejected |
| `frameCount` outside `0..30000` | rejected before allocating the frame list |
| Declared `frameCount` larger than the file could hold | rejected before allocating |
| Frame length `0` or above 4096 | rejected |
| A frame that would run into or past the CRC | rejected |
| Trailing bytes after the CRC | rejected |
| CRC mismatch | rejected as damaged |

**No declared length is ever used to size an allocation before it has been range-checked against
both the format ceilings and the bytes actually remaining.** A corrupt or hostile file can cause a
rejected parse; it cannot cause a large allocation, an infinite loop, or an out-of-bounds read.

The test suite truncates a valid container at **every single byte offset** and asserts that all of
them are rejected rather than partially decoded.

Hard ceilings, independent of server config:

| Constant | Value |
|---|---|
| `MAX_FRAME_COUNT` | 30,000 (10 minutes) |
| `MAX_CONTAINER_BYTES` | 8 MiB |
| `MAX_FRAME_BYTES` | 4,096 |

### What EPV deliberately is not

- Not Java serialization. There is no `ObjectInputStream` anywhere in EchoPins.
- Not a general media container. It holds one mono Opus stream and nothing else.
- Not seekable or indexed. Messages are seconds long; playback is sequential.
- Not encrypted. Audio sits in the world save, readable by the server operator — see
  [PRIVACY.md](../PRIVACY.md). Encrypting it would imply a protection that does not exist, since
  the server has to decrypt it to play it anyway.

## Durability

Writes are atomic from a reader's point of view:

1. Serialise the whole container to a byte array in memory.
2. Write it to `<id>.epv.tmp`.
3. `FileChannel.force(true)` — data and metadata to disk.
4. `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`, falling back to a plain replace if the
   filesystem cannot do atomic moves.

So an id either resolves to a complete, checksum-valid container or does not resolve at all. A
crash between steps 3 and 4 leaves a `.tmp` file, which is swept on the next startup and can never
be mistaken for audio because the listing only accepts `<uuid>.epv`.

Pin metadata is only created **after** the audio write succeeds.

## Pin metadata NBT

`echopins.dat` holds:

```
dataVersion : int          schema version of the file
pins        : list of compound
readState   : list of compound { player: uuid, read: list of uuid }
```

Each pin compound:

| Key | Type | Notes |
|---|---|---|
| `v` | int | schema version of **this pin** |
| `id`, `author`, `audioId` | uuid | |
| `authorName` | string | display only, never used for access |
| `anchorKind` | byte | 0 block, 1 position |
| `dim` | string | `namespace:path` |
| `bx`,`by`,`bz`,`face` | int/byte | block anchors |
| `x`,`y`,`z` | double | position anchors |
| `created`, `expires` | long | epoch millis; `expires = 0` means permanent |
| `vis` | byte | 0 public, 1 private |
| `to` | list of int-array | recipient UUIDs, omitted when empty |
| `caption` | string | optional |
| `audioBytes` | long | for storage accounting |
| `frames` | int | determines duration |

### Versioning

Every pin carries its **own** `v`. Storing the schema version per pin rather than only once per
file means a future migration can be applied lazily and a single unreadable pin can be skipped
without condemning the rest of the world's data.

`DataMigrationRegistry` applies the chain from a pin's stored version up to `DATA_VERSION`. It
rejects gaps in the chain at registration time rather than when someone's world fails to load, and
refuses to read data written by a newer EchoPins instead of silently dropping fields it does not
understand.

There are no migrations yet — v1 is the first released schema. The framework exists now because
adding it later would mean the first release's worlds have no version marker to migrate *from*.

### Corruption handling

Decoding a pin is total: any malformed value — a bad dimension id, an out-of-range coordinate, a
negative frame count — causes that one pin to be skipped and logged at `WARN`. One corrupt entry
never stops a world from loading.

A visibility byte that cannot be parsed falls back to **private**, not public. Corruption must not
be able to expose a message.
