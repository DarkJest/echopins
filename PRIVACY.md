# Privacy

EchoPins records your voice. This document explains exactly what that means, in plain terms.

If you only read one section, read [What is recorded](#what-is-recorded).

## What is recorded

Only **your own voice**, and only **while you are holding the EchoPins record key**.

EchoPins never captures:

- other players' voices,
- ambient proximity conversation,
- microphone audio at any time when you are not actively recording an EchoPin,
- anything at all after you disconnect.

Technically: EchoPins receives audio from Simple Voice Chat's `MicrophonePacketEvent` on the
server. A frame is stored **only** if the player who spoke it has an open recording session of
their own. There is no code path that writes a frame from anyone else, and none that keeps a
session alive past a disconnect. If you want to check this yourself, the entire rule lives in
`RecordingService.onMicrophoneFrame`.

## When it is recorded

Recording starts **only** from an explicit action: you press and hold the EchoPins key.

While it is running:

- a recording indicator is on screen with a red dot, the word "Recording", elapsed time and a
  progress bar,
- a short start cue plays, and a short stop cue plays when you finish,
- the indicator cannot be disabled — turning off "recording cues" silences the sounds but the
  visual indicator stays.

Recording ends when you release the key, when you hit the maximum length, when you disconnect,
die, or change dimension, or when the session goes silent for longer than the configured timeout.

There is **no** background recording, **no** hidden recording, and **no** way for another player
or the server to start recording you.

## Are other players hearing me while I record?

By default, **no**. While you are recording an EchoPin, EchoPins cancels the proximity broadcast
of those audio frames, so people standing next to you do not hear the message you are leaving.

This is the server option `suppressProximityBroadcastWhileRecording`, on by default. A server
operator can turn it off; if they do, recording behaves like ordinary talking and nearby players
will hear you. Ask your server admin if you are unsure.

## Where it is stored

On the **server**, inside the world save:

```
<world>/
  data/echopins.dat            pin metadata (no audio)
  echopins/audio/<xx>/<uuid>.epv   the audio itself
```

Audio never leaves the server except as voice chat playback to players who are allowed to hear it.
EchoPins does not upload anything anywhere, contacts no external service, and has no telemetry.

## Who can hear a message

- **Public** pins: anyone on the server who comes close enough.
- **Private** pins: the author, and the specific players the author chose.

Access is decided by player UUID, never by name, and is re-checked on the server every time
someone presses play — not merely when the marker is shown. Hiding a marker on the client is
presentation only and is never relied on as a security boundary.

Server operators can play and delete any pin. They can also read the files directly. See below.

## What the server operator can do

**A server operator has full access to every recording on their server.** This is not an EchoPins
decision — it follows from the audio living inside the world save, and it is equally true of every
chat log, every book you write, and every sign you place.

Specifically, an operator can:

- listen to any pin, including private ones, through the moderation permission,
- delete any pin,
- copy `<world>/echopins/audio/` off the server and listen to the files elsewhere.

**Do not record anything you would not want the server operator to hear.** If you do not trust the
operator of a server, do not leave voice messages on it.

Operators deliberately **cannot** edit someone else's pin — no rewriting a caption or flipping a
private pin to public. Deleting is the honest moderation tool, and it is logged.

## How to delete your messages

In game:

- Open the inbox (default `N`), go to **Mine**, and press **Delete** on any entry.
- Or run `/echopins mine` to list them, then `/echopins delete <id>`.

Deleting removes the metadata **and** the audio file. It is immediate and cannot be undone.

Pins also delete themselves when they expire — after one week by default.

If you want everything you have ever recorded removed, ask an operator to run:

```
/echopins admin purge player <your name>
```

## How an administrator clears voice storage

```
/echopins admin purge expired      remove everything past its expiry
/echopins admin purge player <p>   remove one player's pins and their audio
/echopins admin cleanup            delete audio files no pin references
```

To wipe **everything**, stop the server and delete both:

```
<world>/data/echopins.dat
<world>/echopins/
```

EchoPins will start again from empty. Do this with the server stopped, and take a backup first.

## Data retention

| | |
|---|---|
| Default pin lifetime | 168 hours (one week), configurable |
| Permanent pins | allowed by default, can be disabled server-side |
| Unconfirmed recordings | deleted after 2 minutes |
| Orphaned audio files | swept every 30 minutes by default |
| Read state | dropped when the pin it refers to is deleted |

## Children and shared servers

EchoPins has no age gate and no way to know who is speaking beyond their Minecraft account. If you
run a server for a young audience, consider:

- setting `maxRecordingSeconds` low,
- setting `defaultExpiryHours` low and `allowPermanentPins = false`,
- or setting `enabled = false` and not using the mod at all.

## What EchoPins does not do

No transcription. No speech-to-text. No cloud storage. No external service of any kind. No
analytics, no telemetry, no crash reporting to us. No Discord integration. No account linking.

## Questions

Open an issue at https://github.com/DarkJest/echopins/issues, or follow
[SECURITY.md](SECURITY.md) if the issue is a vulnerability rather than a question.
