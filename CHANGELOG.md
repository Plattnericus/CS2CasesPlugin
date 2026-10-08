# Changelog

## 1.2.0 — 2026-10-08

- CS20 Case with 17 weapons and 13 Classic Knife finishes; all 55 weapon IDs now have obtainable skins.
- Separate textured 3D inspect meshes and joints for all 55 weapons, including articulated Butterfly handles, folding blades and paired daggers/pistols.
- Three individual random inspect variants per weapon, with no immediate repeats.
- Case value/price/preference sorting, knife filters, budget/search/owned filters and preserved preview navigation.
- Transparent dealer-price reward points and exact target-knife probabilities, with editable preference sources.
- Full-timeline framing checks for right/left hand, pack/fallback geometry and the default 70° FOV on 4:3 screens; safer malformed rigs and scene cleanup.
- Build version is now an explicit resource input so plugin.yml cannot retain a previous release number.


## 1.1.0 — 2026-10-08

- Real Emerald marketplace payments from storage and optional offhand; no new Coin wallets or credits.
- Durable payment reservations, UUID-based item claims, partial collection and player-file receipts for recovery.
- Schema 3: archive Coin-priced listings, return listed skins and preserve all old wallets without conversion.
- 54-slot marketplace/details and collection browsers; private chat search and exact prices, rarity/category filters, price/rarity/float sorting and stale-offer markers.
- Independent case-opening sessions, ordinary repeat clicks, bounded world-reel display pools, separate lanes, hidden GUI animations and `/openings`.
- Opening journal v2 preserves the original reward and opening audit record; v1 journals remain readable.
- 54-slot direct-trade collection picker with persistent green selection, synchronized offers, revision confirmation and configurable review/limits.
- Separate same-tier trade-in contracts, five-Covert gold contracts, atomic input/output exchange and configurable at-most-once Gold broadcasts.
- Missing YAML defaults merged without overwriting configured leaves; complete English/German text and other-language fallbacks.
- Paper API pinned to 26.3 build 159 beta; Java 25 retained. Updated isolated development launcher and regression/runtime checks.

The case dealer continues to use diamonds. Catalogs, existing skin IDs, floats, patterns, wear seeds,
StatTrak kills and origin data are preserved. See `docs/UPGRADE-1.1.md` before installation.
