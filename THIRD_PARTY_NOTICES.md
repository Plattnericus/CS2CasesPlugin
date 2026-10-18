# Third-party and asset notices

Maintainer: **Plattnericus** · **info@plattnericus.dev** · reviewed 2026-10-17.

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
Minecraft binaries are not copied into this build. The four shader programs
`core/entity.vsh`, `core/entity.fsh`, `core/item.vsh` and `core/item.fsh` are adapted from the
installed Minecraft 26.3 resource-pack shader baseline; their original lighting, fog and
transparency logic remains present. The MCCases additions (metal sheen, skin-arm detection in
`include/mccases_arm.glsl`, inspect scene split) are project code. The Minecraft-derived shader
portions are subject to Mojang/Microsoft terms and are not asserted as independently owned or
separately relicensed project code.

The first-person skin arms use no bundled skin: the pack ships a player-head box, and each
player's own client draws their own skin on it from the player's profile.

| Component | Role in this repository | Redistribution status |
| --- | --- | --- |
| Paper API `26.3.build.159-beta` and its API dependencies | `compileOnly`; types provided by the server | Not bundled in the production JAR |
| Xerial SQLite JDBC `3.49.1.0` | `toolsRuntimeOnly`; local database checks | Not bundled; server drivers are supplied by the server installation |
| Gradle Wrapper | Build tooling committed separately | Apache-2.0; [license shipped with the wrapper](gradle/LICENSE) |
| Python / Pillow / PyYAML | Local development and screenshot tools, not part of the repository | Installed separately; not bundled |
| Java runtime and Minecraft/Paper installations | Build/runtime or isolated local testing | Installed/downloaded separately; not release assets |

Use the relevant upstream license and notices when separately distributing one of
these dependencies. This inventory is not a replacement for their license texts.

## Project artwork and additional packs

`AssetGenerator`, `SkinRenderer`, `PackExporter` and the inspect-rig code generate
the standard artwork and geometry from this project's default textures and catalog.
That describes provenance within the build; it does not establish ownership or
permission for every underlying design. CS-inspired names and designs require their
own assessment. See [LEGAL.md](LEGAL.md).

The resource pack includes only the 22 case PNGs listed in
[the source manifest](src/main/resources/case-art/SOURCE.md), taken from a user-provided
reference archive. That archive README says the case and key art were generated with
ImageGen. No skin images, inspect layers, fonts or pack icon from that archive are bundled.
The manifest records source and image hashes; it does not grant rights. The operator remains
responsible for permission and any required attribution for case art, custom assets and
configured NPC player skins.

Project code and generated artwork are licensed under the [MCCases License](LICENSE).
It does not extend to the third-party material above: the Minecraft-derived shader
portions, the case images in the source manifest, the Gradle Wrapper and other
dependencies keep their own terms. Separate permissions and statutory rights are unaffected.
