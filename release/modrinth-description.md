# EchoPins

## Leave a message where it matters.

Your friend logs off. Three hours later you walk into the base and find a chest full of iron, a
half-built machine, and no idea what any of it is for.

EchoPins fixes that. Record a short voice message, leave it stuck to the chest, and whoever walks
up next hears it — whether or not you are online.

---

## What it is

**World-anchored asynchronous voice messaging.**

Not a voice chat replacement. Not a waypoint mod. Not Discord inside Minecraft.

It does one thing: it lets you attach a short voice note to a place, and lets your friends find it
later.

```
Near a chest    "Don't take the iron yet, it's for the reactor."
At a portal     "This one comes out by the fortress."
Down a mine     "I went left. There's a spawner on the right."
By a machine    "Don't switch this line on, it's not wired up."
```

Things you would have said out loud if you had both been online — except you weren't.

---

## Features

- **Anchored voice notes** — attach a message to a block face, or drop one in mid-air.
- **Works offline** — the author does not need to be online for you to hear them.
- **Quiet by design** — a small marker fades in as you approach, and nothing else. No giant HUD, no
  beacon beams, no visual clutter.
- **Public or private** — choose exactly who can hear a message. Private pins are enforced
  server-side, per listener.
- **Captions** — optional text alongside the audio, which doubles as an accessibility fallback.
- **Its own volume slider** — a dedicated Simple Voice Chat volume category, so EchoPins can be
  turned down separately from proximity chat.
- **Expiry** — messages clean themselves up, so world saves do not grow forever.
- **Inbox** — Nearby, Mine, Private and Unread, with unheard messages marked.

---

## How it works

1. Look at the place that matters.
2. Hold the **Create EchoPin** key (default `B`).
3. Speak. Release the key.
4. Choose public or private, add a caption if you like, and save.

Your friends see a small marker when they get close, and press one key to listen.

The whole interaction takes a few seconds. No commands, no menus, no setup.

---

## Playing with friends

- **Base building** — label what a machine does before you log off for the night.
- **Exploring** — leave a note at a fork, a portal, or a dungeon you did not have time to clear.
- **Shared storage** — say what a chest is for, so nobody empties it into a furnace.
- **Handovers** — "I got to here, this is what's left" at the end of a session.
- **Private notes** — coordinates you would rather not put in public chat.

It is most useful on a server where people play at different times. That is what it was built for.

---

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.0+ |
| Java | 21 |
| **Required** | [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) for 1.21.1 (2.6.20+) |

**EchoPins must be installed on both the client and the server.** So must Simple Voice Chat.

---

## Installation

**Client:** put Simple Voice Chat and EchoPins in `mods/`. Launch. Rebind keys in
*Options → Controls → EchoPins* if you like.

**Server:** put both jars in `mods/`, start once to generate `config/echopins-server.toml`, then
read the server setup guide before opening it to the public.

---

## Please read: how recording works

**EchoPins cannot switch your microphone on.** Simple Voice Chat's public API exposes microphone
audio only once it is already transmitting, and there is no supported way to start capture from
outside it. Reaching into its internals to force that would be fragile and, more importantly, a
privacy problem.

In practice:

- **Voice activation:** works exactly as you would expect. Hold the EchoPins key and talk.
- **Push-to-talk:** hold the EchoPins key **and** your normal voice chat push-to-talk key at the
  same time. The recording indicator says so, and tells you when no audio is arriving.

While you record an EchoPin, your voice is **not** also broadcast to players standing next to you.
Recording a note is not the same as talking to whoever happens to be nearby.

---

## For server admins

Defaults are chosen so an untouched install cannot be used to fill your disk: pins expire after a
week, storage is capped at 1 GiB, and both creating and playing are rate limited.

| Option | Default |
|---|---|
| `maxRecordingSeconds` | 30 |
| `defaultExpiryHours` | 168 (one week) |
| `maxPinsPerPlayer` | 64 |
| `maxTotalPins` | 5000 |
| `discoveryRadius` | 56 blocks |
| `maxTotalAudioStorageBytes` | 1 GiB |

32 server options in total, every one range-validated.

**Performance:** pins are indexed by chunk, each player is only told about pins near them, and all
disk work happens off the server thread. There is no per-tick scan of all pins. Idle cost is close
to nothing.

**Moderation:**

```
/echopins admin stats
/echopins admin delete <id>
/echopins admin purge player <player>
/echopins admin purge expired
/echopins admin cleanup
```

---

## Privacy

EchoPins records your voice, so this is spelled out rather than buried:

- Recording happens **only** while you hold the key. No background capture, ever.
- A prominent indicator is on screen the whole time, and it cannot be disabled.
- Only **your own** voice is captured — never other players, never ambient chat.
- Audio is stored **on the server**, in the world save. **The server operator can hear it**, just
  as they can read every chat log and book on their server. Do not record anything you would not
  want them to hear.
- Cancelling discards the recording. Unconfirmed recordings are deleted automatically.
- Nothing is uploaded anywhere. No telemetry, no external services, no transcription.

The full policy is in `PRIVACY.md` in the repository.

---

## Compatibility

- Works on dedicated servers and in single player.
- **No mixins** into Minecraft or into Simple Voice Chat, and no reflection into its internals —
  only its published plugin API. That is what makes it safe in a large modpack.
- Registers no blocks, items, entities, or worldgen. There is nothing to conflict with.

---

## FAQ

**Does the author have to be online?**
No. That is the entire point.

**Do I need to be in a voice chat group?**
No. EchoPins uses Simple Voice Chat purely as an audio pipe.

**Can other players hear me while I record?**
No, not by default.

**How much disk does this use?**
Roughly 2 KB per second of speech. A thousand ten-second messages is about 20 MB, and there is a
hard cap regardless.

**Does it work in the Nether, the End, or modded dimensions?**
Yes. Pins store their own dimension and only appear in the right one.

**Can I use Plasmo Voice instead?**
Not in 1.0. The backend sits behind an adapter interface so another one can be added later.

**Is there an API for other mods?**
Not a stable one in 1.0. The internal seams exist for map-mod and team-mod integrations, but
nothing is promised as public API yet.

---

## Images

The images on this page are clearly-labelled **UI mockups** built from the mod's real interface
geometry and strings. Genuine in-game screenshots are still to be captured; the shot list is in
the repository.

---

## Links

- Source and issues: https://github.com/DarkJest/echopins
- Privacy policy, configuration reference and architecture notes are all in the repository.

MIT licensed. Simple Voice Chat is a separate project under its own terms; EchoPins bundles no
part of it.
