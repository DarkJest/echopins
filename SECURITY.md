# Security policy

## Supported versions

| Version | Supported |
|---|---|
| 1.0.x | ✅ |
| < 1.0 | ❌ |

## Reporting a vulnerability

**Please do not open a public issue for a security problem.**

Use GitHub's private reporting: *Security → Report a vulnerability* on
https://github.com/DarkJest/echopins, which opens a private advisory visible only to
maintainers.

Include:

- what the issue is and what an attacker gains,
- the versions of EchoPins, Simple Voice Chat, NeoForge and Minecraft,
- reproduction steps or a proof of concept,
- whether it needs operator privileges or a modified client.

You can expect an acknowledgement within a few days, an assessment within two weeks, and a fix
released for confirmed issues before the report is made public. Credit is given unless you ask
otherwise.

## What counts

EchoPins treats **every client as hostile**. Anything a modified client can do that it should not
is in scope, particularly:

- playing or discovering a private pin without access,
- creating a pin as another player, in another dimension, or at an arbitrary distance,
- deleting or editing someone else's pin,
- causing a server crash, hang, or unbounded memory or disk use,
- escaping the audio directory or influencing any filesystem path,
- capturing a player's voice outside an explicit recording session, or recording someone into
  another player's message,
- bypassing a rate limit or storage cap.

Also in scope: anything that causes EchoPins to record when the player has not asked it to, or
that makes an active recording invisible.

## What does not count

- **A server operator reading or playing recordings.** They own the world files. This is stated
  plainly in [PRIVACY.md](PRIVACY.md) and is a property of the design, not a flaw.
- **No encryption at rest.** Deliberate, and explained in
  [docs/SECURITY_MODEL.md](docs/SECURITY_MODEL.md): the server must decrypt audio to play it, so
  encryption would imply a protection that does not exist.
- **Vulnerabilities in Simple Voice Chat, NeoForge or Minecraft.** Report those upstream. If
  EchoPins uses one of their APIs in a way that makes an upstream issue worse, that part is ours.
- **Cheat clients, x-ray, or anything requiring existing operator privileges.**

## Design commitments

These are the properties a fix must preserve:

1. Identity comes from the connection, never from a payload.
2. Access is checked server-side at discovery **and** again at playback, and once more per
   listener inside the audio channel.
3. No API accepts a filesystem path; audio is addressed by UUID only.
4. Every network collection and string is bounded before it is allocated.
5. A microphone frame is stored only if the speaker has an open session of their own.

The threat model is documented in full in [docs/SECURITY_MODEL.md](docs/SECURITY_MODEL.md).
