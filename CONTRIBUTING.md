# Contributing

Thanks for considering it. EchoPins is small on purpose, and staying small is a feature.

## Before you start

Open an issue first for anything beyond a bug fix. The [non-goals](#non-goals) list exists so
nobody spends a weekend on something that will be declined.

## Building

```bash
git clone https://github.com/DarkJest/echopins
cd echopins
./gradlew build
```

Needs JDK 21. The first build downloads and decompiles Minecraft, so give it a few minutes.

| Task | What it does |
|---|---|
| `./gradlew build` | Compile and test both loaders, produce both jars |
| `./gradlew test` | Unit tests only (they run once per loader project) |
| `./gradlew :neoforge:runClient` | Dev client with Simple Voice Chat on the classpath |
| `./gradlew :neoforge:runServer` | Dev dedicated server |
| `./gradlew :neoforge:runGameTestServer` | GameTest harness |
| `./gradlew :fabric:runClient` | Dev client, **without** Simple Voice Chat — see below |

Development on the voice integration has to happen on NeoForge. Loom refuses to process a mod jar
built with a newer version of Loom than the one in use, and every Loom release that accepts the
current Simple Voice Chat build requires Gradle 9, which ModDevGradle does not support. Rather than
split the build across two Gradle versions, the Fabric dev runtime omits Simple Voice Chat. The
integration code is shared, so exercising it on NeoForge exercises it for both.

`runClient` and `runServer` pull the real Simple Voice Chat mod from the Modrinth Maven so the
integration can actually be exercised.

## Architecture rules

These are enforced by review, and breaking one is the fastest way to get a PR sent back.

0. **`common/` imports nothing from either loader.** It is compiled by both `neoforge` and
   `fabric` against their own remapped Minecraft. If you need something a loader provides, put the
   loader-specific part in that loader's project and reach it through an interface in `common` —
   `ServerLimits`, `ClientSettings` and `EchoPinsNetwork.Transport` are the existing examples.
   Anything else silently breaks one of the two builds.
1. **`dev.echopins.domain` imports nothing from Minecraft, either loader, or Simple Voice Chat.**
   That
   restriction is what makes the interesting logic unit-testable. If a domain class needs a
   Minecraft type, the type is wrong, not the rule.
2. **Client code lives under `dev.echopins.client`** and is reached only through the
   `Dist.CLIENT` entrypoint. A dedicated server must never load a rendering class.
3. **Simple Voice Chat types appear only in `dev.echopins.integration.voicechat`.** Everything
   else talks to the `VoiceBackend` port.
4. **No mixins, and no reflection into Simple Voice Chat internals.** Only its published API. This
   is what keeps EchoPins safe in a big modpack.
5. **The server validates everything.** A payload never carries identity, dimension or time.
6. **No blocking IO on the server thread.** Use `EchoPinsExecutors`.

## Style

- Java 21. Records for value objects, sealed interfaces for closed hierarchies, enums over magic
  strings.
- Comments explain **why**, not what. `// gets the player` above `getPlayer()` will be removed.
- Javadoc on public interfaces, non-obvious domain abstractions, the binary format, and anything
  security-sensitive.
- No `catch (Exception ignored) {}`. If it is genuinely ignorable, log at debug and say why.
- No new dependencies without justification in the PR description. No Kotlin, no Lombok, no UI
  library, no database.

## Tests

New logic in `domain` or the EPV format needs a unit test. Test the **behaviour**, not the
implementation, and name tests so a failure reads as a sentence:

```java
@Test
@DisplayName("A private pin is hidden from players who are not recipients")
```

Please do not add tests that only exist to move a coverage number.

For anything touching a running server, add a row to the matrix in
[docs/TESTING.md](docs/TESTING.md) and say in the PR whether you ran it.

## Localization

Every user-facing string is a translation key. Add it to **both** `en_us.json` and `ru_ru.json`
— an English string in the Russian file is worse than a missing key, because it silently ships.

Check that long translations still fit their panel. The HUD hint has bitten us once already.

## Pull requests

- One logical change per PR.
- Say what you actually tested. "Should work" is not a test result; if you could not run
  something, say so.
- `./gradlew build` must pass.
- Update `CHANGELOG.md` under `[Unreleased]`.

## Non-goals

Declined for v1 regardless of implementation quality:

speech-to-text · transcription · translation · cloud storage · any external web service · Discord
integration · an encryption layer over Simple Voice Chat · a database backend · Fabric or Forge
ports · other Minecraft versions · a mobile or web companion · a global voicemail system · friend
systems · anything resembling a social network

Deliberately deferred, and welcome as a discussion:

Plasmo Voice backend · Xaero/JourneyMap integration · FTB Teams and claim-mod integration ·
text-only notes · reactions · threads

The seams for those already exist (`VoiceBackend`, `AccessPolicy`, `DomainEventBus`), which is why
they can wait without becoming a rewrite.

## Licence

Contributions are MIT, same as the project. Do not paste code from other mods.
