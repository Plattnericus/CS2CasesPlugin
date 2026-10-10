# Third-party and asset notices

Maintainer: **Plattnericus** · **info@plattnericus.dev** · reviewed 2026-10-10.

MCCases is independent of Mojang, Microsoft and Valve. Minecraft-related materials
are subject to the [Minecraft EULA](https://www.minecraft.net/en-us/eula) and
[Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines).
Counter-Strike and Steam are Valve trademarks; see
[Valve Legal Info](https://store.steampowered.com/legal).
No endorsement or trademark license is granted by this project.

## What the production JAR contains

The Gradle `jar` task packages this project's compiled classes, default catalog,
configuration, translations, rendered assets, resource pack and legal documentation.
It does not shade or bundle Paper, Bukkit, Adventure, JOML, Gson, SnakeYAML, SLF4J,
SQLite JDBC, Minecraft client/server binaries or the development check plugin.
References to vanilla model IDs and sounds are resolved by the installed game;
the original game files are not copied into this build.

| Component | Role in this repository | Redistribution status |
| --- | --- | --- |
| Paper API `26.3.build.159-beta` and its API dependencies | `compileOnly`; types provided by the server | Not bundled in the production JAR |
| Xerial SQLite JDBC `3.49.1.0` | `toolsRuntimeOnly`; local database checks | Not bundled; server drivers are supplied by the server installation |
| Gradle Wrapper | Build tooling committed separately | Apache-2.0; [license shipped with the wrapper](gradle/LICENSE) |
| Python / Pillow / PyYAML | Development, pack merge and screenshot scripts | Installed separately; not bundled |
| Java runtime and Minecraft/Paper installations | Build/runtime or isolated local testing | Installed/downloaded separately; not release assets |

Use the relevant upstream license and notices when separately distributing one of
these dependencies. This inventory is not a replacement for their license texts.

## Project artwork and additional packs

`AssetGenerator`, `SkinRenderer`, `PackExporter` and the inspect-rig code generate
the standard artwork and geometry from this project's default textures and catalog.
That describes provenance within the build; it does not establish ownership or
permission for every underlying design. CS-inspired names and designs require their
own assessment. See [LEGAL.md](LEGAL.md).

Custom fonts, logo graphics and artwork from the separately supplied Fusion-HD
pack are not part of the standard public release. Rights evidence for those files
has not been established in this repository. Obtain permission for distribution
and record any required attribution before publishing a combined pack.
Configured NPC player skins require their own rights review as well.

No project-wide open-source license is asserted. Existing third-party licenses,
separate permissions and applicable statutory rights are unaffected.
