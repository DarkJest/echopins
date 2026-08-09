# EchoPins 1.0.1

First release. Minecraft 1.21.1, NeoForge, requires Simple Voice Chat 2.6.20+.

### Voice pins

- Record a short voice message and anchor it to a block face or a free position.
- Hold-to-record on a rebindable key (default `B`), with a preview and confirmation before
  anything is published.
- Optional text caption per pin, which doubles as the accessibility fallback.
- Public and private visibility, with a recipient picker that selects by UUID.
- Short, default or permanent lifetime, subject to server policy.

### Discovery and playback

- Compact world markers that fade in within the discovery radius, with distance scaling and
  configurable occlusion.
- Label showing author, age, length, caption and visibility when you look at a pin.
- Locational playback through Simple Voice Chat, audible only to players allowed to hear it.
- Dedicated **EchoPins** volume category, adjustable separately from proximity chat.
- Inbox with Nearby, Mine, Private and Unread tabs, plus per-player read state.

### Server

- Chunk-based spatial index — no per-tick scan of all pins.
- Per-player subscriptions using snapshots and deltas, recalculated only on chunk crossings.
- 32 server options and 16 client options, all range-validated.
- Token-bucket request limiting plus separate create and playback cooldowns.
- Per-player, per-server, per-location and total-storage caps.
- Incremental expiry sweeps and orphan-audio collection.
- Full player and admin command tree.

### Storage

- `.epv` container: versioned, CRC-32 checked, bounds-checked Opus frames stored verbatim with no
  transcode.
- Atomic writes with stale temporary files swept at startup.
- Pin metadata created only after audio is durably on disk.
- Per-pin schema versioning and a migration framework, present from v1.

### Privacy

- Recording only during an explicit, visible session. No background or hidden capture.
- Only the recording player's own voice is captured.
- Recorded audio is not simultaneously broadcast to bystanders, by default.
- Sessions end on disconnect, death, dimension change, length limit or silence timeout.
- Unconfirmed recordings are deleted automatically.

### Known limitations

- **Push-to-talk users must hold their voice chat key as well as the EchoPins key.** Simple Voice
  Chat's public API only exposes microphone audio once it is already transmitting, and there is no
  supported way to start capture from outside it. The recording indicator says so and reports
  whether audio is arriving.
- Audio is stored unencrypted in the world save; server operators can read it. Documented in
  `PRIVACY.md` rather than papered over.
- Simple Voice Chat is the only backend in 1.0.
- Gallery images are labelled UI mockups; real screenshots are still to be captured.
- The manual multi-client test matrix has not yet been executed end to end.
