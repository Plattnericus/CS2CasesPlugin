# MCCases 1.3.0 verification

Date: October 17, 2026 · Paper 26.3 build 159 · vanilla Minecraft 26.3 clients · Java 25 (Temurin 25.0.2) · macOS

This report records the checks that were actually run for 1.3.0. It is not a guarantee for other
server versions, plugins, shader packs or hardware.

## Offline build checks

`bash gradlew build devChecks` passed with every check task:

| Task | Result |
| --- | --- |
| `verifyFeatures` | 815 skins, 22 cases, 5,500 rewards/reels, weighted odds, wear boundaries, journals; all 63 rigs / 362 profile timelines; marketplace, trade, opening, trade-up and trade-in suites |
| `verifyHeld` | 55 weapons / 322 hand poses (grip in hand, arm signatures, handedness, 70° framing); 66 held-pose inspects start and end in the hold, rig layers return to place, clearance from the player model in seven head poses (furthest ahead of the fist looking ahead: 0.11 blocks); 815 skins with hard-edged sprites and rigs, skin-arm view models and scene layers; shader varyings and tint marks |
| `verifyPack` | 815 skins / 1,377 inspect layers / 204,600 opaque rim faces, catalog-rendered artwork, 22 case images with source hashes, glove hand anchors |
| `verifyResourcePackInstaller` | Pack provenance, deterministic merge, server asset preservation, atomic failure handling |
| `scripts/test_dev.py` | Dev session cleanup and build artifact selection |

## Real-client visual checks

`scripts/visual_checks.py` on the isolated dev session (DevTester and DevObserver, both vanilla 26.3
with the generated pack):

| Mode | Frames | Result |
| --- | --- | --- |
| `held` | 55 | Every weapon in the skin arms, 3D rig, grip in the palm |
| `inspect --all-animations` | 468 | Knife, flip (blade fold), butterfly (handle swings), reverse grip, push daggers, pistol (slide), revolver, SMG, rifle and sniper (bolt), shotgun (pump), box magazine |
| `transitions` | 48 | Start and end hand over without a dip, blink or double image |
| `others` | 114 | Owner F5 front/back and the second client's view: crossbow hold, weapon turned in the hands |
| `use` | 12 | Bow drawn, crossbow charging and loaded |
| `third` | 110 | F5 holds of every weapon |
| `left` | 5 | Mirrored left-handed view model |
| `cases` | 24 | Case menu, world reel and the second client's view |

Additionally checked with frame-by-frame captures: sneak + right click (short and long sneak),
crouching and standing up during an inspect, and holding right click during an inspect (no arrow fired).

## Paper runtime audits

`MCCases-DevChecks` on the same Paper 26.3 server:

| Command | Result |
| --- | --- |
| `mccasesdevcheck items` | PASS |
| `mccasesdevcheck DevTester commands` | PASS |
| `mccasesdevcheck DevTester` (gallery and hotbar input) | PASS |
| `mccasesdevcheck DevTester full` | PASS (shop, signed items, cosmetics, equipment, menus, dealer, inspect inputs incl. body-hand scene, previews, SQL, parallel openings) |
| `mccasesdevcheck DevTester observer DevObserver` | PASS (held-pose riders private to the owner, owner F5 copy, body-hand scene only for the observer, cleanup) |
| `mccasesdevcheck DevTester commerce DevObserver` | PASS |
| `gold`, `nine`, `spam`, `tradein-ui`, `choices` | PASS |
| `random-tradeup`, `guide` | PASS when run on a freshly restarted server; both failed when run directly after the other audits in the same session, because those leave trade-in selections and filters behind |
| `recovery` | PASS: pending rewards were recovered after restart and reconnect (no `PENDING` rows left) |

Not covered: MySQL/MariaDB, Windows/Linux clients, shader packs (Iris and similar replace the core
shaders the skin arms need), large player counts and other plugins.
