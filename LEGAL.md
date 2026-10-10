# Legal information and operation in Italy

Updated: **October 10, 2026**. Project: **MCCases**, maintained by **Plattnericus**.
Project and rights inquiries: **info@plattnericus.dev**.

This document describes the stated operation using exclusively free in-game currency.
It is not a legal review, government authorization or assurance of legality. Actual
server features, assets and operator details must match these statements.

## Independent project and trademarks

MCCases is an unofficial Minecraft plugin, neither approved nor supported by Mojang,
Microsoft or Valve. The English disclaimer is included in the
[bundled notice](src/main/resources/defaults/LEGAL-NOTICE.txt). Counter-Strike and Steam
are Valve trademarks. Third-party marks are used descriptively; no trademark license
is granted. See the [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines)
and [Valve Legal Info](https://store.steampowered.com/legal).

Each server's operator is responsible for that server. The project contact does not
replace the operator's legal name or privacy information. Use your own server logos
and an accessible email address; do not claim an official partnership.

## Rules for the stated free operation

These rules are deliberately narrower than a statement about all legally possible
business models. They apply to the documented server operation:

- Cases, keys, diamonds, emeralds and skins are earned in-game or granted for free.
  No purchases using money, cryptocurrency, vouchers or other assets, including
  paid ranks, boosts, bundles or donation bonuses.
- No cash-out, external sales, Steam transfers, material prizes or conversion into
  another server's currency. Prohibit external sales in server rules and moderate
  them when discovered.
- Collection and trading are cosmetic game features. Displayed values and “Value”
  filters describe the configured game economy, not monetary value.
- Publish actual probabilities from the active catalog. Operators can check them
  with `/csadmin odds <Case>`; different weights or missing tiers change the
  distribution. Opening several cases does not improve an individual case's chance.

Minecraft restricts virtual currencies and requires servers suitable for all ages.
Excluding money purchases alone does not automatically authorize random reward
mechanics. See the [official server rules](https://www.minecraft.net/en-us/usage-guidelines#servers-and-hosting).

The plugin handles Minecraft items and has no money-purchase or cash-out feature.
It cannot control webshops, other plugins or agreements outside the server.
Before monetization, competitions with prizes or external trading platforms, obtain
fresh advice from a professional qualified in Italy. This document does not provide
a conclusive classification under Italian gambling law.

## Copyright and license status

The repository has **no general open-source license**. This revision introduces no
MIT, GPL or other general license. Public source is not blanket permission for reuse.
Contributors retain rights to their contributions; statutory rights and separately
granted permissions remain unaffected. For additional use or redistribution, contact
**info@plattnericus.dev**.

Standard artwork is rendered by the project tools from masks, patterns and palettes.
This describes its technical creation, not automatic legal clearance for every
reference, name or design inspired by existing work. The catalog contains such
references. Review uncertain content against its provenance and replace it with
original designs where necessary. Calling something a fan project does not cure
missing permission.

The project now includes the **Fusion-HD server overlay** supplied for this server:
seven bitmap-font images, their font definition and the pack icon. Their exact source
archive and file hashes are recorded in [resourcepack/fusion/SOURCE.json](resourcepack/fusion/SOURCE.json).
Current MCCases assets are regenerated; the supplied server assets remain unchanged.
This records technical provenance, not an independent rights clearance or a new license.
The operator must have the required rights to those assets and any additional artwork,
fonts, logos or player skins. Editing or combining artwork does not remove its rights.

Do not distribute Minecraft/Counter-Strike installations, extracted game textures,
third-party sounds or client JARs as part of the plugin. The
[Minecraft EULA](https://www.minecraft.net/en-us/eula) distinguishes independent
modifications from redistributed game versions. Actual technical dependencies are
listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Privacy in the actual plugin

UUIDs and player names are personal or identifiable data. The reviewed code stores:

| Data | Technical location / visibility |
| --- | --- |
| UUID, skin ID, float, pattern, StatTrak, provenance, timestamps, favorites and status | SQL `skins`; other known players' collections are visible by default |
| Equipped skins | `equipped` |
| UUID, player name and opening results | `openings`; optional console output |
| Seller name, buyer/seller UUID, offers and item payments | `market_listings`, `market_payments`, `commerce_log` and reservations |
| Proceeds and contracts | `emerald_claims`, `emerald_deliveries`, `trade_contracts` |
| Previous Coin balances and offers after migration | `wallets`, `legacy_market_listings` |
| Recovery receipts | Persistent data in Minecraft player files and their backups |

The default database is `plugins/MCCases/data.db`; alternatively, the configured
MySQL/MariaDB instance is used. Removed skins remain stored with status `REMOVED`.
There is **no complete GDPR erasure function** or automatic retention period.
Handle erasure requests consistently across dependent tables, player files and
backups; do not blindly delete individual rows while payments or recovery remain open.

Public listings, collection views and gold announcements reveal names or gameplay
activity. Choose optional displays deliberately. A permission system can revoke
`mccases.view`; `opening.broadcast-rare`, `trade-in.broadcast-gold` and
`display.log-openings` are configurable. Database storage remains necessary for gameplay.

No external analytics/telemetry integration was found in the reviewed plugin code.
The optional pack webserver, configured database connections and NPC profile lookup
are network functions. Minecraft/Paper, hosting, proxies, webshops and other plugins
have their own processing; their logs may also contain IP addresses and belong in
the actual server privacy information.

Complete the Italian [privacy template](docs/PRIVACY-IT.md) with operator identity,
legal basis, recipients and retention before use. Limit access, review hosting
agreements and plan erasure/access requests. See the
[GDPR, particularly Articles 5, 6, 12–22, 28 and 32](https://eur-lex.europa.eu/eli/reg/2016/679/oj/eng)
and [Garante information](https://www.garanteprivacy.it/informativa-protezione-dati).

For online services offered directly on a consent basis, Italy generally uses age
14 as the threshold for a minor's own digital consent. This is not a general entry
permission from age 14 and does not replace assessment of other legal bases or the
specific offering. See the [Garante's guidance on minors' digital consent](https://www.garanteprivacy.it/home/docweb/-/docweb-display/docweb/9880951).

## Details the operator still needs to complete

- Legal name/organization and required operator information; **info@plattnericus.dev**
  is recorded as the public contact.
- Actual hosting provider, countries, agreements and retention/erasure periods for
  SQL, server logs, player files and backups.
- Provenance and use permissions for designs, additional fonts, logos and NPC skins;
  use original replacements where needed.
- Publication of completed privacy information and server rules somewhere accessible
  before participation. A repository file alone is insufficient for a separately
  operated server.

These details cannot reliably be inferred from program code.
