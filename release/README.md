# Release copy

Everything needed to publish a release, written in advance so publishing is copy-and-paste rather
than writing prose under pressure.

## Layout

```
release/
├── modrinth-description.md     Modrinth project page body
├── modrinth-summary.txt        Short summary (max 256 chars)
├── modrinth-changelog.md       Version changelog for Modrinth
├── modrinth-metadata.md        Fields, categories, dependencies, release channel
├── curseforge-description.md   CurseForge page body — adapted, not a copy
├── curseforge-summary.txt      Short summary (max 255 chars)
├── curseforge-changelog.md     Version changelog for CurseForge
├── curseforge-metadata.md      Fields, categories, relations, modpack permission
├── github-release.md           Body of the GitHub release; CI appends checksums
├── promo-copy.md               Discord, Reddit, X, YouTube, 30-second video script
├── en/                         English copies of the above
└── ru/                         Russian versions
```

## Why the two platform descriptions differ

They are **adapted**, not duplicated. Modrinth readers skim a long markdown page and care about
what the mod does; CurseForge readers are disproportionately modpack authors who want the
dependency and packaging story up front, so that moves near the top there. Blindly pasting the
same text into both is how a project page reads like a template.

## Language

English is the primary language. `ru/` holds a real Russian version, written to read naturally
rather than translated word for word — for example the tagline is *«Оставь сообщение там, где это
важно»* rather than a literal rendering of "Leave a message where it matters".

The Russian description is suitable for the Modrinth and CurseForge pages that support localized
descriptions, and for posting in Russian-speaking communities.

## Before publishing

Project URLs are already filled in throughout: `github.com/DarkJest/echopins`,
`modrinth.com/mod/echopins` and `curseforge.com/minecraft/mc-mods/echopins`. The first two slugs
were verified free; the CurseForge one must be confirmed while creating the project, and if it is
taken, updated in `promo-copy.md` and `ru/promo-copy.md`.

The links point at pages that do not exist until you create them, so publish the pages before
posting any of the promotional copy.
