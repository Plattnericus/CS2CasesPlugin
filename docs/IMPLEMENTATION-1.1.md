# Repository analysis and implementation

The original repository has 713 catalog skin definitions, 21 cases, a signed case/key inventory
system, independent render workers, SQL storage with a single database worker, a player-file skin
journal, resource-pack sprites, inspect animations, optional galleries, and a diamond case dealer.
These content/render/equipment components are retained.

The original schema 2 marketplace committed virtual wallet debits/credits alongside ownership.
Its UI had 54-slot browsing but command-only search, a small detail menu and no offline item-delivery
protocol. `OpeningService` keyed sessions by player UUID and blocked new openings and commands while
one animation ran. World reels allocated two displays for every reel item (including hidden items).
Direct trading already had atomic SQL exchanges, twelve-skin offers, main-thread reservations,
revision-bound confirmations and a review delay; its collection occupied only twelve main-menu
slots. No trade-in/contract system existed.

## Retained architecture and changes

- `Database`, `SkinRepository`, and `CommerceRepository` retain the JDBC worker and conditional
  owner/status writes. Schema 3 archives Coin listings and adds payment, reservation, claim,
  delivery and contract journals plus query indexes. No extra runtime dependency is added.
- `EmeraldRepository`, `EmeraldPayments`, `EmeraldItems` and `ItemReceipts` implement the two-store
  protocol described in `UPGRADE-1.1.md`. Mutable inventory access is main-thread-only; SQL runs on
  the existing worker. Each player's financial operations are serialized, while other players
  and case animations continue. Recovery queries pending rows and DONE IDs with actual PDC receipts,
  so login cost does not grow with all historical completed purchases.
- `OpeningSessions` keys by opening ID and allocates reusable display lanes. Existing sessions,
  rollers, easing, reveal effects and catalogs remain. `WorldReelView` reuses `visible-items + 4`
  item/bar pairs outside the visible window; tasks and entities are removed independently. GUI
  visibility does not finalize or cancel a result. Session limits include held world results.
- `PendingJournal` v2 stores the original opening record along with its reward. The old codec
  continues to read skin-only receipts. Uncertain SQL failures retain the journal without issuing
  a potentially duplicate refund. Recovered rewards and normal animation rewards become available
  only after SQL confirms their OWNED status.
- `SkinPickerMenu` and `CollectionSelection` provide full sale/trade browsers. `ChatInput` captures
  one private chat response, schedules GUI work on the main thread and expires after 30 seconds.
  Main-menu offer synchronization and safe revision confirmations are retained. Menu shift/hotbar,
  drag and movement are canceled; only mouse actions dispatch controls, once per server tick.
- `TradeInRules`, `TradeInService`, `TradeInMenu` and `ContractRepository` implement separate
  contracts. Each contract commits all consumed inputs and one owned output atomically. Actual
  `rareSpecial` classification controls gold messaging. The SQL marker claimed before dispatch
  gives at-most-once announcements: a crash in the small dispatch interval may omit a message.
- `DefaultFiles` adds missing YAML leaves with an atomic replacement and does not overwrite user
  values. Changed Emerald message keys are new keys so old default Coin text cannot override new
  payment semantics. Other translations continue to use bundled English fallback.

Skin IDs, skin roll properties and origins survive transfers. Listings and trade/contract offers
exclude test skins and unavailable/reserved instances. The shared mutation lock guards admin edits,
favorites, equipment, deletion and trading; final SQL owner/status conditions remain authoritative.
Direct-trade reservations are intentionally memory-only: an unfinished offer cancels on disconnect
or shutdown and no input is consumed. A committed SQL trade survives a lost UI acknowledgement.

## Changed and added files

