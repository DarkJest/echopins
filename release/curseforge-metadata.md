# CurseForge project metadata

## Project

| Field | Value |
|---|---|
| Name | `EchoPins` |
| Slug / URL | `echopins` |
| Summary | contents of `curseforge-summary.txt` (max 255 characters) |
| Description | contents of `curseforge-description.md` |
| Licence | `MIT` |
| Avatar | `branding/icon-512.png` |

## Categories

CurseForge's taxonomy differs from Modrinth's, so this is not a copy of the Modrinth tags.

Primary:

- **Server Utility** — most of the mod is server-side, and its options matter most to admins
- **Miscellaneous**

Also applicable:

- **Cosmetic** is *not* right — this is functional, not decorative
- **Map and Information** is arguably right if a third slot is available, since pins are
  place-based information

Do not tag **Adventure and RPG**, **World Gen**, **Technology**, or **Magic**. None apply.

## Game versions

| Field | Value |
|---|---|
| Minecraft | `1.21.1` |
| Modloader | `NeoForge` |
| Java | `Java 21` |
| Environment | Client **and** Server |

## Relations (dependencies)

| Project | Type |
|---|---|
| Simple Voice Chat | **Required Dependency** |

This must be set explicitly. CurseForge does not infer it, and without it the launcher will not
pull Simple Voice Chat into a pack, which produces a missing-dependency screen for the user.

No optional, embedded, incompatible or tool relations.

## File upload

| Field | Value |
|---|---|
| File | `echopins-1.0.0+mc1.21.1-neoforge.jar` |
| Display name | `EchoPins 1.0.0 (MC 1.21.1, NeoForge)` |
| Release type | see below |
| Changelog | contents of `curseforge-changelog.md`, markdown |

### Release type

Pick based on what has actually been verified:

- **Release** — only after the manual matrix in `docs/TESTING.md` has been run on a live dedicated
  server with two or more clients.
- **Beta** — build and unit tests pass, multi-client testing not yet done. Version the artifact
  `0.1.0-beta` in that case.

As of writing, multi-client testing has **not** been performed. Upload as **Beta**.

## Modpack permission

Set project settings to allow modpack inclusion without asking. The description states this too,
because pack authors usually read the description rather than the settings page.

## Gallery

Upload the four images from `branding/promo/` with the captions from
`branding/SCREENSHOT_PLAN.md`. They carry a visible `UI MOCKUP` label; keep it until real
screenshots exist.
