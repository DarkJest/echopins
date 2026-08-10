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
| Minecraft | `1.20.1` |
| Modloader | `Fabric`, `Forge` **and** `NeoForge` |
| Java | `Java 17` |
| Environment | Client **and** Server |

Each uploaded file carries only its own loader tag. Do not tag the NeoForge jar as Fabric or the
other way round: CurseForge uses these tags to decide what a launcher installs, and a wrong tag
hands the user a jar their loader cannot read.

## Relations (dependencies)

| Project | Type | Applies to |
|---|---|---|
| Simple Voice Chat | **Required Dependency** | both files |
| Fabric API | **Required Dependency** | the Fabric file only |

These must be set explicitly. CurseForge does not infer them, and without them the launcher will
not pull the dependency into a pack, which produces a missing-dependency screen for the user.

Relations are set **per file**, so the Fabric API relation goes on the Fabric upload only.

No optional, embedded, incompatible or tool relations.

## File upload

Three files for this beta, uploaded separately.

| Field | Fabric | Forge | NeoForge |
|---|---|---|---|
| File | `echopins-fabric-1.2.0-beta.1+mc1.20.1.jar` | `echopins-forge-1.2.0-beta.1+mc1.20.1.jar` | `echopins-neoforge-1.2.0-beta.1+mc1.20.1.jar` |
| Display name | `EchoPins 1.2.0-beta.1 (MC 1.20.1, Fabric)` | `EchoPins 1.2.0-beta.1 (MC 1.20.1, Forge)` | `EchoPins 1.2.0-beta.1 (MC 1.20.1, NeoForge)` |
| Modloader tag | `Fabric` | `Forge` | `NeoForge` |
| Dependencies | Simple Voice Chat, Fabric API | Simple Voice Chat | Simple Voice Chat |
| Release type | Beta | Beta | Beta |
| Changelog | contents of `curseforge-changelog.md`, markdown | same | same |

### Release type

Pick based on what has actually been verified:

- **Release** — only after the manual matrix in `docs/TESTING.md` has been run on a live dedicated
  server with two or more clients.
- **Beta** — build and unit tests pass, multi-client testing not yet done. Version the artifact
  `0.1.0-beta` in that case.

As of writing, multi-client testing has **not** been performed, and the Fabric build has not been
run in game at all. Upload as **Beta**.

## Modpack permission

Set project settings to allow modpack inclusion without asking. The description states this too,
because pack authors usually read the description rather than the settings page.

## Gallery

Upload the four images from `branding/promo/` with the captions from
`branding/SCREENSHOT_PLAN.md`. They carry a visible `UI MOCKUP` label; keep it until real
screenshots exist.
