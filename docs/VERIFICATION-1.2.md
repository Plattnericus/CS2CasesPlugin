# Verification — MCCases 1.2.0

Executed on 2026-10-08 with Java 25.0.2, Paper 26.3 build 159 beta, vanilla Minecraft 26.3 and SQLite. The server, world, database and two offline client profiles were isolated in a temporary localhost installation. No production server or normal client profile was changed.

## Build and assets

- `bash gradlew build devChecks --console=plain` passed. The `check` task runs the custom `verifyFeatures` executable; the JUnit task has no test sources.
- All 55 weapon IDs have an obtainable skin, a rig and three distinct named inspect profiles: 20 knives, 35 ordinary weapons, 165 profiles. The catalog loads 743 skins in 22 cases, including the new CS20/Classic Knife contents. Existing case pools and skin IDs remain intact.
- 451,488 finite joint-frame checks cover all timelines, both hands, eye/body anchors and pack/fallback transforms. Full-duration camera bounds use 70° FOV and a 4:3 screen. Held-pose continuity, parent cycles, nonfinite/bounded settings and malformed animation rejection were checked.
- The resource pack contains 1,233 nonempty inspect layer models and 14,585 textured cuboids. Every model JSON parses and every inspect texture reference resolves. This validates all skin asset references; in-game captures use one representative finish per weapon model.
- Case-guide checks cover full dealer case + matching key price, missing offers, two maximum integer prices without overflow, weighted EV and exact knife/favorite probabilities.
- Existing commerce/opening tests passed: injected SQL rollback/retry, concurrent buyers, idempotent item claims, reservations, schema migrations, twenty independent persisted outcomes, opening caps/recovery and normal/gold trade-in rules. The catalog check generated 5,500 rewards/reels; 45 language schemas and legacy animation compatibility also passed.
- `python3 -m unittest discover -s scripts -p 'test_*.py'` passed both launcher tests.
- Compatibility compilation against `io.papermc.paper:paper-api:26.2.build.129-stable` passed. The release JAR was built against 26.3 build 159. Its metadata identifies 1.2.0 and Plattnericus, its embedded pack matches the separate ZIP, and development checks/capture classes are excluded.

## Actual client visual coverage

The Minecraft framebuffer capture harness records the game's rendered image. These are actual vanilla client frames, not an offline model renderer. Contact sheets and capture metadata are supplied in `release/MCCases-Visual-Checks-1.2.0.zip`.

| Coverage | Actual captures |
| --- | ---: |
| 35 weapons × 3 variants × 3 ticks × owner/observer | 630 |
| 20 knives × 3 variants × 13 views | 780 |
| Total pose captures | 1,410 |

Every model/variant is captured at ticks 8, 22 and 40 for the owner and front observer. Every knife additionally has left/right/rear observer views, left-hand owner/observer views, and both F5 cameras in body-hand mode. The F5 captures use the left-hand setting and side observer position to avoid the other player blocking the front camera. Contact sheets were visually inspected across all 55 models. Automated full-timeline geometry checks cover frames between those sample poses.

Frozen pose captures start the real inspect service, pause its scheduler and apply the production transform at the requested tick. Visibility checks ensure that the private owner scene does not leak to the observer. Separately, all 55 models run their first variant with the normal scheduler/interpolation through natural completion. Eight timestamped owner/observer pairs per run are provided as motion GIFs/strips; these are sampled motion evidence, not full-frame-rate video. Each run verifies that no ItemDisplay remains afterwards.

Visual checks found and corrected narrow-screen clipping, four pistols produced from already-paired Dual Berettas artwork, paired dagger spacing and inadequate F5 camera settling time. Classic Knife had a model but no obtainable skin; the CS20 contents close that catalog gap. Corrected captures replace the earlier frames in the evidence manifest.

## Runtime and menus

The final JAR enabled with all 743 skins and 22 cases. Genuine Paper ItemStacks/PDC passed the item-payment, storage/offhand, capacity, receipt and journal checks; these use controlled player proxies and are distinct from connected-client gameplay checks.

With both actual clients connected, the case guide passed value/full-price order, favorite/specific knife filters, preview/back preservation, reset, zero budget, exact case-ID search and hotbar protection. Budget `0` and search `cs20` were also sent through the actual Minecraft client chat connection and produced the expected menus. English/German GUI screenshots preserve the displayed price, EV, points per Diamond and formula. The menu's misleading physical-item right-click hint was removed.

The commerce regression passed an 81-skin paginated picker, synchronized protected offers, both confirmations, a 320-Emerald purchase from 400 physical Emeralds, exactly 65 Emeralds of partial payout, a full-inventory retry with no extra payout, and six independent openings across two players while direct trading remained available. Gold regression passed two five-Covert contracts, atomic consumption, rare-special outputs and correct ADMIN/normal announcement markers.

The full integration audit passed forged-marker protection, cosmetics preserving original material/damage/enchantments/metadata, disabled shortcut cleanup, dealer payment/capacity/stale-offer handling, journal recovery, dealer protection, gallery/slot transitions, F5 anchor/F hotkey/sneak swap, async preview cancellation, SQLite persistence and two real concurrent case reels. The original inventory was restored afterwards.

Additional two-client checks passed private/public scene visibility, body yaw/hand anchoring, language policy and complete stop cleanup; inventory-mode switching rejected stale views and protected icons. An actual client disconnect during a frozen Butterfly inspect also removed every owner/public display before any natural completion could occur. With the pack disabled, Butterfly, Shadow Daggers and AK-47 were captured from both clients and left no BlockDisplay after stop.

An initial guide test sent multiple clicks in one tick and hit the intended duplicate-click guard; the test now spaces clicks across ticks and passed. The first full-audit attempts lacked their required equipped knife/dealer fixtures; those fixtures were supplied and the complete rerun passed. These were development harness/setup failures, not unresolved filter or gameplay failures.

## Practical limits

The models and animation gestures adapt the project's artwork to Minecraft cuboids and display entities. They are not a literal CS mesh/animation/hand-rig port. Minecraft arms retain their vanilla behavior; `/inspect hand` is required for F5 because Paper cannot read client camera mode. World geometry can occlude displays. Arbitrary zoom, custom anchors/packs, high-player-count performance, latency extremes, MySQL runtime and interference by other plugins were not tested. Paper 26.3 build 159 is a beta build. No claim of universal bug-free behavior is made.

See [rig and case-guide controls](INSPECT-AND-CASE-GUIDE-1.2.md), [condensed test output](VERIFICATION-1.2.log) and [release checksums](../release/SHA256SUMS-1.2.0).
