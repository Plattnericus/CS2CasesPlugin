# Permanent Fusion HD source overlay

Every normal `bash gradlew build` and `bash gradlew resourcePack` merges this overlay
with the freshly generated MCCases namespace. The resulting Fusion ZIP is embedded
in the production JAR. `/csadmin exportpack` uses the same Java implementation and
retains installed server artwork. No Downloads file or manual Python image merge is
needed for future builds.

Imported from the supplied `MCCases-ResourcePack-Fusion-HD-1.2.0.zip`:

- Seven original PNG glyphs: logo, kirbis, hirsch, user, tachometer, globe and prosse.
- The original `assets/minecraft/font/default.json` provider definitions.
- The original `pack.png`.

All nine asset files retain their exact bytes. `SOURCE.json` records provenance and
SHA-256 hashes. The original skin and inspect PNGs are retained separately in `resourcepack/artwork`.
Old 1.2.0 model definitions are replaced as a unit by current
128px sprites and inspect geometry, including correct Karambit/Talon orientation.
This prevents old textures from being paired with incompatible new UVs or pivots.

To replace server assets, edit the files here and update their source hashes in
`SOURCE.json`. Keep custom artwork outside `assets/mccases/`; that namespace is
managed by the catalog/exporter. `verifyFusionPack` checks provenance, every preserved
file, model/texture references, bitmap-font references and dimensions, repeated
installation, server export receipts and atomic error recovery. A missing font texture
fails the build; it does not silently fall back to the standard pack.

Build outputs:

```text
build/distributions/MCCases-ResourcePack-Fusion-HD-<version>.zip
build/distributions/MCCases-Fusion-Overlay.zip
build/libs/MCCases-<version>.jar
```

The standard ZIP is an intermediate generated input. No image generation or raster
editing is performed on the supplied server artwork. Rights and licensing remain as
stated in [the legal information](../../LEGAL.md) and [notices](../../THIRD_PARTY_NOTICES.md).
