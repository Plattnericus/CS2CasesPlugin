# Safe upgrade to MCCases 1.1

1. Stop the server cleanly. Back up the complete `plugins/MCCases` folder, the SQL database,
   and every world's player data together. Include `secret.key`, custom catalogs, textures and the
   player PDC journals. With SQLite, copy the database only after shutdown (or use SQLite's backup
   API); copying just the live `.db` while WAL is active is not a consistent backup.
2. Use Java 25 and Paper 26.3. This release compiles against build 159 beta. First verify the upgrade
   on a copy of your world and database. Paper's official setup guidance is at
   https://docs.papermc.io/paper/dev/project-setup/.
3. Replace the old MCCases JAR with `release/MCCases-1.1.0.jar` (also built at `build/libs/MCCases-1.1.0.jar`). Keep the data folder and
   `secret.key`. Do not install both versions. The optional pack is bundled; the matching standalone
   artifact is `release/MCCases-ResourcePack-1.1.0.zip` (also in `build/distributions/`).
4. Start normally. Migration to schema 3 creates new payment/claim/contract tables. Existing Coin
   wallets stay unchanged in `<prefix>wallets`. Old offers move to `<prefix>legacy_market_listings`;
   their original skins become OWNED again. No old price becomes an Emerald price. Existing skin
   rows and roll properties are preserved. Pending opening receipts are replayed on login.
5. Missing config/message/sound leaves are merged on enable. Existing values are preserved.
   Old `starting-balance`, `currency-name`, `max-price` and `opening.block-commands` values are
   legacy settings and no longer govern item payments or opening locks. Review the new
   `market.yml` `emeralds` section, `opening.max-active-*` and `trade-in` settings.
6. Check `/market balance`, publish a small test offer, buy with real Emeralds, then collect through
   `/market claims`. Check direct-trade selection and ordinary consecutive openings.

`/market credit` has been removed. `/market legacy <known-player>` requires `mccases.admin` and
reads old Coin balances without writing anything. An administrator can export the old wallet table
and retain it for audit or manually grant explicitly agreed compensation through vanilla item tools.
This release intentionally has no automatic conversion command or exchange rate.

New permissions are `mccases.tradein` (default true); marketplace uses `mccases.market`, direct trades
use `mccases.trade`, and `/openings` uses `mccases.use`. No Vault or client mod is needed.

## Item transaction recovery

A purchase reserves its listing in SQL (PREPARED). On the main thread, the current inventory is
rechecked and exactly the price is removed. A PAY receipt with UUID and amount is written into
player PDC and `Player.saveData()` is invoked. The skin transfer, listing removal, seller's item claim
and DONE journal state then commit together in SQL. No receipt means no debit was durably recorded;
recovery cancels that preparation. A receipt means the same prepared transaction is completed,
using the same skin and claim IDs. A DONE transaction is acknowledged by removing its receipt.

Claims are physical Emerald items awaiting delivery, not spendable virtual balances. A delivery
reserves a claim quantity in SQL without reducing its remaining count. The main thread rechecks
capacity, adds that whole reserved quantity without dropping leftovers, saves a GIVE receipt with
inventory, then commits exactly that claim reduction in SQL. If capacity changed, the reservation
is canceled and the entire attempted delivery is deferred. Each attempt may select a smaller
quantity first when `emeralds.partial-claims` is true. Replaying an already DONE delivery is a no-op.

Online sellers also collect explicitly. This keeps one payout protocol for online/offline/full
inventories. SQL errors retain paid/delivered receipts and block further financial mutations for
that player until `/market recover` or login reconciles them. Reconnect after a disconnect; pending
operations remain in SQL until that player's saved PDC can be examined. A server restart does not
blindly refund unknown transactions or discard unmatched preparations.

Opening journal v2 stores the skin and original opening audit record. A failed SQL acknowledgement
keeps both for idempotent replay instead of refunding a possibly committed reward. Legacy v1 skin-only
receipts still replay safely, but cannot reconstruct an opening ID that was never stored in them.

## Limits of the persistence boundary

Minecraft player files and SQL do **not** share an ACID transaction. The protocol relies on the
inventory and PDC receipt being captured by the same player save, and on that save remaining durable
before SQL completion. `saveData()` does not expose a cross-system fsync guarantee. Disk failure,
filesystem corruption, third-party plugins replacing inventories/PDC, or restoring only one side of
a backup can still break that assumption. Restoring an older player file after a DONE SQL purchase
can restore spent items; restoring after a DONE payout can erase delivered items.

Do not manually delete receipts, reset PREPARED rows, or restore player files separately from SQL.
In a disputed storage failure, preserve both backups and use UUIDs in `market_payments`,
`emerald_deliveries`, `emerald_claims`, `commerce_log`, `trade_contracts` and opening records to audit
what was acknowledged. The plugin will not guess compensation when saved state was externally lost.
No absolute power-loss, external rollback or cross-database guarantee is claimed.

One running MCCases instance owns a database/table prefix. This is a single-server design with many
players; sharing the same live tables between servers is unsupported. SQLite is regression-tested;
MySQL/MariaDB require separate environment validation. Once schema 3 is written, downgrade only by
restoring the complete pre-upgrade backup, not by running the old plugin against the new database.
