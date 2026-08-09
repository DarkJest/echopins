# Modrinth project metadata

Values to enter when creating the project. Nothing here can be automated without the project
already existing and an API token being configured — see `docs/RELEASE.md`.

## Project

| Field | Value |
|---|---|
| Name | `EchoPins` |
| Slug | `echopins` (fall back to `echo-pins` if taken) |
| Project type | **Mod** |
| Summary | contents of `modrinth-summary.txt` |
| Description | contents of `modrinth-description.md` |
| Licence | `MIT` |
| Icon | `branding/icon-512.png` |

## Environment

| Field | Value |
|---|---|
| Client side | **Required** |
| Server side | **Required** |

Both are required: the client renders markers and drives recording, the server owns all data,
validation and playback. A client-only or server-only install does not work, and the protocol
handshake rejects a mismatch with a clear message.

## Categories

Primary:

- `social` — the mod exists to let players communicate
- `utility` — it is a small tool, not a content mod

Also reasonable if a third is allowed:

- `game-mechanics`

Do **not** tag: `adventure`, `worldgen`, `equipment`, `decoration`, `optimization`, `library`.
None apply, and mis-tagging is the fastest way to annoy people browsing.

## Versions

| Field | Value |
|---|---|
| Game versions | `1.21.1` |
| Loaders | `neoforge`, `fabric` — set per file, not per version |
| Version number | `1.1.0` |
| Version name | `EchoPins 1.1.0 for 1.21.1` |
| Channel | see below |
| Changelog | contents of `modrinth-changelog.md` |
| Files | `echopins-neoforge-1.1.0+mc1.21.1.jar`, `echopins-fabric-1.1.0+mc1.21.1.jar` |

Modrinth allows several files under one version. Upload both and mark the NeoForge jar primary,
or create two versions if you would rather the loaders have separate changelog entries. Add
**Fabric API** as a required dependency alongside Simple Voice Chat; it applies to the Fabric jar
only, so use two versions if that distinction matters to you.

### Release channel

Choose based on what has actually been tested, not on the version number:

- **Release** — only once the manual matrix in `docs/TESTING.md` has been run on a live dedicated
  server with at least two clients.
- **Beta** — if the automated build passes but multi-client testing has not happened yet. In that
  case publish as a beta rather than a release.

At the time of writing, the automated build and unit tests pass, and the multi-client matrix has
**not** been executed. Publish as **beta** until it has.

## Dependencies

| Project | Type | Notes |
|---|---|---|
| `simple-voice-chat` | **Required** | Version 2.6.20 or newer for 1.21.1. EchoPins depends on the `voicechat_api` mod id that Simple Voice Chat ships inside its own jar. |

No optional or embedded dependencies. Nothing is bundled.

## Links

| Field | Value |
|---|---|
| Source | `https://github.com/DarkJest/echopins` |
| Issues | `https://github.com/DarkJest/echopins/issues` |
| Wiki | leave blank — the docs live in the repository |
| Discord | leave blank unless one exists; do not invent a link |

## Gallery

Upload the four images from `branding/promo/`, using the captions in
`branding/SCREENSHOT_PLAN.md`.

**They are UI mockups and are labelled as such in the image itself.** Keep that labelling until
real screenshots replace them.

Featured image: `world-pin.png`.
