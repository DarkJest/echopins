# Promotional copy

Ready to use. The links are already filled in, but they point at pages that do not exist until
you create them.

Please do not post any of this until the mod pages actually exist and the multi-client testing in
`docs/TESTING.md` has been done. Announcing an untested release is how a good mod gets a bad
first impression.

---

## One-line pitch

> Leave voice messages exactly where your friends need them.

## Short pitch (2–3 sentences)

> EchoPins lets you record a short voice message and stick it to a place in your Minecraft world —
> a chest, a portal, a fork in a mine. Your friends hear it when they get there, whether or not
> you are online. It's the thing you would have said out loud, if you had both been playing at the
> same time.

---

## Discord announcement

> **EchoPins 1.0 — leave a message where it matters** 🎙️📍
>
> You know the problem. Your friend logs off, you walk into the base, and there's a chest full of
> iron and a half-built machine with no explanation.
>
> EchoPins lets you record a short voice message and anchor it to a spot in the world. Look at the
> chest, hold a key, say *"don't take the iron yet, it's for the reactor"*, and the next person to
> walk up hears it. You don't have to be online.
>
> • Public or private messages
> • Small, quiet markers — no giant HUD, no beacon beams
> • Its own volume slider in Simple Voice Chat
> • Messages expire on their own so your world save doesn't grow forever
> • Full EN + RU translations
>
> Minecraft 1.21.1 · NeoForge & Fabric · needs Simple Voice Chat on both sides
>
> One thing worth knowing up front: it records your voice, and that audio lives on the server
> where whoever owns the machine can read it. That's spelled out properly in the privacy doc rather than
> buried.
>
> https://modrinth.com/mod/echopins

---

## Reddit post

**Title:** EchoPins — leave short voice messages anchored to places in your world (1.21.1,
NeoForge & Fabric)

**Body:**

> I play on a small server where nobody is online at the same time, and we kept running into the
> same thing: you come back to the base and there's a chest full of iron, a half-built machine and
> no idea what any of it is for. Signs never quite worked — you don't want to spend two minutes
> placing and typing every time you log off.
>
> So: EchoPins. You look at a spot, hold a key, say a sentence, and release. It leaves a small
> marker there. Whoever walks up next sees it and presses one key to listen. The author doesn't
> need to be online.
>
> Things it's actually been used for so far: "don't take this iron yet", "this portal comes out by
> the fortress", "I went left, there's a spawner on the right", "don't turn this line on".
>
> A few deliberate choices:
>
> - **It's small.** No friend system, no feed, no notifications beyond a soft cue. It's a voice
>   note stuck to a place, and nothing else.
> - **Markers are quiet.** They fade in when you're close and that's it.
> - **Private pins are enforced server-side**, and again per listener inside the audio channel —
>   not just hidden on the client.
> - **It expires by default.** A week, configurable. Voice data shouldn't accumulate forever on
>   someone's server without them choosing that.
>
> Two honest caveats:
>
> If you use push-to-talk, you have to hold your voice chat key as well as the EchoPins key.
> Simple Voice Chat's API only hands over microphone audio once it's already transmitting, and
> there's no supported way to start capture from outside it. I could have hacked around that by
> reaching into its internals, but that's both fragile and exactly the kind of thing you don't
> want a mod doing with your microphone. The HUD tells you when no audio is arriving.
>
> And the audio sits unencrypted in the world save, so whoever owns the server can read it. That's
> in the privacy doc in plain language, because it's a real thing players should know rather than
> something to gloss over.
>
> Requires Simple Voice Chat. 1.21.1, NeoForge or Fabric, both sides. MIT licensed, source linked below.
>
> Happy to answer questions about how it's built — the audio container and the sync layer were the
> interesting parts.
>
> https://modrinth.com/mod/echopins
> https://github.com/DarkJest/echopins

*Post to r/feedthebeast. Read the rules first, flair it correctly, and reply to comments — a
drive-by link post reads as spam.*

---

## X / Twitter

