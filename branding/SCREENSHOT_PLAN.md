# Screenshot plan

## Why this file exists

The images in `promo/` are **UI mockups**, not gameplay screenshots. They were built by rendering
the mod's real interface geometry, colours and strings as vector art over an abstract backdrop.
Every panel and string matches what the mod draws; the world behind them does not.

They carry a visible `UI MOCKUP` tag and must never be presented as screenshots.

This file is the shot list for replacing them with genuine captures.

## Capture setup

Use the same setup for every shot so the set looks like a set.

| | |
|---|---|
| Resolution | 1920×1080, captured with `F2` |
| GUI scale | 3 (2 for the inbox, so the whole panel is comfortable) |
| Field of view | 70 (default) |
| Graphics | Fancy, no shaders — shots should represent a default install |
| HUD | `F1` **off** for world shots, on for the ones showing the HUD |
| Time of day | Day for outdoor shots; torch-lit for the mine shot |
| Weather | Clear (`/weather clear`) |
| Players | Two accounts, so author names are real and not "Dev" |

Turn off the smooth-lighting-heavy resource packs and use vanilla textures. The point is to show
EchoPins, not a build.

Crop to 16:9 and export PNG. Do not upscale, do not add borders or drop shadows.

---

## Shot 1 — An EchoPin beside a chest

**Replaces:** `promo/world-pin.png`

- **Composition:** Chest slightly left of centre, roughly two blocks away, camera at eye height
  looking slightly down. The EchoPin marker floats above the chest lid. The focused-pin label sits
  just below the crosshair.
- **Must be visible:** the teal marker, the label with a real player name, `• new`, a relative age,
  a duration, a caption, and the "to play / for inbox" line.
- **Setup:** have the second account record a message near the chest with the caption
  *"Don't take the iron yet, it's for the reactor"*, then walk up on the first account.
- **Resolution:** 1920×1080 → crop 1600×900
- **Caption:** "Walk up to a chest and find out what your friend left it there for."
- **Alt text:** "A Minecraft chest with a small teal EchoPins marker floating above it. A compact
  label below the crosshair shows the author's name, that the message is new, that it was left 12
  seconds ago, that it is 8 seconds long, and a caption reading 'Don't take the iron yet, it's for
  the reactor'."

## Shot 2 — Recording

**Replaces:** `promo/recording.png`

- **Composition:** Same location as shot 1 so the set reads as a sequence. The recording panel is
  top-centre. HUD **on**.
- **Must be visible:** the red dot, the word "Recording", elapsed and maximum time, a partly filled
  progress bar, and the hint line.
- **Setup:** capture roughly 7 seconds into a 30-second maximum so the bar is visibly partial.
  Take **two** versions: one in voice-activation mode showing "Release the key to finish", and one
  in push-to-talk showing "Hold your voice chat key". The push-to-talk variant is the more useful
  one for the README, because that hint is the single most common source of confusion.
- **Resolution:** 1920×1080 → crop 1600×900
- **Caption:** "Hold the key and speak. The indicator is always visible while recording."
- **Alt text:** "The EchoPins recording indicator at the top of the screen: a red dot, the word
  Recording, a timer reading 0:07 of 0:30, a partly filled progress bar, and a hint reading 'Hold
  your voice chat key'."

## Shot 3 — Playing another player's pin

**New shot, no mockup equivalent**

- **Composition:** In a mine or cave, torch-lit, marker at a tunnel junction. Label shows
  "Playing…".
- **Must be visible:** the marker, the label in its playing state, and enough of the tunnel to make
  the "which way did they go" situation obvious.
- **Setup:** second account leaves *"I went left. There's a spawner on the right."* at a fork;
  first account plays it.
- **Resolution:** 1920×1080 → crop 1600×900
- **Caption:** "Messages play from where they were left, so you hear them in context."
- **Alt text:** "A torch-lit Minecraft mine shaft splitting into two tunnels. An EchoPins marker
  hovers at the fork and its label shows the message is currently playing."

## Shot 4 — A private pin

**Replaces:** `promo/private-pin.png`

- **Composition:** Near a nether portal or a base entrance. The private marker — blue-grey with an
  amber padlock — with its label.
- **Must be visible:** the padlock badge, and `[private]` in the label. Both must be legible, since
  the whole point is that private pins differ by **shape** as well as colour.
- **Setup:** record a private pin addressed to the account taking the screenshot.
- **Resolution:** 1920×1080 → crop 1600×900
- **Caption:** "Private pins are visible only to the people you choose."
- **Alt text:** "A blue-grey EchoPins marker with an amber padlock badge next to a nether portal.
  The label below shows the author's name followed by the word private in brackets."

## Shot 5 — The inbox

**Replaces:** `promo/inbox.png`

- **Composition:** Inbox screen open, **Nearby** tab selected, five or six entries, at least one
  unread (amber), at least one private, and at least one owned by the viewer so a `Delete` button
  is enabled while others are greyed out.
- **GUI scale:** 2, so the whole panel fits comfortably.
- **Must be visible:** all four tab names, the mixed read/unread states, the enabled vs. disabled
  Delete buttons, and the page indicator.
- **Setup:** seed pins from two or three accounts at varying ages. Real names and real timestamps.
- **Resolution:** 1920×1080 → crop 1600×900
- **Caption:** "Everything nearby, everything you left, and everything you have not heard yet."
- **Alt text:** "The EchoPins inbox screen with tabs for Nearby, Mine, Private and Unread. Five
  entries list author, age, duration and coordinates, each with Play and Delete buttons. One entry
  is highlighted amber as unread and one is marked private."

## Shot 6 — Settings

**New shot, no mockup equivalent**

- **Composition:** Two captures side by side, or two separate images:
  1. The EchoPins client config screen showing the marker and accessibility sections.
  2. Simple Voice Chat's *Adjust volumes* screen with the **EchoPins** category and its pin icon
     visible.
- **Must be visible:** in (2), the EchoPins entry with its 16×16 icon — that is the proof the
  dedicated volume category actually registered.
- **Resolution:** 1920×1080 → crop 1600×900 each
- **Caption:** "EchoPins gets its own volume slider, separate from voice chat."
- **Alt text:** "The Simple Voice Chat volume screen showing a separate EchoPins slider with a
  small map-pin-and-microphone icon beside it."

---

## After capturing

1. Save as `branding/promo/<name>.png`, replacing the mockup of the same name.
2. Delete the corresponding `.svg` mockup source, and remove its row from
   `branding/promo/_backdrop.md`.
3. Remove the *"UI mockup"* captions under the images in `README.md` and `README_RU.md`.
4. Update the **Screenshots** section of both READMEs — it currently states plainly that the images
   are mockups, and that sentence must go once it is no longer true.
5. Re-check the alt text above still describes the real image, and correct it if not.
6. Upload the same set to the Modrinth and CurseForge gallery, using these captions.

Until step 3 is done, the mockup labelling must stay. Presenting a mockup as a screenshot is not
acceptable even briefly.
