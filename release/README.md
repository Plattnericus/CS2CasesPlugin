# MCCases 1.2.7 build artifacts

The 1.2.7 JAR and resource pack were built and verified locally. Build artifacts are kept out of Git; the resource pack was copied to `~/Downloads/MCCases-ResourcePack-1.2.7.zip` for the live client test.

Recreate them with `bash gradlew build`; outputs are `build/libs/MCCases-1.2.7.jar` and `build/distributions/MCCases-ResourcePack-1.2.7.zip`. Verify the local copies with [SHA256SUMS-1.2.7](SHA256SUMS-1.2.7).
- [1.2.7 verification report](../docs/RELEASE-1.2.7-VERIFICATION.md).

The latest public plugin release is listed on the [GitHub releases page](https://github.com/Plattnericus/CS2CasesPlugin/releases).

The plugin embeds the same generated pack. It renders all catalog skins from bundled weapon
maps and includes the 22 case PNGs documented in `src/main/resources/case-art/SOURCE.md`.
It does not bundle the old Fusion overlay, the separate skin archive or its custom fonts.
Runtime export preserves server-owned assets while removing the three retired glyph images
and their font providers from old installations.

[Installation and controls](../README.md) · [Legal information](../LEGAL.md).
Contact: **info@plattnericus.dev**.
