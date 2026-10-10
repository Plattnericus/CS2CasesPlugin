# Development and local testing

[Back to the plugin documentation](../README.md)

## Project structure

| Path | Contents |
| --- | --- |
| `src/main/java/` | Plugin source |
| `src/main/resources/defaults/` | Default configuration, catalog, translations and textures |
| `src/tools/java/` | Pack exporter, asset renderer and check tools |
| `scripts/` | Local development server and cleanup checks |
| `release/` | Plugin JAR and resource pack ZIP |
| `resourcepack/` | Historical exported assets; merge from the current release ZIP |
| `build/` | Generated output; new files ignored, historical tracked outputs retained |

## Build

You need **JDK 25** and a network connection for the first Gradle run.
The wrapper downloads dependencies; a separate Gradle installation is not required.

```sh
bash gradlew build
```

On Windows, use `gradlew.bat build`. Output is written to
`build/libs/MCCases-1.2.2.jar` and `build/distributions/MCCases-ResourcePack-Fusion-HD-1.2.2.zip`.
The Fusion pack ZIP and a small source-overlay ZIP are embedded in the plugin JAR.
JDK 25 performs the entire merge; no Pillow, manual merge or Downloads path is needed.
`resourcepack/fusion/` is the permanent source for the seven font textures, their
font definition and pack icon. The standard ZIP is only an intermediate build artifact.
`PackDistribution` uses the same merge for runtime exports; startup preserves exported
server catalogs and regenerates edited packs from the actual loaded catalog when needed.

| Task | Purpose / output |
| --- | --- |
| `bash gradlew build` | Build the plugin and run feature checks |
| `bash gradlew verifyPack` | Verify all exported sprites, rim UVs and both hand transforms |
| `bash gradlew inspectRigPreview` | Software filmstrips of all 252 articulated variants |
| `bash gradlew verifyFeatures` | Check catalog, reward selection, translations, inventory queries, journal and timelines |
| `bash gradlew resourcePack` | Always generate current MCCases assets and merge the project Fusion overlay |
| `bash gradlew fusionOverlay` | Package original custom font/icon sources without changing bytes |
| `bash gradlew verifyFusionPack` | Check exact asset preservation, references, deterministic merging, upgrades and failure recovery |
| `bash gradlew standardResourcePack` | Intermediate generated assets used as the input to Fusion |
| `bash gradlew generateAssets` | Regenerate weapon layers and pattern PNGs in the default resources |
| `bash gradlew previewSheet` | Skin and map contact sheets, plus seed statistics in `build/preview/` |
| `bash gradlew inspectFilmstrip` | Contact sheets and GIFs of inspect variations in `build/filmstrip/` |
| `bash gradlew inspectFilmstrip -PfilmstripMovies=false` | Run the same framing checks and generate contact sheets without GIFs |
| `bash gradlew devChecks` | Build the separate integration check plugin at `build/libs/MCCases-DevChecks-1.2.2.jar` |
| `bash gradlew dumpPalette` | Print Minecraft's map palette for the renderer |

Override the API dependency with `-PpaperApi=<Maven-coordinate>`.
Building against another API does not replace runtime checks on that Paper version.

Version 1.2.1 exports skin/inspect textures at 128px with binary silhouette coverage and
fractional premultiplied alpha filtering. Karambit and Talon turn only at presentation time;
pattern sampling and saved classifications retain their original UV coordinates.
`scripts/generate_inspect_profiles.py` reproduces all 252 stock timelines. The v2 migration
baseline recognizes unchanged 1.2.0 ring profiles; customized timelines remain editable.
Skin `traded` history is persisted separately from `origin` and `source` in schema 4.
Recorded direct-trade instance IDs are backfilled; market purchases preserve the flag.
`SkinPresentationChecks` covers real v3 migration, repeated trades/restarts and en/de/it source lore.

World wheels use `opening.world.scene-scale: 3.0`, applied after the compact layout so
reflow cannot cancel the enlargement. This scales all display transforms and interaction
boxes uniformly for every grid size 1–9 without increasing the moving entity window.
The enlarged world grid may extend beyond the opener’s camera; step back for an overview.
Offline checks compare every enlarged cell against the compact geometry; the real-Paper
audit validates settings fallback, scaled glass, bounded item displays and final cleanup.

For focused two-client visual evidence on an accepted isolated test server:

```sh
python3 scripts/capture_inspect_audit.py --root <session> --output <captures> --only karambit,talon
python3 scripts/capture_inspect_motion.py --root <session> --output <motion> --only karambit,talon --all-variants
python3 scripts/capture_held_models.py --root <session> --output <held> --only karambit,talon
python3 scripts/capture_opening_layout.py --root <session> --output <wheels>
```

These scripts require the local RCON/capture harness and two development clients. They operate
on test fixtures and must not be pointed at a production world. Held-model captures wait for
vanilla's re-equip animation before taking the screenshot.

