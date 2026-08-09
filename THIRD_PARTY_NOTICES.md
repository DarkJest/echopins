# Third-party notices

EchoPins is MIT licensed. It **bundles no third-party code**: nothing below is redistributed
inside the released jar. Everything listed here is either provided by the environment at runtime
or used only at build time.

## Why EchoPins is MIT

The licence was chosen after checking what EchoPins actually ships and what its dependencies
require:

- EchoPins contains **no copied source** from Simple Voice Chat, NeoForge or Minecraft.
- It uses no mixins into Minecraft or into Simple Voice Chat, and no reflection into Simple Voice
  Chat internals — only its published plugin API.
- The compile-time dependency on `voicechat-api` is `compileOnly`, so the API classes are not
  packaged into the EchoPins jar. They are supplied at runtime by the Simple Voice Chat mod the
  user installed themselves.

Because nothing copyleft or otherwise restrictive is redistributed, a permissive licence is
appropriate and MIT was chosen as the most widely understood option.

## Runtime dependencies (not bundled)

### Simple Voice Chat

- Project: https://modrepo.de/minecraft/voicechat/overview
- Source: https://github.com/henkelmax/simple-voice-chat
- Author: Max Henkel
- Used via: the published `de.maxhenkel.voicechat:voicechat-api` artifact, `compileOnly`
- Redistributed: **no**

The Simple Voice Chat mod jar declares `license = "All rights reserved"`. EchoPins therefore does
**not** redistribute it, does not copy code from it, and does not modify it. Users install it
themselves from its official pages. EchoPins only calls the plugin API that the project publishes
to Maven for exactly this purpose.

If you are packaging EchoPins into a modpack, obtain Simple Voice Chat through the normal
Modrinth/CurseForge distribution channels and respect that project's own terms.

### NeoForge and Minecraft

- NeoForge: https://neoforged.net/ — LGPL 2.1 for the loader and patches
- Minecraft: © Mojang Studios, under the Minecraft EULA

Neither is redistributed by EchoPins. Both are provided by the player's installation.

## Build-time only

| Dependency | Licence | Purpose |
|---|---|---|
| ModDevGradle | LGPL 2.1 | Gradle plugin that sets up the NeoForge development environment |
| JUnit 5 | EPL 2.0 | Unit tests |
| SLF4J | MIT | Logging API and a test-time binding |

None of these appear in the released artifact.

## Assets

Every visual asset in `branding/` and `src/main/resources/assets/echopins/textures/` is original
work created for this project, released under the same MIT licence as the code.

They contain no Mojang artwork, no Minecraft textures, no Simple Voice Chat artwork, and no
third-party icon sets. The logo is an original map-pin-plus-microphone construction built from
plain geometry in `branding/logo.svg`.

Banner and mockup text is rendered with fonts installed on the build machine. Those fonts are not
redistributed — only the rasterised images are, which is not font redistribution.
