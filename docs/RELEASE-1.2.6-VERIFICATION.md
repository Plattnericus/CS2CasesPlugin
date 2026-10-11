# MCCases 1.2.6 verification

Verified October 11, 2026 with Temurin 25.0.2, Paper 26.3 build 159 beta and two
connected Vanilla Minecraft 26.3 clients.

## Changes verified

- The pack contains all 815 rendered skin finishes, 22 case textures and 362 inspect
  variants across 63 knife, firearm and glove models. Firearms and knives have six
  variants each; gloves retain four.
- Held firearms and knives use separate first- and third-person transforms. The first-person
  model includes a compact pixel forearm and hand; third-person keeps the player's Minecraft
  arm. The pack-based inspect scene also attaches that arm to the animated weapon grip. Three
  shared arm profiles cover guns, normal-grip knives and reverse-grip knives.
- Case-opening reels use 1.5 times the previous item size and spacing. The camera fitting
  keeps the one-to-nine opening layout centered and non-overlapping.
- The case PNGs come from the supplied resource-pack archive. Their source archive hash and
  per-image hashes are recorded in [the artwork source record](../src/main/resources/case-art/SOURCE.md).
  No new case or skin artwork was generated; the small forearm and sleeve swatches come from
  hand-written pixel code.

## Checks

```sh
JAVA_HOME=<JDK-25> bash gradlew clean build devChecks inspectRigPreview
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/verify_release_live.py --root .dev/runtime --output <evidence-dir>
python3 scripts/capture_inspect_motion.py --root .dev/runtime --output <evidence-dir> --only karambit,butterfly,ak47,awp --all-variants
python3 scripts/capture_held_models.py --root .dev/runtime --output <evidence-dir> --only talon,butterfly,ak47,awp
python3 scripts/capture_opening_layout.py --root .dev/runtime --output <evidence-dir>
```

- The clean build and feature, pack, installer, item-rig and animation checks succeeded.
  Checks cover all 815 skins, 22 case images, 63 rigs, 362 timelines, animated arm placement,
  framing, grip anchors, source hashes, retired glyph cleanup and the complete one-to-nine camera layout.
- All five Python launcher tests passed.
- All twelve Paper live suites passed with both clients connected: 155 command checks,
  complete item and inventory audit, observer separation, two-player commerce, rare and
  normal trade-ups, menus, 32 real random trade-up contracts, case guide, nine simultaneous
  openings, queue controls and spam protection.
- Real clients captured 24 inspect variants across Karambit, Butterfly, AK-47 and AWP;
  after the animated-arm change, a further 48 live frames cover all six variants of Talon,
  Butterfly, AK-47 and AWP from both owner and observer clients;
  32 held-model views across Talon, Butterfly, AK-47 and AWP in both hands and camera modes;
  and all nine case counts from owner and observer views (18 screenshots).
- The resource-pack export command completed during the live command suite.

The first sequential live run hit the commerce check's 30-second runtime timeout. The isolated
retry passed, followed by a successful repeat of all twelve suites. The successful full rerun is
the recorded release result. Gradle's standard `test` task is `NO-SOURCE`; the project checks run
through `devChecks`, `verifyFeatures`, `verifyPack` and `verifyResourcePackInstaller`.

After adding the animated inspect arm, the complete local Gradle checks and 48 additional
Minecraft captures passed. A post-change attempt to repeat the live command suites did not produce
the harness completion marker within its 120-second deadline, so that attempt is recorded as
incomplete rather than as a pass. The earlier successful twelve-suite run predates the arm-only
inspect rendering change.

Local client screenshots and logs are saved under `.dev/runtime/visual-live-motion-final-1.2.6/`,
`.dev/runtime/visual-live-held-final-1.2.6/`, `.dev/runtime/visual-live-openings-final-1.2.6/` and
`.dev/runtime/live-final-optimized-1.2.6-rerun/`. The animated-arm captures are in
`.dev/runtime/visual-inspect-arms-1.2.6/`.

## Release files

The production JAR and standalone pack are attached to the [1.2.6 GitHub release](https://github.com/Plattnericus/CS2CasesPlugin/releases/tag/v1.2.6).
Their SHA-256 values are recorded in [SHA256SUMS-1.2.6](../release/SHA256SUMS-1.2.6).
The binary archives are kept out of Git history. The development-only integration checker is
excluded from the production JAR and release downloads.