Feature checks cover the default catalog's 815 skins and 22 cases, 5,500 reward and reel checks,
weighted drop chances, float limits, regional language fallbacks and 46 legacy animation timelines.
They also validate all 252 individual profiles/63 rigs, full-timeline 70°/4:3 framing in both hands
with pack/fallback geometry, joint continuity, invalid configuration and case-guide probabilities.
Filmstrips check each tick for clipped models at a 70° vertical field of view and 16:9 aspect ratio.
`hand_pack_*` uses a geometric avatar reference. These render views are not Minecraft client
screenshots.

`verifyFeatures` also checks trade confirmation revisions and offer limits, and runs real SQLite
transactions for Emerald purchases/claims: concurrent buyers, incorrect owners, preserved skin
values, partial and repeated payouts, SQL failure rollback/retry, trade reservations, transaction
history, migration from schemas 1/2 and database reopening. Signed item and inventory-capacity
checks run separately on actual Paper through `mccasesdevcheck items`.
The SQLite driver is included only on the check classpath and is not distributed with the plugin;
Paper supplies the driver at runtime.

Defaults use English for all players: `language: en` and `client-language: false`.
The inventory shortcut is disabled with `skin-inventory-item.enabled: false`.
`/inventory` opens the hologram collection; `/inventory vanilla` opens the normal chest menu.
Existing configuration files retain their settings, so check these values on older installations.

## Isolated macOS session

`scripts/dev.py` starts Paper and a vanilla client in `.dev/runtime/`. Requirements:

- macOS, Python 3 with PyYAML, and Java 25;
- Minecraft 26.3 installed in `~/Library/Application Support/minecraft/`;
- available local ports 25565 and 8165;
- a built plugin and pack ZIP.

```sh
bash gradlew build devChecks
python3 scripts/dev.py prepare
```

Before starting the server for the first time, read the Minecraft EULA and, after accepting it,
set `eula=true` in `.dev/runtime/server/eula.txt`.
The session watches the process ID of a running desktop app. When that process exits,
the server and clients stop.

```sh
python3 scripts/dev.py start <App-PID>
python3 scripts/dev.py status
python3 scripts/dev.py observer
python3 scripts/dev.py console 'csadmin info'
python3 scripts/dev.py restart
python3 scripts/dev.py stop
```

| Action | Effect |
| --- | --- |
| `prepare` | Prepare Paper, the current build, default files, pack and missing client assets |
| `start <PID>` | Start the supervisor and connect the vanilla client after the server is ready |
| `status` | Show process status and server readiness |
| `observer` | Start a second isolated vanilla client, `DevObserver`, with French Minecraft settings |
| `console '<Command>'` | Send a command without its leading slash to the server console |
| `restart` | Restart clients and server with the current build; preserve local player data |
| `stop` | Stop the session and remove all of `.dev/runtime/` |
| `cleanup-stale` | Remove leftovers from a session that is no longer running |

The first profile is named `DevTester`. The server and pack web server bind to `127.0.0.1`;
the server uses offline mode for this local session. Launcher tokens and regular Minecraft
settings are not changed. Available assets are reused, and missing files are downloaded
with checksum verification.

A one-time LaunchAgent removes leftovers at the next login. Process fingerprints prevent
reused PIDs from terminating unrelated processes. `stop` also removes the test world and
database; source files, `build/` and `release/` are preserved.

The supervisor restarts server or client processes that exit unexpectedly while the selected
desktop app remains open. It also watches the optional second client.
`restart` preserves player data and restarts all enabled clients with the current build.

```sh
python3 -m unittest discover -s scripts -p 'test_*.py'
```

## Integration checks on Paper

`MCCases-DevChecks-1.2.2.jar` belongs only on a local test server. It is not embedded in the release
JAR and should not be included in `release/`. The check plugin requires MCCases and permission
`mccases.admin`.

```text
mccasesdevcheck DevTester
mccasesdevcheck DevTester inventory
mccasesdevcheck DevTester full
mccasesdevcheck DevTester observer DevObserver
mccasesdevcheck DevTester commerce DevObserver
```

The first command checks `/inventory`: hotbar changes work both while sneaking and
standing, including over a hologram target. Wheel/number-key input never changes pages;
the page buttons browse the collection.
The gallery stays open and is fully removed at exactly ten blocks of distance.
The check then verifies normal crossbow-to-bow switching and inspect cancellation.

`inventory` checks the complete switch from `/inventory` hologram to `/inventory vanilla`
chest menu and back. The previous display must be removed, and number-key swaps and item
drags must not move skins or insert items into the chest menu. It requires a second connected
client with a loaded profile to verify that a pending request for another player's collection
cannot replace the subsequently opened vanilla menu.

Before running `full`, give and equip an owned knife with `/csadmin giveskin` and `/csadmin equip`,
and spawn a dealer with `/csadmin shop spawn`. These are required test fixtures.

