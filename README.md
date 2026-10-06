# MCCases

Cases, skins and knives for Minecraft, with a virtual collection, pattern analysis and animated inspects inspired by Counter-Strike 2.

**Version 1.0.0** · **Paper 26.2** · **Java 25** · [Plattnericus](https://plattnericus.dev)

![Weapon and knife skins from the MCCases catalog](docs/images/skins.png)

MCCases runs on the server as a Paper plugin. Players use a normal Minecraft client.
The bundled resource pack adds skin sprites to items; maps, holograms and block models
are available without it. Swords, bows and crossbows keep their normal gameplay values.

[Installation](#installation) · [Playing](#playing) · [Commands](#commands) · [Permissions](#permissions) · [Configuration](#configuration) · [Resource Pack](#resource-pack) · [Custom Content](#custom-content) · [Development](docs/DEVELOPMENT.md)

## Features

| Feature | Included |
| --- | --- |
| Catalog | 21 cases, 713 skins and 55 weapon and knife models |
| Collection | Categories, favorites, filters, seven sort orders and the 36 newest skins |
| Trading | Direct skin trades with two live offers, confirmation from both players and cancellation safeguards |
| Marketplace | Fixed-price listings, search, sorting, categories and persistent Coin balances |
| Skin values | Float, exterior, pattern, StatTrak, origin and creation date |
| Pattern analysis | Doppler phases, Fade percentage, Blue Gem classifications and Fire & Ice |
| Inspects | 46 timelines with random variations for weapon classes and knife families |
| Language | English for all players by default; 45 translation files available |
| Dealer | Villager or mannequin with skin changes, player tracking and gestures |
| Storage | SQLite or MySQL/MariaDB, opening history and recovery of interrupted openings |

Open the collection with `/inventory` or `/skins`. By default it appears as a private hologram
wall; `/inventory vanilla` opens your skins in a normal chest menu instead.
There is no permanent shortcut item in the player's inventory by default.
Other players can view a collection but cannot equip, edit or delete its skins.
Admins can also manage collections belonging to known offline players.

Case openings use a public reel in front of the player or an inventory roulette.
The server determines the result before the animation begins. The animation does not change
the drop chance or the selected skin.

## Installation

1. Prepare a **Paper 26.2 server running Java 25**.
2. Copy [MCCases-1.0.0.jar](release/MCCases-1.0.0.jar) into `plugins/`.
3. Start the server. Configuration files are created in `plugins/MCCases/`.
4. As an operator, run `/csadmin info` to check the loaded catalog.
5. Run `/csadmin shop spawn` to place a dealer at your position.

No other plugins are required. Local runtime checks use Paper 26.2 Build 129.
Check your server configuration before using another Paper version.
The development check plugin is separate and is not part of the release installation.

Missing default files are added on startup. Existing configuration and catalog files are preserved.
Use `/csadmin reload` to reload messages, catalog entries, sounds and display settings.
Changing the database requires a restart. Before replacing the plugin JAR, stop the server
and back up `plugins/MCCases/` along with world and player data. Include the database,
opening journal and `secret.key`.

### First case opening

In this example, `PlayerName` is a connected player:

```text
/csadmin givecase PlayerName kilowatt_case 1
/csadmin givekey PlayerName case_key 1
```

The player opens the case by right-clicking its item or using `/cases`. One case and its matching
key are consumed. All bundled cases use `case_key`; custom cases can specify another key.

## Playing

### Collection and equipment

`/inventory` or `/skins` opens all skins as a hologram by default; `/knife` opens knives.
Use `/inventory vanilla` for your collection in a normal chest menu. Tab completion suggests
`vanilla` as well as known player names. These commands open the collection without occupying
a normal inventory slot. The optional inventory shortcut is disabled by default.
If it is enabled in configuration, its default slot is **17** in the main inventory.
Slot numbers start at 0. Disabling the shortcut removes remaining marked shortcut items and
releases the slot; ordinary items are preserved.

| Action in the 3D collection | Result |
| --- | --- |
| Look at a skin | Show its name and values in the action bar |
| Right-click | Open the skin's inspect menu |
| Left-click a knife | Equip or unequip it |
| Left-click a weapon skin | Equip or unequip it on the bow |
| Sneak + left-click a weapon skin | Equip or unequip it on the crossbow |
| Sneak + right-click | Toggle favorite |
| Use the buttons below the wall | Change page, category, sort order, filters or list view |
| Mouse wheel or number keys | Change hotbar slot normally; the collection stays open |
| Mouse wheel or number keys over a skin in the hologram | Change page without changing the held slot |
| Move ten blocks from the gallery | Close the collection automatically |

The close button ends the collection view at any time. Distance is measured from the gallery's
fixed position.

There are three equipment slots: **knife**, **bow** and **crossbow**. Knives appear on configured
sword types; weapon skins appear on bows or crossbows. A skin instance can occupy only one
equipment slot. Damage, durability and enchantments are preserved.

Skins belong to the virtual collection. The held weapon displays their cosmetic appearance;
it is not a transferable skin item. The inspect menu lets players delete their own skins
after confirmation.

### Inspect animations

| Input | Action |
| --- | --- |
| **F** — Minecraft's “Swap Hands” action | Inspect the held skin |
| **Sneak + F** | Swap hands normally |
| **Sneak + right-click** with a skin weapon | Start an inspect |
| `/inspect` | Inspect the held skin, or the equipped knife if no skin is held |
| `/inspect hand` | Select body-hand mode and start an inspect; suitable for F5 |
| `/inspect view` | Select first-person mode and start an inspect |

Minecraft lets players rebind F. Set `inspect.swap-hand-key: false` to disable inspect
for the Swap Hands action.

The first-person animation appears on the right side of the view. Observers see a separate scene
at the player's body hand. Left-handed players get a mirrored display. Body-hand mode follows
body orientation without moving upward as the player tilts their head.

The selected mode also applies to F until the next login. Minecraft does not send its F5 camera
setting to the server, so the command selects the mode. Weapon classes and knife families have
regular, reverse, short and special variations, including a Desert Eagle twirl.
When multiple variations are available, the same variation is not repeated consecutively.

Positions, sizes, models, timelines and random pools are configured in `inspect.yml`.

### Trading skins

Use `/trade` to choose a player or `/trade PlayerName` to send a request. The recipient can accept
or decline in chat; `/trade accept PlayerName` and `/trade decline` perform the same actions.
Requests expire after 60 seconds by default.

The trade menu has two colored halves with your actual player heads. Your available skins appear
on the left immediately. The right side shows the skins the other player offers you.
Click one of your skins to add it to your offer; click it again to remove it.
Selected skins have a green background behind the weapon and a **✓** before the name.
The green background uses a dedicated menu sprite from the optional resource pack.
When pack models are disabled, an enchantment glint and the **✓** mark the selection instead.
Equipped weapons keep their normal appearance.

You can offer up to twelve skins. Larger collections have page arrows.
The other player's offer remains visible and updates live while you browse.
Each skin shows its float, pattern, StatTrak values and specific instance ID.
The other player's offer uses normal skin icons; green selection backgrounds identify your own
selected skins.

Click **Accept** to confirm the current offer. Your left half then shows exactly the skins
you are giving away, with their green selection backgrounds still visible.
The other player sees your acceptance immediately. Before the trade completes,
clicking Accept again withdraws your acceptance and returns to your collection.
`/trade accept` accepts either a request or the current offer; an optional player name
must match the actual trade partner.

A one-sided offer is a gift and still needs both players to accept.
If you receive nothing, the Accept button explicitly warns you.

Every offer change clears both confirmations. After the latest change, players must wait
two seconds before accepting. The button displays the countdown and becomes available
automatically. Ownership changes only when both players accept the same offer.
Closing the menu, using `/trade cancel`, leaving the server or remaining inactive for five
minutes cancels an unfinished trade. Offered skins cannot be sold, equipped, deleted or edited.
Adding an equipped skin to an offer unequips it; after cancellation it can be equipped again.

### Skin marketplace

`/market` opens all listings. Tabs lead to your own listings, skin selection for selling
and your Coin balance. The collection and case dealer also have trade and marketplace buttons;
a skin's inspect menu lets its owner select it for sale.

| Action | Command or menu |
| --- | --- |
| Browse listings | `/market` or `/marketplace` |
| Manage your listings | `/market own` |
| Sell a skin | **Sell a Skin**, then set and confirm the price |
| Set an exact price | `/market sell <Skin-ID> <Price>`; opens price confirmation |
| Search for a skin or seller | `/market search <Name>` |
| View your balance | `/market balance` |
| Add funds as an admin | `/market credit <Known-Player> <Amount>`; also available from the console |

Listings can be sorted by date, price or float and filtered by knives or weapons.
Before purchase, the menu displays the seller, full skin values and total price.
Purchases require a separate confirmation. Sellers can cancel their listings for free
until someone buys them. Listed skins are reserved and hidden from the collection.

The currency is a separate, persistent **Coin balance**. Each player receives 1,000 Coins
on first access by default. Sellers receive the full price, including when they are offline.
There are no fees and no additional economy plugin is required. Coins and the case dealer's
diamonds are separate currencies; they cannot be exchanged.

`plugins/MCCases/market.yml` controls the starting balance, currency display name, maximum price,
listing limit and timeouts. Use `/csadmin reload` after changes.
The starting balance is granted once; later configuration changes do not alter existing balances.
Defaults allow 20 active listings per player and a maximum price of one billion Coins.

Purchase, payment and ownership transfer are saved together in the database.
A skin can be sold only once. Listings and balances survive restarts.
Transfers and credits are recorded in `commerce_log`. Float, pattern, StatTrak count
and origin are preserved when ownership changes. Equipment and favorite settings reset
for the new owner. Skins with origin `TEST` cannot be traded or sold.

Trading and marketplace messages include English and German translations. With the default
English server language, all players see English. If client language selection is enabled,
other client languages use the English fallback for these menus; their existing translations
remain available for other features.

### Skin previews

| Mode | Display |
| --- | --- |
| `item` | Rotating skin sprite from the resource pack |
| `map` | Rendered map showing the instance's pattern, float and wear |
| `hologram` | The same instance as a colored text-display grid |

`item` is the default and falls back to a map when pack models are disabled.
Pack sprites show the skin's general appearance; `map` and `hologram` show individual
patterns and wear.

### Dealer

The dealer sells cases and keys for **diamonds**. Currency, prices and excluded cases
are configured in `shop.yml`. Purchases support quantities of one or five.
Payment is taken only if the delivery fits in the player's inventory.

A mannequin uses a player name or signed texture data for its skin. It can change skins,
look at nearby players, wave to newcomers and hold a case. A villager is also available.
Remove a nearby dealer with `/csadmin shop remove`.

## Commands

`<Argument>` is required; `[Argument]` is optional. Do not type the brackets.
Case, key and skin IDs support tab completion.

### Players

All player commands are used in-game.

| Command | Alias | Permission | Action |
| --- | --- | --- | --- |
| `/skins [Player]` | `/inventory` | `mccases.use` | View your collection or a known player's collection |
| `/inventory vanilla` | — | `mccases.use` | Open your skins in a normal chest menu |
| `/knife [Player]` | `/knives` | `mccases.use` | View your knife collection or another player's knives |
| `/cases` | — | `mccases.use` | View owned cases, their contents and opening options |
| `/inspect` | — | `mccases.inspect` | Inspect the held skin or equipped knife |
| `/inspect hand` | — | `mccases.inspect` | Select body-hand mode for this login |
| `/inspect view` | — | `mccases.inspect` | Select first-person mode for this login |
| `/trade [Player]` | `/skintrade` | `mccases.trade` | Choose a trade partner or send a request |
| `/trade accept [Player]` | — | `mccases.trade` | Accept a request or the current trade offer |
| `/trade decline` | — | `mccases.trade` | Decline a request |
| `/trade cancel` | — | `mccases.trade` | Cancel a request or unfinished trade |
| `/market` | `/marketplace`, `/skinmarket` | `mccases.market` | Open the marketplace |
| `/market own` | — | `mccases.market` | Manage your listings |
| `/market sell <Skin-ID> <Price>` | — | `mccases.market` | Set a sale price and open confirmation |
| `/market search <Name>` | — | `mccases.market` | Search listings |
| `/market balance` | — | `mccases.market` | View your Coin balance |

Viewing another player's collection also requires `mccases.view`.
The player may be offline but must already be known to the server.

### Administration

All commands below require **`mccases.admin`**. `/mccases` is an alias for `/csadmin`.
The “Execution” column indicates where the command can be run. Targets specified as `<Player>`
must be online, except `/csadmin manage`, which also supports known offline players.

#### Items and skins

| Command | Execution | Action |
| --- | --- | --- |
| `/csadmin givecase <Player> <Case> [Amount] [test]` | In-game / console | Give signed cases; default amount is 1 |
| `/csadmin givekey <Player> <Key> [Amount] [test]` | In-game / console | Give signed keys; default amount is 1 |
| `/csadmin giveskin <Player> <Skin> [Float] [Pattern] [StatTrak]` | In-game / console | Add a skin directly to the collection |
| `/csadmin list <Player>` | In-game / console | List skin instances and short IDs |
| `/csadmin history <Player>` | In-game / console | Show the last 15 case openings |
| `/csadmin manage <Player>` | In-game | Open collection management with add, edit, equip and delete actions |
| `/csadmin removeskin <Player> <ID>` | In-game / console | Remove a skin instance and unequip it |
| `/csadmin equip <Player> <ID>` | In-game / console | Equip a knife in the knife slot or a weapon skin on the bow |
| `/csadmin equipslot <Player> <ID> <Slot>` | In-game / console | Equip in `knife`, `bow` or `crossbow` |
| `/csadmin setfloat <Player> <ID> <Value>` | In-game / console | Change float |
| `/csadmin setpattern <Player> <ID> <Value>` | In-game / console | Change pattern and update its analysis |
| `/csadmin setstattrak <Player> <ID> <true\|false>` | In-game / console | Change the StatTrak flag |
| `/market credit <Known-Player> <Amount>` | In-game / console | Add Coins to a known player's balance |

`<ID>` identifies a skin instance from `/csadmin list`, not a catalog ID.
Use a full UUID or an unambiguous prefix. Amounts must be **1–64**, float must be **0–1**
and admin patterns must be **0–99999**. Normal drops use seeds **0–999** by default.
Specify StatTrak as `true` or `false`; `giveskin` disables it when omitted.
Missing float and pattern values are selected randomly.

The word `test` follows an explicitly supplied amount. Test cases or test keys mark the
opening result and history entry as a test. Other optional arguments are supplied from left
to right.

#### Testing and pattern tools

| Command | Execution | Action |
| --- | --- | --- |
| `/csadmin testcase <Case>` | In-game | Open without consuming items or keeping a permanent skin |
| `/csadmin testcase <Case> keep` | In-game | Open without consuming items; keep the result as an admin skin |
| `/csadmin odds <Case>` | In-game / console | Show current drop chances, skin counts and StatTrak chance |
| `/csadmin preview <Skin> [Pattern] [Float]` | In-game | Preview; defaults are pattern 0 and float 0.01 |
| `/csadmin pattern <Skin> <Pattern>` | In-game / console | Analyze color shares, classification, phase and Fade percentage; also preview in-game |
| `/csadmin browser <Skin> [Pattern]` | In-game | Open the pattern browser with ±1, ±10 and random choices |
| `/csadmin scan <Skin>` | In-game / console | List available analysis metrics |
| `/csadmin scan <Skin> <Metric> [Count]` | In-game / console | Rank seeds by a metric; default is 10 results, allowed range is 1–50 |

Available metrics depend on the weapon; examples include `playside.blue` and `fade`.

#### Dealer and server

| Command | Execution | Action |
| --- | --- | --- |
| `/csadmin` | In-game / console | Show command help |
| `/csadmin shop spawn` | In-game / console | Create the configured dealer type |
| `/csadmin shop spawn villager` | In-game / console | Create a villager |
| `/csadmin shop spawn mannequin` | In-game / console | Create a mannequin |
| `/csadmin shop remove` | In-game | Remove the nearest dealer |
| `/csadmin exportpack` | In-game / console | Build a pack for the current catalog; resend it if distribution is active |
| `/csadmin reload` | In-game / console | Reload messages, catalog, sounds, shop and displays |
| `/csadmin info` | In-game / console | Show version, catalog size, preview cache and pack address |

In-game, dealers spawn at the player's position. From the console, they spawn at the first world's
spawn point. Pack export writes to `plugins/MCCases/resourcepack/MCCases-ResourcePack.zip`.

### Examples

```text
/csadmin giveskin PlayerName butterfly_doppler 0.02 10 false
/csadmin giveskin PlayerName ak47_case_hardened 0.03 661 true
/csadmin list PlayerName
/csadmin equipslot PlayerName <ID-from-the-list> bow
/csadmin givecase PlayerName kilowatt_case 5 test
/csadmin givekey PlayerName case_key 5 test
/csadmin odds kilowatt_case
/csadmin scan ak47_case_hardened playside.blue 10
```

## Permissions

| Permission | Default | Access |
| --- | --- | --- |
| `mccases.use` | All players | Collection and cases |
| `mccases.inspect` | All players | Inspect commands and inputs |
| `mccases.view` | All players | View other players' collections |
| `mccases.shop` | All players | Buy from the dealer |
| `mccases.trade` | All players | Trade skins and use trade requests |
| `mccases.market` | All players | Buy and sell skins; view Coin balance |
| `mccases.admin` | Operators | All admin commands and management menus |

## Configuration

Default files are stored in `plugins/MCCases/`. Values below refer to the bundled configuration.

[config.yml](src/main/resources/defaults/config.yml) · [shop.yml](src/main/resources/defaults/shop.yml) · [inspect.yml](src/main/resources/defaults/inspect.yml) · [sounds.yml](src/main/resources/defaults/sounds.yml) · [Language files](src/main/resources/defaults/)

| Setting | Default | Purpose |
| --- | --- | --- |
| `language` | `en` | Server language for menus, messages and the console |
| `client-language` | `false` | When enabled, follow each player's Minecraft language |
| `storage.type` | `sqlite` | `sqlite`, `mysql` or `mariadb` |
| `skin-inventory.display` | `world` | Private display wall; alternatively `gui` |
| `opening.display` | `world` | Public opening reel; alternatively `gui` |
| `opening.duration-ticks` | `120` | Roulette movement duration; 20 ticks equal one second |
| `opening.easing` | `cinematic` | Deceleration curve |
| `opening.broadcast-rare` | `true` | Announce rare special drops |
| `opening.speech-bubble.enabled` | `true` | Speech bubble when clicking an active world opening |
| `preview.mode` | `item` | `item`, `map` or `hologram` |
| `skin-inventory-item.enabled` | `false` | Optional inventory shortcut; disabled to keep access through commands |
| `skin-inventory-item.slot` | `17` | Slot used only when the shortcut is enabled; 0–35, hotbar 0–8 |
| `knives.bow-skins` | `true` | Allow weapon skins on bows |
| `knives.crossbow-skins` | `true` | Allow weapon skins on crossbows |
| `inspect.swap-hand-key` | `true` | Use the Swap Hands action for inspect |
| `inspect.sneak-right-click` | `true` | Start inspect with sneak + right-click |
| `inspect.cooldown-ticks` | `8` | Delay between inspects |
| `stattrak.count-mobs` | `false` | Count mob kills as well as player kills |
| `resource-pack.enabled` | `false` | Use custom item models |
| `resource-pack.distribution.enabled` | `false` | Enable the built-in pack web server and offer the pack on join |

`shop.yml` contains currency, case and key prices, and dealer options.
`sounds.yml` contains sound cues. `inspect.yml` defines display models and animations,
including `anchor`, `hand-anchor`, `model-scale`, `hand-model-scale` and `animation-pools`.

### Languages

English is used for every player by default: `language: en` and `client-language: false`.
Minecraft clients may use another language without changing plugin menus and messages.

With `client-language: true`, menus, chat, item names, lore, rarity and exterior follow each
Minecraft client's language. Changing language updates the display during play.
Weapon, skin and case names keep their catalog names.

Selection follows **exact locale → base language → configured server language**.
Missing message keys are filled from the bundled translation and then English.
`client-language: false` uses the server language for all players.

Custom files are named `messages_<locale>.yml`, such as `messages_es_419.yml`, and are loaded by
`/csadmin reload`. Messages support MiniMessage. Public displays use their owner's language;
dealer text and freely configured text use the server language.

<details>
<summary>45 bundled language codes</summary>

```text
af     ar     be     bg     ca     cy     da     de     en
eo     es     fil    fr     fy     ga     gd     gl     he
hi     hr     id     is     it     ja     ko     lb     mi
ms     mt     nb     nl     nn     pl     pt_br  pt_pt  ro
ru     sl     sv     tr     tt     uk     yo     zh_cn  zh_tw
```

</details>

### Database and storage

SQLite uses `plugins/MCCases/data.db` by default. MySQL or MariaDB credentials are configured
under `storage.mysql`. The default `storage.table-prefix` is `pc_`.
Database access runs on a separate thread.

Each opening gets a journal entry in player data. The skin and opening history are saved
together in one database transaction. Interrupted results are recovered on the next login.
Death, teleporting or leaving the animation does not discard a determined drop.
Back up the journal and database together.

Cases and keys carry signed identifiers. A display name alone does not make an item valid.
Menu icons, the optional inventory shortcut and cosmetic skin references are protected against
unauthorized movement or use. Deleted skins are stored as removed; their opening history remains.

## Resource Pack

The pack is bundled inside the plugin JAR and extracted to `plugins/MCCases/resourcepack/`.
The standalone ZIP is available at
[release/MCCases-ResourcePack-1.0.0.zip](release/MCCases-ResourcePack-1.0.0.zip).

### Automatic distribution

Set these values within the existing `resource-pack` section of `config.yml`:

```yaml
resource-pack:
  namespace: mccases
  distribution:
    enabled: true
    bind-address: 0.0.0.0
    port: 8165
    public-url: "http://mc.example.net:8165"
    required: false
    prompt: "<gray>MCCases skin textures"
```

`public-url` must be reachable by players. Open the selected port for external connections.
`127.0.0.1` is suitable only for local testing. The web server serves only the pack file and offers
it on join. Active distribution also enables custom item models.
`required: true` disconnects players who decline the pack.

### Combining it with a server pack

1. Copy `assets/mccases/` from the ZIP or `resourcepack/` into your own pack.
2. Deliver the combined pack through your existing server pack distribution.
3. Set `resource-pack.enabled: true` and leave built-in distribution disabled.

No vanilla files are replaced. When custom models are enabled, the client must have the matching
pack loaded; otherwise missing models may appear. Re-export with `/csadmin exportpack` after
catalog changes and update any manually distributed pack.

The pack also contains separate green trade-selection sprites. These apply only to your selected
icons in the trade menu. Normal collection icons and equipped weapons use the original sprites.
Bundled packs are refreshed when their content changes, including within the same plugin version;
manually exported or edited packs are preserved.

## Drops and skin values

Default weights correspond to these rarity tiers:

| Rarity | Weight |
| --- | ---: |
| Mil-Spec | 79.923 |
| Restricted | 15.985 |
| Classified | 3.197 |
| Covert | 0.639 |
| Rare Special Item | 0.256 |

The actual distribution depends on a case's contents; `/csadmin odds <Case>` shows it.
The default StatTrak chance is 10% for eligible weapons. There is no pity system.

| Exterior | Float range |
| --- | --- |
| Factory New | 0.00 ≤ float < 0.07 |
| Minimal Wear | 0.07 ≤ float < 0.15 |
| Field-Tested | 0.15 ≤ float < 0.38 |
| Well-Worn | 0.38 ≤ float < 0.45 |
| Battle-Scarred | 0.45 ≤ float ≤ 1.00 |

Skins may define their own float limits. Patterns control texture transformations and can have
manual classifications. StatTrak counts the skin on the weapon held in the killer's main hand
when the opponent dies. Self-kills are excluded; mob kills are disabled by default.

<details>
<summary>Included cases and IDs</summary>

| ID | Case |
| --- | --- |
| `csgo_weapon_case` | CS:GO Weapon Case |
| `operation_bravo_case` | Operation Bravo Case |
| `operation_breakout_case` | Operation Breakout Weapon Case |
| `huntsman_case` | Huntsman Weapon Case |
| `falchion_case` | Falchion Case |
| `shadow_case` | Shadow Case |
| `chroma_case` | Chroma Case |
| `chroma_2_case` | Chroma 2 Case |
| `chroma_3_case` | Chroma 3 Case |
| `gamma_case` | Gamma Case |
| `gamma_2_case` | Gamma 2 Case |
| `glove_case` | Glove Case |
| `spectrum_case` | Spectrum Case |
| `clutch_case` | Clutch Case |
| `horizon_case` | Horizon Case |
| `danger_zone_case` | Danger Zone Case |
| `prisma_case` | Prisma Case |
| `fracture_case` | Fracture Case |
| `snakebite_case` | Snakebite Case |
| `dreams_and_nightmares_case` | Dreams & Nightmares Case |
| `kilowatt_case` | Kilowatt Case |

</details>

## Custom content

```text
plugins/MCCases/
├── config.yml                    Server and display options
├── messages_*.yml                Messages for each language
├── sounds.yml                    Sound cues
├── inspect.yml                   Models and inspect timelines
├── shop.yml                      Prices, currency and dealer
├── market.yml                    Coin balances, marketplace and trade limits
├── data.db                       SQLite database with default configuration
├── secret.key                    Signing key for cases and keys
├── resourcepack/                 Bundled or exported pack
├── catalog/
│   ├── rarities.yml              Rarities, weights and exterior thresholds
│   ├── weapons.yml               Weapons, model assignments and analysis regions
│   ├── styles.yml                Pattern textures and seed transformations
│   ├── finishes.yml              Finishes, variations and finish sets
│   ├── patterns.yml              Classifications and manual seed data
│   ├── keys.yml                  Key definitions
│   ├── cases/*.yml               Cases and their skins
│   └── skins/*.yml               Additional skins outside cases
└── textures/
    ├── weapons/<ID>/             Masks and material layers
    ├── patterns/                 Tileable pattern textures
    ├── wear/                     Wear textures
    └── overlays/                 Fixed designs and logos
```

### Adding a case

Create `catalog/cases/my_case.yml`. Its file name is the case ID:

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

Run `/csadmin reload`, check console messages and use `/csadmin exportpack` if you use the pack.
Weapon, style, rarity, key and finish-set IDs must exist in the catalog.

### Textures and patterns

Place custom pattern PNGs in `textures/patterns/`. `catalog/styles.yml` links them to a style.
`mapping: smooth` or `steps` colors grayscale values using the skin palette;
`mapping: none` preserves the original colors. The `transform` section defines translation,
rotation, scaling and mirroring for each seed.

Weapons use square PNG layers of the same size, normally 256 × 256 pixels:

| File | Required | Purpose |
| --- | --- | --- |
| `mask.png` | Yes | Weapon shape |
| `paint.png` | No | Paintable areas |
| `base.png` | No | Base material beneath the paint |
| `shadow.png` | No | Shading |
| `highlight.png` | No | Highlights |
| `wear.png` | No | Sensitivity of each area to wear |

Add new weapons to `catalog/weapons.yml` as well.
Rules in `catalog/patterns.yml` use measurements such as `playside.blue`.
Manual seed entries take precedence over calculated classifications.
`/csadmin pattern`, `/csadmin browser` and `/csadmin scan` help inspect the results.

## Rendering and limitations

Skin textures are rendered from the bundled PNG layers, patterns and palettes.
They are not original Valve textures. Names and designs are inspired by Counter-Strike;
float limits and pattern displays may differ from the original game.

Inspects use display entities with Minecraft interpolation. Block models are animated without
the pack; skin sprites are animated with it. This does not include a complete CS2 model
with a freely animated Minecraft hand rig. On bows and crossbows, the pack sprite replaces
the visible vanilla drawing animation while retaining the weapon's actual behavior.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Missing textures or models | Is the matching pack loaded? Check namespace and distribution in `config.yml` |
| Plugin language does not change | Check `language`, `client-language` and available `messages_<locale>.yml` files, then run `/csadmin reload` |
| Opening does not start | Have a valid case and matching key; use `/cases` |
| F does not start an inspect | Hold an equipped skin; check `mccases.inspect` and `inspect.swap-hand-key` |
| F5 inspect appears in front of the camera | Use `/inspect hand`; return to first-person mode with `/inspect view` |
| Skin does not fit an equipment slot | Knives require `knife`; weapon skins require `bow` or `crossbow` |
| Shop does not sell a case | Check prices and `cases.exclude` in `shop.yml` |
| No inventory shortcut item | This is the default; use `/inventory` or `/skins` |
| Selected trade skin lacks a green background | Load the matching pack and enable its models; without them, selection uses glint and **✓** |
| New content is missing | Check IDs, PNG files and console warnings during reload |

## Local development session

The isolated local server and Minecraft clients use `.dev/runtime/`.
`scripts/dev.py` prepares the session and keeps its server and clients running while the
selected desktop app process remains open. Closed game clients are restarted.
Closing the watched app stops the session's processes and removes its temporary runtime data.
The regular Minecraft installation and its settings are preserved.

```text
python3 scripts/dev.py prepare
python3 scripts/dev.py start <desktop-app-process-ID>
python3 scripts/dev.py status
python3 scripts/dev.py observer
python3 scripts/dev.py console "list"
python3 scripts/dev.py restart
python3 scripts/dev.py stop
```

This requires an installed Minecraft 26.2 client, a built plugin and acceptance of the Minecraft
EULA before the server can start. `observer` starts the second client for trade testing.
Use `127.0.0.1:25565` to connect to the local server.
The check plugin is for development only; keep it out of `release/`.
Temporary test fixtures, screenshots and extra check JARs can be removed after validation;
source checks remain available for later development.

Build instructions, rendering tools and local server checks are documented in
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).
