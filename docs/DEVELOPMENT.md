# Development and local testing

[Back to the plugin documentation](../README.md)

## Project structure

| Path | Contents |
| --- | --- |
| `src/main/java/` | Plugin source |
| `src/main/resources/defaults/` | Default configuration, catalog, translations and textures |
| `src/main/resources/case-art/` | 22 case images and their source/checksum record |
| `src/tools/java/` | Pack exporter, asset renderer and check tools |
| `scripts/` | Local development server and cleanup checks |
| `release/` | Plugin JAR and resource pack ZIP |
| `build/` | Generated output |

## Build

You need **JDK 25** and a network connection for the first Gradle run.
The wrapper downloads dependencies; a separate Gradle installation is not required.

```sh
bash gradlew build
```

On Windows, use `gradlew.bat build`. Output is written to
`build/libs/MCCases-1.2.6.jar` and `build/distributions/MCCases-ResourcePack-1.2.6.zip`.
The generated pack is embedded in the plugin JAR. Skin and inspect images use the current
renderer; only the 22 documented case images are supplied bitmaps. `PackDistribution` merges
the generated catalog into an existing pack at runtime, preserves other server assets and
removes retired custom-font glyphs/providers from legacy installations.

| Task | Purpose / output |
| --- | --- |
| `bash gradlew build` | Build the plugin and run feature checks |
| `bash gradlew verifyPack` | Verify all rendered skins and inspect layers, 22 case images, rim UVs, held-arm assets and both hand transforms |
| `bash gradlew inspectRigPreview` | Software filmstrips of all 362 articulated variants |
| `bash gradlew verifyFeatures` | Check catalog, reward selection, translations, inventory queries, journal and timelines |
| `bash gradlew resourcePack` | Render every catalog skin and build the generated MCCases pack |
| `bash gradlew verifyResourcePackInstaller` | Check legacy glyph removal, server asset preservation and atomic updates |
| `bash gradlew generateAssets` | Regenerate weapon layers and pattern PNGs in the default resources |
| `bash gradlew previewSheet` | Skin and map contact sheets, plus seed statistics in `build/preview/` |
| `bash gradlew inspectFilmstrip` | Contact sheets and GIFs of inspect variations in `build/filmstrip/` |
| `bash gradlew inspectFilmstrip -PfilmstripMovies=false` | Run the same framing checks and generate contact sheets without GIFs |
| `bash gradlew devChecks` | Build the separate integration check plugin at `build/libs/MCCases-DevChecks-1.2.6.jar` |
| `bash gradlew dumpPalette` | Print Minecraft's map palette for the renderer |

Override the API dependency with `-PpaperApi=<Maven-coordinate>`.
Building against another API does not replace runtime checks on that Paper version.

Version 1.2.6 renders the catalog sprites and inspect layers at 128px from bundled weapon maps.
Exported coverage is opaque, mesh boundaries follow the source silhouette and held models use native palm
anchors in both hands. Three shared block-style forearm models cover first-person firearms, normal-grip knives and reversed-grip knives; third-person keeps the player model's own arm. Karambit and Talon turn only at presentation time; pattern sampling
and saved classifications retain their original UV coordinates. Their pivots match the
original finger-ring holes. `scripts/generate_inspect_profiles.py` reproduces all 362 stock
timelines. The v1/v2/v3/v4 migration baselines recognize unchanged older stock profiles;
customized timelines remain editable. The pack includes the Minecraft 26.3 item shader
baseline with a reserved face tint for simulated metal sheen; this is not world reflection.
Skin `traded` history is persisted separately from `origin` and `source` in schema 4.
Recorded direct-trade instance IDs are backfilled; market purchases preserve the flag.
`SkinPresentationChecks` covers real v3 migration, repeated trades/restarts and en/de/it source lore.