`full` additionally checks shop payments and rollback, signed items, inventory protection,
cosmetic restoration, equipment, menus, dealers, inspect inputs, previews, SQLite transactions
and two independent case openings. The physical inventory is restored; the opening check leaves
two additional `TEST` skins. When the inventory shortcut is disabled, checks verify that
remaining marked stars are removed from inventory, offhand and cursor, ordinary stars are
preserved, and the formerly reserved slot can be used normally.

`observer` requires two connected vanilla clients: the owner uses German Minecraft settings
and the observer uses French settings. With the default fixed English language, both must
see English plugin text despite their different client locales. With client language selection
enabled, each player must see the corresponding translation.
The check verifies real client locales, menu titles, item text with unchanged signatures and
amounts, scale, and separate visibility of first-person and body-hand scenes.
Let any active case opening finish before running it.

`commerce` requires two connected vanilla clients with loaded profiles. It creates 82 temporary
admin skin fixtures across two players, checks a three-page collection browser, retained selection,
protected hotbar/drag actions, shared reservations, synchronized offers, review and both confirmations.
It then buys an exact skin for 320 of 400 real inventory Emeralds, collects precisely 65 of 320
Emeralds into limited inventory space, verifies that a full-inventory retry adds nothing, and opens
three cases per player while starting and cancelling an independent direct trade. It waits for
all six rewards and both case/key inventories to finish. It restores physical inventories and
removes its admin skin fixtures; test openings remain in the development collection and the
seller's uncollected fixture claim remains in the isolated database.

Additional isolated-server commands:

```text
mccasesdevcheck items
mccasesdevcheck DevTester commands
mccasesdevcheck DevTester gold
mccasesdevcheck DevTester nine
mccasesdevcheck DevTester spam
mccasesdevcheck DevTester tradein-ui
mccasesdevcheck DevTester guide
mccasesdevcheck DevTester recovery
```

`commands` checks all player command roots/aliases, console and permission rejection,
invalid arguments, the admin command handlers and real menu dispatch. It injects SQL
failures for grants, edits, removals and equipment moves and verifies that errors are
reported, failed writes retain the previous state, and concurrent equipment requests
are rejected. It also checks malformed-config reload recovery, exports a server pack
and opens three test cases. Use it only on a disposable server: it creates fixtures,
modifies its local configuration temporarily and installs temporary SQLite triggers.
Expected injected exceptions appear in the server log; the final result is saved in
`plugins/MCCasesDevChecks/command-audit.txt`.

`items` uses genuine Paper ItemStacks and PDC with controlled player/inventory proxies; it needs
no connected player. It covers storage/offhand, armor exclusion, insufficient/negative/overflow
payment, full/partial capacity, saved idempotent receipts, twenty signed case/key pairs, test
provenance and legacy/current journals. This is a runtime API test, not a gameplay test.

`gold` creates five Covert fixtures for each of an ADMIN and a normal CASE-origin contract,
executes the actual service, checks atomic input consumption and rare-special output, and checks
the SQL announcement marker (zero for admin, one for the normal contract). Outputs are removed
afterward. Inspect both clients' chat to verify that only the normal contract announces.

`guide` checks case-value/full-price sorting, favorite and individual knife filtering,
preview/back state, zero budget, ID search, reset and hotbar protection. Menu clicks are
spaced across ticks so they respect the production duplicate-click guard. Pack item models
must be enabled for this fixture, because case IDs are read from their icon models.

`recovery` consumes two signed test pairs and waits for two persisted PENDING outcomes. After
READY RECOVERY, disconnect the player before the animation completes, then stop the isolated server.
Restart and reconnect;
the same two UUIDs must become OWNED and the opening records must remain unique. This command
intentionally leaves the test rewards for inspection.

See [MULTI-OPENING-VERIFICATION.md](MULTI-OPENING-VERIFICATION.md) for the current parallel-opening,
trade-in, texture and 252-animation checks. [VERIFICATION-1.2.md](VERIFICATION-1.2.md) records the earlier release checks.

The actual client capture tools are `src/tools/client/ClientCaptureHarness.java`,
`scripts/capture_inspect_audit.py`, `scripts/capture_inspect_motion.py` and
`scripts/build_visual_evidence.py`. They require two isolated connected clients, a separately
compiled test Java agent, a local RCON setup and the development check plugin. The agent
uses Minecraft's framebuffer/camera APIs and is excluded from the production plugin.
Pose captures pause the inspect task; motion captures leave the real scheduler running.
Use [the 1.2.2 Fusion report](RELEASE-1.2.2-VERIFICATION.md) for the current results.
[The 1.2.1 report](RELEASE-1.2.1-VERIFICATION.md) and earlier 1.2 reports remain
historical evidence.

Remove extra check JARs and test fixtures after validation; retain useful labeled test evidence.
Keep source checks available for later development. The session's temporary world, database and
client data are removed when its watched desktop app closes.

These checks cover SQLite and the local default setup. MySQL/MariaDB, different server
configurations and high player counts require their own runtime checks.
