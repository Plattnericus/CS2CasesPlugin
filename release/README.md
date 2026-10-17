# MCCases 1.3.0 build artifacts

The 1.3.0 JAR and resource pack are a matched pair and were built and verified locally. Build
artifacts are kept out of Git; published builds are on the
[GitHub releases page](https://github.com/Plattnericus/CS2CasesPlugin/releases).

Recreate them with `bash gradlew build`; the outputs are `build/libs/MCCases-1.3.0.jar` and
`build/distributions/MCCases-ResourcePack-1.3.0.zip`. Verify local copies with
[SHA256SUMS-1.3.0](SHA256SUMS-1.3.0) and see the [1.3.0 verification report](../docs/RELEASE-1.3.0-VERIFICATION.md).

The plugin embeds the same generated pack: skin sprites, 3D weapon rigs and skin-arm view models
rendered from the catalog and bundled weapon maps, the 22 case PNGs documented in
`src/main/resources/case-art/SOURCE.md`, and the Minecraft 26.3 entity and item shader programs.
Runtime export preserves server-owned assets.

[Installation and controls](../README.md) · [Legal information](../LEGAL.md) ·
Contact: **info@plattnericus.dev**