World wheels use 1.5× the previous icon scale and spacing. `opening.world.scene-scale: 3.0` remains an upper size limit. Camera
fitting sizes every wheel against the complete 3×3 grid, with fixed scale for
counts 1–9 at a given distance. Glass, icons, text and hitboxes share this scale.
The default strip shows five icons. Offline checks cover centering, non-overlap,
constant size and camera margins at distances 1.2, 3 and 6 blocks. Real-Paper
checks verify nine simultaneous outcomes, glass geometry and entity cleanup.

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
They also validate all 362 individual profiles/63 rigs, full-timeline 70°/4:3 framing in both hands
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
Choose persistent mode to keep the local server and client running independently of
the desktop app, including automatic restart if either process exits:

```sh
python3 scripts/dev.py start --persistent
```

Persistent mode preserves the world/database when stopped and installs a login starter.
`stop` removes that login starter. For an app-bound disposable session instead, pass
the process ID of a running desktop app; that mode stops when the app exits.

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
| `observer` | Start a second isolated vanilla client, `DevObserver`, with English Minecraft settings |
| `console '<Command>'` | Send a command without its leading slash to the server console |
| `restart` | Restart clients and server with the current build; preserve local player data |
| `start --persistent` | Keep the server and client running independently of an app; preserve dev data |
| `stop` | Stop the session; preserve persistent data or remove disposable session data |
| `cleanup-stale` | Remove leftovers from a session that is no longer running |

The first profile is named `DevTester`. The server and pack web server bind to `127.0.0.1`;
the server uses offline mode for this local session. Launcher tokens and regular Minecraft
settings are not changed. Available assets are reused, and missing files are downloaded
with checksum verification.

The app-bound mode installs a one-time cleanup LaunchAgent. Persistent mode installs a
login starter instead. Process fingerprints prevent
reused PIDs from terminating unrelated processes. `stop` removes the test world and
database only in app-bound disposable mode; source files, `build/` and `release/` are preserved.

The supervisor restarts server or client processes that exit unexpectedly while the selected
desktop app remains open. It also watches the optional second client.
`restart` preserves player data and restarts all enabled clients with the current build.

```sh
python3 -m unittest discover -s scripts -p 'test_*.py'
```

## Integration checks on Paper

`MCCases-DevChecks-1.2.6.jar` belongs only on a local test server. It is not embedded in the release
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
mccasesdevcheck DevTester choices
mccasesdevcheck DevTester random-tradeup
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

`choices` checks direct inventory/gallery/market/sale sort lists and category/rarity return paths.
`random-tradeup` runs 32 confirmed ten-input contracts through the real menus and SQL storage,
checking legal reward pools, source retention and variation across independent draws.

`guide` checks case-value/full-price sorting, favorite and individual knife filtering,
preview/back state, zero budget, ID search, reset and hotbar protection. Menu clicks are
spaced across ticks so they respect the production duplicate-click guard. Pack item models
must be enabled for this fixture, because case IDs are read from their icon models.

`recovery` consumes two signed test pairs and waits for two persisted PENDING outcomes. After
READY RECOVERY, disconnect the player before the animation completes, then stop the isolated server.
Restart and reconnect;
the same two UUIDs must become OWNED and the opening records must remain unique. This command
intentionally leaves the test rewards for inspection.

The actual client capture tools are `src/tools/client/ClientCaptureHarness.java`,
`scripts/capture_inspect_audit.py`, `scripts/capture_inspect_motion.py` and
`scripts/build_visual_evidence.py`. They require two isolated connected clients, a separately
compiled test Java agent, a local RCON setup and the development check plugin. The agent
uses Minecraft's framebuffer/camera APIs and is excluded from the production plugin.
Pose captures pause the inspect task; motion captures leave the real scheduler running.
Use [the 1.2.6 verification report](RELEASE-1.2.6-VERIFICATION.md) for the current results.

Remove extra check JARs and test fixtures after validation; retain useful labeled test evidence.
Keep source checks available for later development. The session's temporary world, database and
client data are removed when its watched desktop app closes.

These checks cover SQLite and the local default setup. MySQL/MariaDB, different server
configurations and high player counts require their own runtime checks.
