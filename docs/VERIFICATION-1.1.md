# Verification — MCCases 1.1.0

Executed on 2026-10-08 in an isolated temporary server/world/database, not a production installation.
Java 25.0.2, Paper 26.3 build 159 beta, vanilla Minecraft 26.3, SQLite. The Paper JAR and cached
Mojang server file were verified against their published checksums. Two offline development
profiles connected only to 127.0.0.1. Client native transport was disabled for this macOS test.

## Automated checks

- `bash gradlew build devChecks --console=plain`: successful. `check` runs the custom
  `verifyFeatures` executable; the JUnit `test` task has no sources.
- Price bounds and overflow; concurrent buyers; prepare/complete/cancel and idempotent retries;
  exact skin ID/float/pattern/wear seed/StatTrak transfer; listing/equipment reservations;
  partial/repeated item claims; interrupted payment/delivery after reopening SQLite;
  injected SQL trigger failures during purchase and payout, complete rollback and successful retry.
- Schema 1 and 2 migration: old skin data retained, old Coin wallets unchanged, old offers archived
  and returned without treating their prices as Emeralds.
- Opening registry IDs, independent lanes, per-player/global caps and lane reuse; twenty independent
  persisted outcomes across two players, duplicate replay without extra rows, recovery of PENDING
  outcomes; normal/gold contract rules, invalid/test/admin inputs, atomic consumption and announcement
  marker claimed once.
- Existing catalog: 713 skins, 21 cases, 5,250 generated rewards/reels, odds, wear boundaries,
  inventory queries and journal codec. All 45 language schemas and 46 animation timelines checked;
  complete English/German keys and other-language fallbacks retained.
- `python3 -m unittest discover -s scripts -p 'test_*.py'`: two launcher cleanup tests passed.
- `bash gradlew compileJava -PpaperApi=io.papermc.paper:paper-api:26.2.build.129-stable`:
  successful compatibility compilation. Final artifacts were rebuilt against 26.3 build 159.

## Actual Paper runtime

MCCases loaded all 713 skins and 21 cases under Paper 26.3 / Java 25. `mccasesdevcheck items`
passed using genuine Paper ItemStacks/PDC with controlled player/inventory proxies: storage and
offhand payment, armor exclusion, negative/overflow/insufficient funds, exact item removal,
full/partial delivery capacity, idempotent saved receipts, twenty case/key pairs, test provenance
and v1/v2 opening journals. This checks runtime APIs, not a connected player's gameplay.

## Two connected vanilla clients

`mccasesdevcheck DevTester commerce DevObserver` passed, then passed again on the final plugin:

- 54-slot trade and picker inventories; an 81-skin collection reaches three pages.
- Collection/back navigation retains the trade and selections; reservations prevent equipment;
  both offers synchronize; hotbar and drag events are canceled; both confirmations required.
- Buyer starts with 400 real Emeralds across storage/offhand, purchases for 320 and ends with 80;
  seller claim persists, listing disappears and roll properties are unchanged.
- Limited inventory capacity accepts exactly 65 Emeralds; the remaining claim amount is correct;
  a full-inventory retry delivers nothing.
- Three independent openings per player run together; six exact case/key pairs are consumed,
  six rewards arrive, journals clear and all active sessions clean up. A direct trade can start
  and cancel while these openings run.

The development plugin invokes the real services and dispatches inventory events against connected
players. These are server integration checks with actual clients, not manual mouse/visual layout QA.
An attempted overlapping run during a committing contract was correctly blocked by the service;
the commerce audit was rerun sequentially and passed. Checks account for earlier unclaimed test claims.

`mccasesdevcheck DevTester gold` passed: two actual five-Covert service contracts consume all inputs
and produce rare-special outputs. ADMIN output has an unclaimed announcement marker; normal output
has one claimed marker. Both clients' logs contain exactly one identical GOLD DROP line naming
DevTester and the awarded Kukri Knife. The administrator contract emits no global announcement.

## Disconnect and actual server restart

Two test openings were allowed to persist, then DevTester disconnected before their animations
finished. SQL showed exactly two PENDING UUIDs; a snapshot retained owner, skin, float, pattern and
StatTrak kills. The server stopped cleanly, restarted, and the real client reconnected. Paper logged
`Recovered 2 interrupted case reward(s) for DevTester`. Both original UUIDs became OWNED, all
snapshot values matched, and each retained exactly one opening-history record. No second reward
was created. A clean shutdown while the player stayed connected was also checked: queued
finalization preserved both original rewards across restart.

## Remaining validation limits

MySQL/MariaDB runtime, other plugins mutating inventories/PDC, large-server load profiling,
manual visual/interaction QA, abrupt host power loss and mismatched backup restoration were not
tested. World-reel entity use is bounded in code; no throughput or high-player-count guarantee is
claimed. Paper 26.3 build 159 is a beta build. See UPGRADE-1.1.md for the inventory/SQL durability
boundary and the complete backup requirement.

Condensed build/server/client evidence is saved in [VERIFICATION-1.1.log](VERIFICATION-1.1.log).

Release JAR SHA-256: `21e1bebda0996e246bffd8548672f867b934b0d1eac25888f494b81282bf0be5`.
