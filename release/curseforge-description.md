# EchoPins — leave a message where it matters

> **Requires [Simple Voice Chat](https://www.curseforge.com/minecraft/mc-mods/simple-voice-chat).
> Install EchoPins and Simple Voice Chat on both the client and the server.**

Your friend logs off. Three hours later you walk into the base and find a chest full of iron, a
half-built machine, and no idea what any of it is for.

EchoPins fixes that. Record a short voice message, leave it stuck to the chest, and whoever walks
up next hears it — whether or not you are online.

## What it does

EchoPins lets you attach a **short voice note to a place** in the world. Other players find it
later and play it back. That is the whole mod.

It is not a voice chat replacement, not a waypoint mod, and not a chat client. It does one thing
well.

**Examples of what people actually leave:**

- Near a chest — *"Don't take the iron yet, it's for the reactor."*
- At a portal — *"This one comes out by the fortress."*
- Down a mine — *"I went left. There's a spawner on the right."*
- By a machine — *"Don't switch this line on, it's not wired up."*

## How to use it

1. Look at the place that matters
2. **Hold `B`** (rebindable)
3. Speak, then release
4. Pick public or private, add a caption if you want, save

Friends see a small marker when they get close and press one key to listen.

## Features

- Voice notes anchored to a block or a position
- Works while the author is **offline**
- Small, unobtrusive world markers that fade in as you approach
- **Public or private** pins, with private enforced server-side per listener
- Optional text captions, which also serve as an accessibility fallback
- Its **own volume slider** in Simple Voice Chat, separate from proximity chat
- Automatic expiry so world saves do not grow forever
- Inbox screen: Nearby, Mine, Private, Unread
- Full English and Russian translations

## Requirements

|  |  |
| --- | --- |
| **Minecraft** | 1.21.1 |
| **Loader** | NeoForge 21.1.0+ or Fabric Loader 0.19.3+ |
| **Also required on Fabric** | Fabric API |
| **Java** | 21 |
| **Required mod** | Simple Voice Chat 2.6.20+ for 1.21.1 |
| **Sides** | Client **and** server |

## Important: how recording works

**EchoPins cannot turn your microphone on.** Simple Voice Chat's public API only exposes
microphone audio once it is already transmitting, and there is no supported way to start capture
from outside. Forcing it by reaching into the mod's internals would be fragile and a privacy
problem, so EchoPins does not do that.

What this means for you:

- **Voice activation** — works normally. Hold the EchoPins key and talk.
- **Push-to-talk** — hold the EchoPins key **and** your voice chat push-to-talk key together. The
  recording indicator reminds you, and tells you when no audio is arriving.

While recording an EchoPin, your voice is **not** also broadcast to players standing next to you.

## Privacy — please read

EchoPins records your voice, so here it is in plain terms:

- Recording happens **only** while you hold the key. There is no background recording.
- A visible indicator is on screen the entire time. It cannot be turned off.
- Only **your own** voice is captured. Never other players, never ambient chat.
- Audio is stored **on the server**, inside the world save. Operator status grants **no** in-game
  access to a private pin — an admin can delete one without being able to play it — but anyone who
  can read the server's files can read the audio. Do not record anything you would not want the
  server's owner to hear.
- Nothing is uploaded anywhere. No telemetry, no external services, no transcription.

The complete policy is in `PRIVACY.md` in the source repository.

## For server admins

The defaults are safe out of the box — pins expire after a week, storage is capped at 1 GiB, and
creating and playing are both rate limited.

There are 32 server options covering limits, discovery radius, expiry, rate limiting, storage caps
and cleanup. Every one is range-validated.

Moderation:

```
/echopins admin stats
/echopins admin delete <id>
/echopins admin purge player <player>
/echopins admin purge expired
/echopins admin cleanup
```

**Performance:** pins are indexed by chunk, players are only told about pins near them, and disk
work never happens on the server thread. Idle cost is negligible even with thousands of pins.

## Modpack friendly

- **No mixins** into Minecraft or Simple Voice Chat, and no reflection into its internals — only
  the published plugin API
- Registers no blocks, items, entities or worldgen
- Nothing to conflict with, no registry ids to reserve
- Permission to include EchoPins in any modpack, public or private, no need to ask

*(Simple Voice Chat is a separate project with its own terms — include it through the normal
CurseForge dependency, not by copying the jar.)*

## Screenshots

The images in the gallery are clearly-labelled **UI mockups** made from the mod's real interface
layout and text. Real in-game screenshots are still to be captured.

## Links

- **Source and issues:** https://github.com/DarkJest/echopins
- **Licence:** MIT
