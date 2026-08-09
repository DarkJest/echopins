# Release process

The step-by-step checklist lives in [RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md). This document
explains how the machinery works and why it is arranged this way.

## Versioning

[Semantic Versioning](https://semver.org/). The version lives in exactly one place —
`mod_version` in `gradle.properties` — and everything else derives from it.

| Change | Bump |
|---|---|
| Breaking wire protocol or persisted schema | major |
| New feature, backwards compatible | minor |
| Bug fix only | patch |

Two things are versioned separately from the mod and must be bumped when they change
incompatibly:

- `EchoPinsNetwork.PROTOCOL_VERSION` — NeoForge refuses a connection whose EchoPins protocol
  version differs, turning a mismatched pair into a clear message instead of a decode failure.
- `EchoPinsSavedData.DATA_VERSION` — bump **together with** a registered `DataMigration`. The
  registry rejects a gap in the chain at startup rather than when a user's world fails to load.

## Artifact naming

```
echopins-<version>+mc<minecraft>-neoforge.jar
```

for example `echopins-1.0.0+mc1.21.1-neoforge.jar`.

The build metadata lives in the archive version rather than the base name, so Gradle does not
append the raw version a second time.

## Reproducibility

The build aims to be deterministic:

- Java toolchain pinned to 21 via `java.toolchain`, so the JDK on the machine does not matter.
- UTF-8 forced for compilation and Javadoc.
- `preserveFileTimestamps = false` and `reproducibleFileOrder = true` on every archive task, so a
  rebuild of the same commit produces a byte-identical jar.
- The Gradle wrapper is committed, so everyone uses the same Gradle.
- Dependency versions are pinned in `gradle.properties`; nothing uses a dynamic version or a
  snapshot.

Verify:

```bash
./gradlew clean build && sha256sum build/libs/*.jar
./gradlew clean build && sha256sum build/libs/*.jar   # should match
```

## Workflows

### `build.yml`

Runs on every push and pull request: compile, unit tests, upload the jar and test results. The
NeoForm cache is keyed on `gradle.properties`, because that is what determines the Minecraft and
NeoForge versions being decompiled — the expensive part of a cold build.

GameTests are a **separate job** marked `continue-on-error`. They need a headless Minecraft
server, which is materially heavier and historically flakier in CI than unit tests. Keeping them
separate means a GameTest infrastructure failure is visibly distinct from a real build failure,
and the job can be promoted to required once it has proven stable.

### `release.yml`

Triggered by a `v*` tag, or manually with a tag input.

The important design decision: **GitHub release creation and mod-site publishing are separate
jobs.** The GitHub release is the source of truth and must never be blocked by a third-party API
being down or a token being absent. The publishing job is `continue-on-error` and checks for
credentials first, skipping cleanly with an explanatory message if none are configured.

The workflow also refuses to release if the tag does not match `mod_version`. Publishing
`v1.0.1` from a tree that still says `1.0.0` is a mistake that is very annoying to undo on
Modrinth, so it fails loudly instead.

Checksums are computed in CI from the artifact that is actually uploaded — not pasted in by hand,
which is how checksums end up wrong.

## Secrets

| Secret | Purpose |
|---|---|
| `MODRINTH_TOKEN` | Modrinth API token, scope `Create versions` |
| `MODRINTH_PROJECT_ID` | Target project |
| `CURSEFORGE_TOKEN` | CurseForge API token |
| `CURSEFORGE_PROJECT_ID` | Target project |

Absent secrets are not an error. The publish job detects them and skips.

**Never commit a token.** `GITHUB_TOKEN` is provided automatically and needs no setup.

## Release copy

Everything in `release/` is written ahead of time so a release is copy-and-paste rather than
prose-under-pressure:

| File | Used for |
|---|---|
| `modrinth-description.md`, `modrinth-summary.txt`, `modrinth-metadata.md` | Modrinth page |
| `curseforge-description.md`, `curseforge-summary.txt`, `curseforge-metadata.md` | CurseForge page |
| `modrinth-changelog.md`, `curseforge-changelog.md` | Version changelogs |
| `github-release.md` | Body of the GitHub release; CI appends checksums |
| `promo-copy.md` | Discord, Reddit, X, YouTube, and a 30-second video script |
| `en/`, `ru/` | Per-language copies |

The Modrinth and CurseForge descriptions are **adapted**, not copies of each other — the platforms
have different conventions and different audiences, and CurseForge readers in particular care
about modpack permissions up front.

## Choosing the channel

Pick from what has actually been tested:

- **Release** — the manual matrix in [TESTING.md](TESTING.md) has been run on a live dedicated
  server with two or more clients.
- **Beta** — build and unit tests pass, multi-client testing has not happened.

A `1.0.0` that has never been run with two clients is a beta with a confident version number. Ship
it as `0.1.0-beta` instead; the tag suffix also makes `release.yml` mark the GitHub release as a
prerelease automatically and skip mod-site publishing.

## Hotfixes

1. Branch from the tag.
2. Fix, add a regression test.
3. Bump the patch version, update the changelog.
4. Tag and push; the workflow does the rest.
5. Merge back to the main branch.

For a security fix, follow [SECURITY.md](../SECURITY.md) — get the release out first, publish the
advisory once users have had a chance to update.
