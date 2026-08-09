# Release checklist

## Project identity — already applied

Every placeholder has been replaced. The project is wired to:

| | |
|---|---|
| GitHub | `https://github.com/DarkJest/echopins` |
| Modrinth | `https://modrinth.com/mod/echopins` |
| CurseForge | `https://www.curseforge.com/minecraft/mc-mods/echopins` |

The `echopins` slug was verified free on Modrinth and on GitHub at the time of writing. The
CurseForge slug could not be checked without an account — **confirm it during project creation**,
and if it is taken, pick another and update `release/promo-copy.md` and `release/ru/promo-copy.md`,
which are the only files carrying that URL.

Still worth deciding:

- **`mod_authors`** in `gradle.properties` — currently `EchoPins Contributors`.
- **Copyright holder** in `LICENSE` — currently `EchoPins Contributors`.
- **Root package** — `dev.echopins`. Fine to keep.

## Decide the release channel honestly

Section 40 of the original brief is right about this: pick the channel from the actual state of
testing, not from the version number.

- **`1.0.0` / Release** — only once the manual matrix in [docs/TESTING.md](docs/TESTING.md) has
  been run on a live dedicated server with two or more real clients, including private-pin ACL and
  push-to-talk recording.
- **`0.1.0-beta` / Beta** — the automated build and unit tests pass but multi-client testing has
  not happened.

**Current state at the time of writing: automated build and unit tests pass; the multi-client
matrix has _not_ been executed.** On that basis the honest first publish is **beta**. If you go
that route, set `mod_version=0.1.0-beta` in `gradle.properties` and tag `v0.1.0-beta`.

## Pre-release

- [ ] All placeholders above replaced
- [ ] `mod_version` in `gradle.properties` matches the tag you intend to push
- [ ] `CHANGELOG.md` has a dated section for this version, and `[Unreleased]` is empty
- [ ] `./gradlew clean build` passes
- [ ] `./gradlew runServer` starts a dedicated server with no client-class errors
- [ ] `./gradlew runClient` starts and the keybinds appear under *Options → Controls → EchoPins*
- [ ] Manual matrix in `docs/TESTING.md` filled in with real ✅/❌, not optimistic ones
- [ ] Both language files have the same key set:
      `python -c "import json;a=json.load(open('src/main/resources/assets/echopins/lang/en_us.json',encoding='utf-8'));b=json.load(open('src/main/resources/assets/echopins/lang/ru_ru.json',encoding='utf-8'));print(set(a)^set(b) or 'identical')"`
- [ ] Long Russian strings still fit their panels in game

## Creating the project pages

These need account ownership and cannot be automated from a clean slate.

- [ ] Create the Modrinth project using [release/modrinth-metadata.md](release/modrinth-metadata.md)
- [ ] Create the CurseForge project using [release/curseforge-metadata.md](release/curseforge-metadata.md)
- [ ] Set **Simple Voice Chat as a required dependency on both**, explicitly
- [ ] Upload the gallery images from `branding/promo/` with the captions from
      [branding/SCREENSHOT_PLAN.md](branding/SCREENSHOT_PLAN.md)
- [ ] Confirm the images keep their `UI MOCKUP` label until real screenshots replace them

## Repository secrets

Add under *Settings → Secrets and variables → Actions*. The release workflow skips publishing
gracefully if they are absent, so the GitHub release never depends on them.

| Secret | Needed for | Where to get it |
|---|---|---|
| `MODRINTH_TOKEN` | Modrinth publishing | Modrinth → Settings → PATs, scope `Create versions` |
| `MODRINTH_PROJECT_ID` | Modrinth publishing | The project page, under the ID field |
| `CURSEFORGE_TOKEN` | CurseForge publishing | CurseForge → My Account → API Tokens |
| `CURSEFORGE_PROJECT_ID` | CurseForge publishing | The project page, top right |

**Never commit a token.** They belong only in repository secrets.

## Releasing

```bash
git tag -a v1.0.0 -m "EchoPins 1.0.0"
git push origin v1.0.0
```

The `release.yml` workflow then:

1. verifies the tag matches `mod_version`, failing loudly if not,
2. runs a clean build and the tests,
3. computes the jar's SHA-256 and writes a `.sha256` file,
4. creates the GitHub release from `release/github-release.md` with the checksum appended,
5. attempts Modrinth and CurseForge publishing in a separate job that is allowed to fail.

## Post-release

- [ ] Download the published jar and verify its SHA-256 against the release notes
- [ ] Install the downloaded jar on a clean instance and confirm it loads
- [ ] Record the SHA-256 in `CHANGELOG.md` or the release notes for future reference
- [ ] Announce, using [release/promo-copy.md](release/promo-copy.md) — but not before the pages
      exist and testing is genuinely done
- [ ] Watch the issue tracker for the first day

## What is deliberately not automated

- Creating the Modrinth and CurseForge projects — needs account ownership.
- Adding repository secrets — needs repository ownership.
- Capturing real screenshots — needs a running game with more than one player.
- Filling in the manual test matrix — needs a human with two clients.

Everything else, including checksums and release notes, is handled by the workflow.