```text
.gitignore
CHANGELOG.md
README.md
build.gradle.kts
docs/DEVELOPMENT.md
docs/IMPLEMENTATION-1.1.md
docs/UPGRADE-1.1.md
docs/VERIFICATION-1.1.md
docs/VERIFICATION-1.1.log
release/MCCases-1.1.0.jar
release/MCCases-ResourcePack-1.1.0.zip
scripts/dev.py
src/main/java/dev/plattnericus/cases/bootstrap/DefaultFiles.java
src/main/java/dev/plattnericus/cases/command/PlayerCommands.java
src/main/java/dev/plattnericus/cases/commerce/ChatInput.java
src/main/java/dev/plattnericus/cases/commerce/CollectionSelection.java
src/main/java/dev/plattnericus/cases/commerce/CommerceCommands.java
src/main/java/dev/plattnericus/cases/commerce/CommerceService.java
src/main/java/dev/plattnericus/cases/commerce/EmeraldItems.java
src/main/java/dev/plattnericus/cases/commerce/EmeraldPayments.java
src/main/java/dev/plattnericus/cases/commerce/ItemReceipts.java
src/main/java/dev/plattnericus/cases/commerce/ListingMenu.java
src/main/java/dev/plattnericus/cases/commerce/MarketMenu.java
src/main/java/dev/plattnericus/cases/commerce/SellMenu.java
src/main/java/dev/plattnericus/cases/commerce/SkinPickerMenu.java
src/main/java/dev/plattnericus/cases/commerce/TradeMenu.java
src/main/java/dev/plattnericus/cases/commerce/TradeSession.java
src/main/java/dev/plattnericus/cases/config/PluginSettings.java
src/main/java/dev/plattnericus/cases/core/CaseItemListener.java
src/main/java/dev/plattnericus/cases/core/CasesBootstrap.java
src/main/java/dev/plattnericus/cases/core/CasesContext.java
src/main/java/dev/plattnericus/cases/core/CasesRuntime.java
src/main/java/dev/plattnericus/cases/gui/Menu.java
src/main/java/dev/plattnericus/cases/gui/MenuListener.java
src/main/java/dev/plattnericus/cases/gui/menu/CasePreviewMenu.java
src/main/java/dev/plattnericus/cases/gui/menu/CasesMenu.java
src/main/java/dev/plattnericus/cases/gui/menu/SkinInventoryMenu.java
src/main/java/dev/plattnericus/cases/opening/ActiveOpeningsMenu.java
src/main/java/dev/plattnericus/cases/opening/OpeningMenu.java
src/main/java/dev/plattnericus/cases/opening/OpeningService.java
src/main/java/dev/plattnericus/cases/opening/OpeningSession.java
src/main/java/dev/plattnericus/cases/opening/OpeningSessions.java
src/main/java/dev/plattnericus/cases/opening/RevealEffects.java
src/main/java/dev/plattnericus/cases/opening/WorldReelView.java
src/main/java/dev/plattnericus/cases/profile/PendingJournal.java
src/main/java/dev/plattnericus/cases/profile/ProfileService.java
src/main/java/dev/plattnericus/cases/skin/SkinInstance.java
src/main/java/dev/plattnericus/cases/storage/CommerceRepository.java
src/main/java/dev/plattnericus/cases/storage/ContractRepository.java
src/main/java/dev/plattnericus/cases/storage/Database.java
src/main/java/dev/plattnericus/cases/storage/EmeraldRepository.java
src/main/java/dev/plattnericus/cases/storage/SkinRepository.java
src/main/java/dev/plattnericus/cases/tradein/TradeInMenu.java
src/main/java/dev/plattnericus/cases/tradein/TradeInRules.java
src/main/java/dev/plattnericus/cases/tradein/TradeInService.java
src/main/resources/defaults/config.yml
src/main/resources/defaults/market.yml
src/main/resources/defaults/messages_de.yml
src/main/resources/defaults/messages_en.yml
src/main/resources/defaults/sounds.yml
src/main/resources/plugin.yml
src/tools/java/dev/plattnericus/cases/opening/OpeningChecks.java
src/tools/java/dev/plattnericus/cases/tools/CommerceChecks.java
src/tools/java/dev/plattnericus/cases/tools/CommerceRuntimeChecks.java
src/tools/java/dev/plattnericus/cases/tools/FeatureChecks.java
src/tools/java/dev/plattnericus/cases/tools/ItemRuntimeChecks.java
src/tools/java/dev/plattnericus/cases/tools/RuntimeChecksPlugin.java
```
