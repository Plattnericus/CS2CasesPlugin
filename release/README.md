# MCCases verified downloads

Current build: **1.2.6**.

Download the production plugin and matching resource pack from the
[MCCases 1.2.6 GitHub release](https://github.com/Plattnericus/CS2CasesPlugin/releases/tag/v1.2.6).
Verify the downloads with [SHA256SUMS-1.2.6](SHA256SUMS-1.2.6).
- [1.2.6 verification report](../docs/RELEASE-1.2.6-VERIFICATION.md).

The plugin embeds the same generated pack. It renders all catalog skins from bundled weapon
maps and includes the 22 case PNGs documented in `src/main/resources/case-art/SOURCE.md`.
It does not bundle the old Fusion overlay, the separate skin archive or its custom fonts.
Runtime export preserves server-owned assets while removing the three retired glyph images
and their font providers from old installations.

[Installation and controls](../README.md) · [Legal information](../LEGAL.md).
Contact: **info@plattnericus.dev**.
