## What this changes

<!-- One or two sentences. Link the issue if there is one. -->

Closes #

## Why

<!-- What problem does this solve? -->

## What I tested

<!--
Be specific and be honest. "Should work" is not a test result.
If you could not run something, say so — an untested area named is more useful than a wrong claim.
-->

- [ ] `./gradlew build` passes
- [ ] Unit tests added or updated for changed logic
- [ ] Ran in a dev client (`./gradlew runClient`)
- [ ] Ran on a dedicated server (`./gradlew runServer`)
- [ ] Relevant rows in `docs/TESTING.md` exercised

Details:

## Checklist

- [ ] `dev.echopins.domain` still imports nothing from Minecraft, NeoForge or Simple Voice Chat
- [ ] No client-only class is reachable from common or server code
- [ ] Simple Voice Chat types stay inside `dev.echopins.integration.voicechat`
- [ ] Any new user-facing string is a translation key, added to **both** `en_us.json` and `ru_ru.json`
- [ ] Long translations still fit their panel
- [ ] `CHANGELOG.md` updated under `[Unreleased]`
- [ ] No new dependency, or the PR explains why one is needed

## Notes for the reviewer

<!-- Anything you are unsure about, or deliberately left out of scope. -->
