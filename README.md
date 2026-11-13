<div align="center">

# MCCases

**CS2-style cases, skins, inspects and trade-ups for Paper servers.**

Server-side only. No client mods. One plugin and one resource pack.

![Paper 26.3](https://img.shields.io/badge/Paper-26.3-2b6cb0?style=flat-square)
![Java 25](https://img.shields.io/badge/Java-25-c05621?style=flat-square)
![22 cases](https://img.shields.io/badge/Cases-22-6b46c1?style=flat-square)
![815 skins](https://img.shields.io/badge/Skins-815-b83280?style=flat-square)
![License](https://img.shields.io/badge/License-MCCases%20(non--commercial)-4a5568?style=flat-square)

[Install](#install) · [Quick start](#quick-start) · [How to play](#how-to-play) · [Commands](#commands-and-permissions) · [Configuration](#configuration) · [Resource pack](#resource-pack) · [Checks](#how-the-plugin-is-checked) · [Building](#building-from-source)

</div>

> MCCases is not an official Minecraft or Valve product.

## Contents

| For server owners | For players | For developers |
| --- | --- | --- |
| [Requirements](#requirements) | [Quick start](#quick-start) | [Building from source](#building-from-source) |
| [Install](#install) | [Opening cases](#opening-cases) | [How the plugin is checked](#how-the-plugin-is-checked) |
| [Configuration](#configuration) | [Your collection](#your-collection) | [Project layout](#project-layout) |
| [Resource pack](#resource-pack) | [Trade-up](#trade-up) | [License](#license) |
| [Updating](#updating) · [Troubleshooting](#troubleshooting) | [Trading and market](#trading-and-market) | |

## Features

| | |
| --- | --- |
| **Cases** | 22 cases and 815 skins across 35 weapons, 20 knives and 5 glove types |
| **Real skin values** | Float, wear, pattern seed, StatTrak, Doppler phases, Fade percentage and Blue Gem tiers |
| **Openings** | A CS2-style reel in the world that nearby players can watch, up to nine at once per player |
| **Inspects** | Your own skin arms hold every weapon; 102 natural held inspects |
| **Collection** | Favorites, sorting, filters and search |
| **Trade-up** | Contracts with published odds and a result window |
| **Economy** | Player trading, an emerald market and a case dealer that also lives in villages |
| **Storage** | SQLite or MySQL/MariaDB, with recovery of interrupted openings and payments |

## Requirements

| | |
| --- | --- |
| Server | Paper 26.3 |
| Java | 25 |
| Players | The MCCases resource pack (see [Resource pack](#resource-pack)) |

The resource pack draws the skins and cases; without it items show vanilla icons. Menus work without
it. No other plugins and no client mods are needed.

## Install

1. Download `MCCases-<version>.jar` and `MCCases-ResourcePack-<version>.zip` from the
   [latest release](https://github.com/Plattnericus/CS2CasesPlugin/releases/latest).
2. Put the JAR into `plugins/` and start the server once. The default files appear in `plugins/MCCases/`.
3. Give players the resource pack. The easiest way is the built-in distribution: open
   `plugins/MCCases/config.yml` and set

   ```yaml
   resource-pack:
     enabled: true
     distribution:
       enabled: true
       public-url: "http://your-server-address:8165"   # players must be able to reach this
   ```

   Open port 8165 (TCP) on your firewall. See [Resource pack](#resource-pack) for other options.
4. Run `/csadmin reload` or restart the server, then check `/csadmin info`.
5. Optional: place a dealer with `/csadmin shop spawn` so players can buy cases and keys for emeralds.

## Quick start

Give yourself a case and a key (you need operator rights):

```text
/csadmin givecase YourName kilowatt_case 9
/csadmin givekey YourName case_key 9
```

1. Type `/cases`, pick the case and click **Open**, or use `/cases open kilowatt_case 1`.
2. Watch the reel. Each opening uses one case and one key.
3. Type `/skins` to see what you own.
4. Hold a skin and press **F** to inspect it.
5. Type `/tradeup`, pick ten skins of the same rarity and trade them up for one of the next rarity.

## How to play

### Opening cases

`/cases` lists every case. Open one, or several at once with **Open 9 cases together**. You can open
up to nine cases at the same time; anything beyond that waits in line (up to 1000 per command). Other
players nearby can watch the reels. Results are decided and saved before the animation starts, so a
disconnect or restart never loses a drop. `/openings` shows running openings and your last results;
`/cases cancel` drops the waiting ones.

### Your collection

`/skins` (or `/inventory`) opens your collection. `/inventory vanilla` opens it as a normal chest menu.

| Action | Result |
| --- | --- |
| Left-click a skin | Inspect it |
| Right-click a knife | Equip it |
| Shift-click | Mark as favorite |
| Sort and Filter buttons | Order by rarity, name or float; filter by rarity, wear and StatTrak |
| Bottom row | Market, trade-up and trading with other players |

A skin takes one of three slots: knife (shown on swords), bow or crossbow. Skins are cosmetic and
never change damage or durability.

### Inspecting

| Input | Action |
| --- | --- |
| **F** | Inspect the held skin |
| Sneak + right-click | Inspect after you stand up |
| `/inspect` | Inspect the held skin or the equipped knife |

### Trade-up

`/tradeup` follows the CS2 contract rules:

- Ten skins of the same rarity make one skin of the next rarity.
- All ten must be normal, or all ten StatTrak.
- Five Covert skins make one rare special item (knife or gloves).
- The result comes from the cases of your input skins, weighted by how many inputs came from each case.
- **Possible outcomes** shows every result with its exact chance before you confirm.
- The result's float is the average of your inputs' floats, each scaled to its own skin's float range.

Pick skins in the list. A selected skin glows, gets a green check in its name and shows its position
in the contract as a number. Filter by weapon, rarity and StatTrak, or use **Search** to type a name.
Favorites are left out of **Auto select**. After you confirm, the menu closes and a window shows the
skin you received.

### Trading and market

- `/trade <Player>` opens a trade. Both players must accept the same offer. Any change resets both
  confirmations and starts a two-second review. A trade holds up to twelve skins.
- `/market` sells skins for emerald items from your inventory. Sellers are paid even when offline and
  collect with `/market claims`.
- The market has a search: click the compass in the bottom right corner and type a skin or seller
  name, or use `/market search <Name>`. Right-click the compass to clear it. The skin picker for
  trades and sales has the same search.

### Dealer

The dealer sells cases and keys for emeralds. Prices are in `shop.yml`.

| Dealer | How it appears | Behavior |
| --- | --- | --- |
| Placed | `/csadmin shop spawn` | Stands still where you placed it |
| Village | In every third newly generated village one villager becomes the dealer | Walks around like a villager and stops while a player is close |

Both look the same: a villager, or a mannequin with a player skin that changes on a timer. Skins
given as player names are looked up once and saved in `dealer-skins.yml`, so the dealer keeps its
look and no lookup is repeated. Village dealers only appear in chunks nobody has visited yet;
villagers that players moved or bred are never taken. Everything is set in `shop.yml`:

```yaml
npc:
  type: mannequin              # mannequin or villager
  skins: [Notch, jeb_]         # player names, or {texture: "...", signature: "..."}
  skin-interval-seconds: 300

natural-spawn:
  enabled: true
  every: 3                     # one dealer in every third village (1 = every village)
  min-distance: 96             # never within this many blocks of another dealer
  wander: true                 # false = village dealers stand still too
```

## Commands and permissions

`<Argument>` is required, `[Argument]` optional.

| Command | Permission | Purpose |
| --- | --- | --- |
| `/skins [Player]`, `/inventory` | `mccases.use` | Your collection, or another player's |
| `/knife [Player]` | `mccases.use` | Knife collection |
| `/cases`, `/cases open <Case> <1-1000>`, `/cases cancel` | `mccases.use` | Cases and openings |
| `/openings` | `mccases.use` | Active openings and recent results |
| `/inspect [hand\|view]` | `mccases.inspect` | Inspect and choose the view |
| `/tradeup` | `mccases.tradeup` | Trade-up contracts |
| `/trade [Player]`, `/trade accept\|decline\|cancel` | `mccases.trade` | Trading |
| `/market`, `/market sell <Skin-ID> <Price>`, `/market own`, `/market search <Name>` | `mccases.market` | Market |
| `/market balance`, `/market claims`, `/market recover` | `mccases.market` | Emeralds and proceeds |
| `/casesplugin [help]` | none | Lists the commands you may use |

| Permission | Default | Access |
| --- | --- | --- |
| `mccases.use`, `mccases.inspect`, `mccases.view`, `mccases.shop` | Everyone | Collection, cases, inspects, dealer |
| `mccases.trade`, `mccases.market`, `mccases.tradeup` | Everyone | Trading, market, trade-up |
| `mccases.admin` | Operators | All admin commands |

<details>
<summary>Operator commands (<code>/csadmin</code>, alias <code>/mccases</code>)</summary>

| Command | Purpose |
| --- | --- |
| `/csadmin info` | Version, catalog size and pack address |
| `/csadmin givecase <Player> <Case> [Amount] [test]` | Give cases; `test` marks the results as tests |
| `/csadmin givekey <Player> <Key> [Amount] [test]` | Give keys |
| `/csadmin giveskin <Player> <Skin> [Float] [Pattern] [StatTrak]` | Add a skin to a collection |
| `/csadmin list <Player>`, `/csadmin history <Player>` | Skin instances; last 15 openings |
| `/csadmin manage <Player>` | Manage a collection in a menu |
| `/csadmin equip <Player> <ID>`, `/csadmin equipslot <Player> <ID> <knife\|bow\|crossbow>` | Equip a skin |
| `/csadmin setfloat`, `setpattern`, `setstattrak <Player> <ID> <Value>` | Edit skin values |
| `/csadmin removeskin <Player> <ID>` | Remove a skin |
| `/csadmin testcase <Case> [keep]` | Test an opening without using items |
| `/csadmin odds <Case>` | Real drop chances |
| `/csadmin preview`, `pattern`, `browser`, `scan` | Preview and analyze patterns |
| `/csadmin shop spawn [villager\|mannequin]`, `/csadmin shop remove` | Dealers |
| `/csadmin exportpack`, `/csadmin reload` | Rebuild the pack; reload configuration |
| `/market legacy` | Migrate old coin balances and offers |

`<ID>` is a skin instance from `/csadmin list` (a UUID or an unambiguous prefix).

</details>

## Configuration

Files live in `plugins/MCCases/`. Missing defaults are added on startup and your changes are kept.

| Setting (`config.yml`) | Default | Purpose |
| --- | --- | --- |
| `storage.type` | `sqlite` | `sqlite`, `mysql` or `mariadb` |
| `opening.display` | `world` | `world` for reels in the world, `gui` for an inventory roulette |
| `opening.world.visible-items` | `7` | Skins visible in the reel at once |
| `opening.world.scene-scale` | `2.0` | Upper limit for the reel size; reels shrink to fit when several open |
| `opening.max-active-per-player` | `9` | Openings at the same time (maximum 9) |
| `opening.duration-ticks` | `120` | Length of the reel |
| `opening.broadcast-rare` | `true` | Announce rare drops |
| `trade-up.broadcast-gold` | `true` | Announce gold contracts |
| `inspect.held-pose` | `true` | Inspect held skins in your own arms |
| `stattrak.count-mobs` | `false` | Count mob kills too |
| `resource-pack.enabled` | `false` | Use the pack's models; set to `true` once players have the pack |

```text
plugins/MCCases/
├── config.yml            Main options
├── shop.yml              Dealer look, village dealers, currency and prices
├── dealer-skins.yml      Saved dealer skins (written by the plugin)
├── market.yml            Market and trade limits
├── inspect.yml           Inspect animations
├── sounds.yml            Sounds
├── messages_en.yml       All text (MiniMessage)
├── catalog/              Cases, skins, weapons, rarities, keys
├── textures/             Weapon layers and patterns
├── resourcepack/         The bundled pack
├── data.db               SQLite database
└── secret.key            Signs cases and keys; back it up
```

Back up the database, player data and `secret.key` together.

### Drops

| Rarity | Mil-Spec | Restricted | Classified | Covert | Rare Special |
| --- | ---: | ---: | ---: | ---: | ---: |
| Default weight | 79.923 | 15.985 | 3.197 | 0.639 | 0.256 |

| Exterior | Factory New | Minimal Wear | Field-Tested | Well-Worn | Battle-Scarred |
| --- | --- | --- | --- | --- | --- |
| Float | 0.00-0.07 | 0.07-0.15 | 0.15-0.38 | 0.38-0.45 | 0.45-1.00 |

Eligible items have a 10 % StatTrak chance. `/csadmin odds <Case>` shows the exact chances.

<details>
<summary>Included cases</summary>

`csgo_weapon_case`, `operation_bravo_case`, `operation_breakout_case`, `huntsman_case`,
`falchion_case`, `shadow_case`, `chroma_case`, `chroma_2_case`, `cs20_case`, `chroma_3_case`,
`gamma_case`, `gamma_2_case`, `glove_case`, `spectrum_case`, `clutch_case`, `horizon_case`,
`danger_zone_case`, `prisma_case`, `fracture_case`, `snakebite_case`,
`dreams_and_nightmares_case`, `kilowatt_case`. All open with `case_key`.

</details>

<details>
<summary>Adding your own case</summary>

Create `catalog/cases/my_case.yml`; the file name is the case ID:

```yaml
case:
  name: "My Case"
  key: case_key
  color: "#3a8ab0"
  stattrak-chance: 0.10
  description: ["Custom Collection"]

skins:
  ak47_neon_wave:
    weapon: ak47
    rarity: covert
    name: "Neon Wave"
    style: waves
    palette: ["#0a0a2a", "#e83ab0", "#2ad0f0"]
    float: [0.0, 0.7]

rare-special:
  knives: [karambit, butterfly]
  finishes: chroma
```

Run `/csadmin reload`, check the console, then `/csadmin exportpack` to rebuild the pack. Weapon,
style, rarity, key and finish IDs must exist in the catalog.

</details>

## Resource pack

Every player needs the pack. It contains the skin and case icons, the 3D weapon models and the shaders
for the skin-arm view. Minecraft 26.3 is required.

**Built-in distribution (recommended).** The plugin hosts the pack and offers it on join:

```yaml
resource-pack:
  enabled: true
  distribution:
    enabled: true
    bind-address: 0.0.0.0
    port: 8165
    public-url: "http://your-server-address:8165"
    required: false          # true kicks players who decline
```

**Your own server pack.** Copy `assets/mccases/` and `assets/minecraft/shaders/` from the ZIP into your
pack, set `resource-pack.enabled: true`, leave the distribution off and run `/csadmin exportpack` after
catalog changes. Packs that replace the same core shaders need a manual merge.

**Manual install.** Players can also put the ZIP into their `resourcepacks` folder.

## Updating

1. Stop the server and back up `plugins/MCCases/`.
2. Replace the JAR and the resource pack together; they belong as a pair.
3. Start the server and check `/csadmin info`.

Versions are dates: `MONTH.DAY.YEAR`. See the [changelog](CHANGELOG.md).

Coming from a version that called the feature trade-in: the config section is now `trade-up:` and the
permission is `mccases.tradeup`. Rename both in your files; `/tradein` no longer exists.

## Troubleshooting

| Problem | Fix |
| --- | --- |
| Items show vanilla icons or missing textures | Load the matching pack and set `resource-pack.enabled: true` |
| Players are not offered the pack | Check `distribution.public-url`, open port 8165 and run `/csadmin reload` |
| Arms look like stretched heads | Another pack replaces the core shaders, or the client is not on 26.3 |
| **F** does not inspect | Hold an equipped skin; check `mccases.inspect` |
| A skin does not fit a slot | Knives use `knife`; weapon skins use `bow` or `crossbow` |
| An opening does not start | You need a case and its matching key; see `/cases` |
| The dealer does not sell a case | Check prices and `cases.exclude` in `shop.yml` |
| The dealer has the default skin | The name lookup failed (see the console); it is tried again on the next start |
| No dealer in a village | Only every third newly generated village gets one; lower `natural-spawn.every` |
| New content is missing | Check IDs and PNG files, then look at the console after `/csadmin reload` |

## How the plugin is checked

MCCases has no unit-test framework. Instead it ships small check programs in `src/tools` that run the
real plugin code against the real default files and stop the build on the first wrong result. Each one
prints a `PASS` or `Verified` line that says what it covered.

**Run on every build** (`bash gradlew build` runs all four):

| Task | What it checks |
| --- | --- |
| `verifyFeatures` | The catalog (815 skins, 22 cases), thousands of simulated drops against the published odds, wear boundaries, every message key and its fallback, the reel layout for one to nine openings, opening recovery after a crash, trade-up rules and odds (12,000 simulated contracts), market payments and trades |
| `verifyPack` | The generated resource pack: every skin has its model, texture and inspect layers, and nothing is missing or left over |
| `verifyResourcePackInstaller` | Merging the pack into an existing server pack: same result every time, other packs' files untouched, a failed write changes nothing |
| `verifyHeld` | The first-person held view: grip and arm positions for every weapon and hand pose |

**Run on a live server** (`bash gradlew devChecks` builds `MCCases-DevChecks-<version>.jar`):

Put that JAR next to MCCases on a test server and run `/mccasesdevcheck`. It drives the plugin the way
a player would: real commands, real menus and clicks, real trades, market purchases and trade-ups with
online players. Use it before a release; never install it on a production server.

**What the checks do not cover:** how things look. Colors, sizes and animations are judged by eye on a
test server. The preview tasks (`previewSheet`, `inspectFilmstrip`, `viewInspectPreview`) render
images to `build/` to make that faster.

If you change behavior on purpose, a check may fail because it still expects the old behavior. Update
the expectation in the check together with the change, in the same commit.

## Building from source

You need JDK 25. The Gradle wrapper downloads everything else.

```sh
bash gradlew build
```

This writes `build/libs/MCCases-<version>.jar` and `build/distributions/MCCases-ResourcePack-<version>.zip`,
refreshes `resourcepack/MCCases-ResourcePack.zip` and runs the checks above.

| Task | Result |
| --- | --- |
| `bash gradlew build` | Plugin JAR, resource pack and all build checks |
| `bash gradlew resourcePack syncResourcePack` | Regenerates the pack only |
| `bash gradlew devChecks` | The live-server check plugin |

Releases are built by the [release workflow](.github/workflows/release.yml) when a GitHub release is
published; tag it `vMONTH.DAY.YEAR`.

### Project layout

```text
src/main/java/dev/plattnericus/cases/
├── opening/      Case openings: world reel, GUI reel, layout
├── tradeup/      Trade-up rules, service and menus
├── commerce/     Trading, market, payments
├── shop/         Dealer, village dealers, dealer skins
├── gui/          Menus, filters, skin collection
├── inspect/      Inspect animations and held view
└── storage/      SQLite and MySQL/MariaDB
src/main/resources/defaults/   Config, catalog, messages (English)
src/tools/                     Check programs and the pack generator
```

## License

MCCases is source-available and free for non-commercial use under the [MCCases License](LICENSE). You
may use, change and share it for free, but you may not sell it or sell cases, keys or skins for real
money, and you must credit "Based on MCCases by Plattnericus" in anything derived from it. Third-party
material is listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Counter-Strike, CS2 and StatTrak are trademarks of Valve; Minecraft is a trademark of Mojang/Microsoft.

<div align="center">

[info@plattnericus.dev](mailto:info@plattnericus.dev) · [plattnericus.dev](https://plattnericus.dev) · [Issues](https://github.com/Plattnericus/CS2CasesPlugin/issues)

</div>