> Your friend logs off. You find a chest full of iron and no explanation.
>
> EchoPins lets you leave a short voice message stuck to a place in your Minecraft world. Your
> friends hear it when they get there — online or not.
>
> 1.21.1 · NeoForge / Fabric · Simple Voice Chat
>
> https://modrinth.com/mod/echopins

*(Attach `branding/promo/world-pin.png`.)*

Alternative, shorter:

> 🎙️📍 Leave a voice message where it matters.
>
> EchoPins: record a note, stick it to a chest, a portal, a fork in a mine. Your friends hear it
> when they arrive.
>
> Minecraft 1.21.1 · NeoForge & Fabric
> https://modrinth.com/mod/echopins

---

## YouTube showcase title

Primary:

> EchoPins — Leave Voice Messages Anywhere in Minecraft

Alternatives:

> This Minecraft Mod Lets You Leave Voice Notes for Your Friends
>
> Minecraft, but you can leave voice messages stuck to places

Avoid clickbait phrasing. The idea sells itself in four words; overselling it will only disappoint
people who install it expecting a bigger mod.

## YouTube description

> EchoPins lets you record a short voice message and anchor it to a place in your Minecraft world.
> Your friends hear it when they get there — even if you are offline.
>
> Look at a chest, hold a key, speak, release. That's it.
>
> ⏱️ Chapters
> 0:00 The problem
> 0:20 Leaving your first EchoPin
> 0:50 Finding someone else's message
> 1:20 Public vs private
> 1:50 The inbox
> 2:20 Server settings
>
> 📦 Download
> Modrinth: https://modrinth.com/mod/echopins
> CurseForge: https://www.curseforge.com/minecraft/mc-mods/echopins
> Source: https://github.com/DarkJest/echopins
>
> ⚙️ Requirements
> Minecraft 1.21.1 · NeoForge 21.1+ or Fabric Loader 0.19.3+ · Java 21
> Simple Voice Chat (required, on both client and server)
>
> 🎙️ A note on push-to-talk
> If you use push-to-talk, hold your voice chat key as well as the EchoPins key while recording.
> Simple Voice Chat's API only exposes microphone audio once it's already transmitting, so EchoPins
> can't switch your mic on for you. The in-game indicator tells you when no audio is arriving.
>
> 🔒 Privacy
> EchoPins only records while you hold the key, only records your own voice, and always shows an
> indicator. Audio is stored on the server, where the operator can access it. Full details:
> https://github.com/DarkJest/echopins/blob/main/PRIVACY.md
>
> MIT licensed.

---

## 30-second video script

| Time | Visual | Audio |
|---|---|---|
| 0:00–0:05 | A player opens a chest full of iron. They pause, clearly unsure. | *(ambient game audio only)* |
| 0:05–0:10 | A small teal EchoPin marker fades in above the chest. The label appears: author, "12s ago", "0:08". | Soft notification cue. |
| 0:10–0:15 | Player presses the play key. The label switches to "Playing…". | **Voice:** *"Don't use this iron yet — it's for the reactor."* |
| 0:15–0:20 | Cut to the same player elsewhere, looking at a machine. Recording HUD appears: red dot, timer, progress bar. | **Voice:** *"Don't switch this line on, it's not wired up."* Then the stop cue. |
| 0:20–0:25 | Cut to a second player entering a dark mine, finding a pin at a tunnel fork and playing it. | **Voice:** *"I went left. There's a spawner on the right."* |
| 0:25–0:30 | Fade to the EchoPins logo on dark background, then the tagline, then version line. | Music resolves. |

**End card**

```
EchoPins
Leave a message where it matters.

Minecraft 1.21.1 · NeoForge / Fabric · Simple Voice Chat
```

**Production notes**

- Use real recorded voice, not text-to-speech. The mod is about people's voices, and synthetic
  audio undercuts that completely.
- Keep the music quiet under the voice lines, or drop it entirely for 0:10–0:25.
- Do not speed anything up. The point is that the interaction is already fast.
- Shoot at 1080p60 with vanilla resources and no shaders, so it represents a default install.
