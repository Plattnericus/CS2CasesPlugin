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
| `resourcepack/` | Pack assets for merging into a server pack |
| `build/` | Generated output; new files ignored, historical tracked outputs retained |

## Build

You need **JDK 25** and a network connection for the first Gradle run.
The wrapper downloads dependencies; a separate Gradle installation is not required.

```sh
bash gradlew build
```

On Windows, use `gradlew.bat build`. Output is written to
`build/libs/MCCases-1.1.0.jar` and `build/distributions/MCCases-ResourcePack-1.1.0.zip`.
The pack ZIP is also embedded in the plugin JAR.

| Task | Purpose / output |
| --- | --- |
| `bash gradlew build` | Build the plugin and run feature checks |
| `bash gradlew verifyFeatures` | Check catalog, reward selection, translations, inventory queries, journal and timelines |
| `bash gradlew resourcePack` | Export the default catalog's pack |
| `bash gradlew generateAssets` | Regenerate weapon layers and pattern PNGs in the default resources |
| `bash gradlew previewSheet` | Skin and map contact sheets, plus seed statistics in `build/preview/` |
| `bash gradlew inspectFilmstrip` | Contact sheets and GIFs of inspect variations in `build/filmstrip/` |
| `bash gradlew inspectFilmstrip -PfilmstripMovies=false` | Run the same framing checks and generate contact sheets without GIFs |
| `bash gradlew devChecks` | Build the separate integration check plugin at `build/libs/MCCases-DevChecks-1.1.0.jar` |
| `bash gradlew dumpPalette` | Print Minecraft's map palette for the renderer |

Override the API dependency with `-PpaperApi=<Maven-coordinate>`.
Building against another API does not replace runtime checks on that Paper version.

Feature checks cover the default catalog's 713 skins and 21 cases, 5,250 reward and reel checks,
weighted drop chances, float limits, regional language fallbacks and 46 animation timelines.
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

`MCCases-DevChecks-1.1.0.jar` belongs only on a local test server. It is not embedded in the release
JAR and should not be included in `release/`. The check plugin requires MCCases and permission
`mccases.admin`.

```text
mccasesdevcheck DevTester
mccasesdevcheck DevTester inventory
mccasesdevcheck DevTester full
mccasesdevcheck DevTester observer DevObserver
mccasesdevcheck DevTester commerce DevObserver
```

The first command checks `/inventory`: hotbar changes work normally both while sneaking and
standing. Over a hologram target, they change pages while preserving the held slot.
The gallery stays open and is fully removed at exactly ten blocks of distance.
The check then verifies normal crossbow-to-bow switching and inspect cancellation.

`inventory` checks the complete switch from `/inventory` hologram to `/inventory vanilla`
chest menu and back. The previous display must be removed, and number-key swaps and item
drags must not move skins or insert items into the chest menu. It requires a second connected
client with a loaded profile to verify that a pending request for another player's collection
cannot replace the subsequently opened vanilla menu.

`full` additionally checks shop payments and rollback, signed items, inventory protection,
cosmetic restoration, equipment, menus, dealers, inspect inputs, previews, SQLite transactions
and a complete case opening. The physical inventory is restored; the opening check leaves
one additional `TEST` skin. When the inventory shortcut is disabled, checks verify that
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
mccasesdevcheck DevTester gold
mccasesdevcheck DevTester recovery
```

`items` uses genuine Paper ItemStacks and PDC with controlled player/inventory proxies; it needs
no connected player. It covers storage/offhand, armor exclusion, insufficient/negative/overflow
payment, full/partial capacity, saved idempotent receipts, twenty signed case/key pairs, test
provenance and legacy/current journals. This is a runtime API test, not a gameplay test.

`gold` creates five Covert fixtures for each of an ADMIN and a normal CASE-origin contract,
executes the actual service, checks atomic input consumption and rare-special output, and checks
the SQL announcement marker (zero for admin, one for the normal contract). Outputs are removed
afterward. Inspect both clients' chat to verify that only the normal contract announces.

`recovery` consumes two signed test pairs and waits for two persisted PENDING outcomes. After
READY RECOVERY, disconnect the player before the animation completes, then stop the isolated server.
Restart and reconnect;
the same two UUIDs must become OWNED and the opening records must remain unique. This command
intentionally leaves the test rewards for inspection.

See [VERIFICATION-1.1.md](VERIFICATION-1.1.md) for the checks actually run for this release.

Remove temporary screenshots, extra check JARs and test fixtures after validation.
Keep source checks available for later development. The session's temporary world, database and
client data are removed when its watched desktop app closes.

These checks cover SQLite and the local default setup. MySQL/MariaDB, different server
configurations and high player counts require their own runtime checks.
