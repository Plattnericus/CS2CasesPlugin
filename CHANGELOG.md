# Changelog

## 1.3.0 — 2026-10-17

- **Skin-arm view model.** Your own Minecraft skin arms (wide or slim) hold every knife and gun in
  first person, with the 3D weapon rig and its metal finish: grip in the palm, support hand on the
  handguard, reverse grip for Karambit and Talon, Shadow Daggers in both hands. Left-handed players get
  a mirrored view. The arms come from each player's own skin; no skin is stored in the pack.
- **Use poses.** Drawing a bow aims, charging a crossbow reloads and a loaded crossbow stays ready;
  the arms keep holding the weapon. Third-person holds put the grip into the palm, blade or muzzle forward.
- **Held-pose inspects.** 66 animations in eleven families plus weapon specials. They start and end in
  the hold; the wrist turns the weapon, elbow and shoulder follow, the forearm twists and the support
  hand lets go and grabs again. Slides rack, bolts pull, pumps cycle, butterfly handles swing around the
  blade and folding blades flick closed and open. Every playback varies in energy and roll direction.
- **Inspect in every view.** The scene rides the player and stays locked to the view, also while
  crouching and standing up. Other players and your own F5 view see the player model raise both arms
  into the crossbow hold and turn the weapon in its real hand, always clear of head and torso. The
  pack's shaders pick the right scene for the camera, so F5 needs no command.
- **Seamless controls.** The inspected item stays in its hotbar slot; the hand never dips when the
  inspect starts or ends. Shift + right click starts the inspect once you stand up. Bows and crossbows
  do not draw, charge or fire while they are inspected.
- **Resource pack.** Vanilla 26.3 entity shaders for the skin arms; the item shaders combine the metal
  sheen with the per-pixel split between first- and third-person scenes. Rig layers carry scene marks
  on every face. Key icons have hard edges.
- **Settings.** `inspect.held-pose` (default `true`). Gloves, inspects from menus and servers without the
  pack keep the 362 profile inspects.
- **Checks.** `verifyHeld` covers every weapon's grips, arm signatures, handedness and framing, all
  held-pose inspects and rig layers, clearance from the player model in seven head poses and the pack's
  shaders, arms and scene layers. `scripts/visual_checks.py` captures real-client screenshots in first
  person, F5, left-handed and from a second player.

## 1.2.3 — 2026-10-10

- Keep all 815 original Fusion skin sprites and 1,377 inspect layers as durable, hashed source artwork. Rebuild opaque geometry from the selected artwork so UVs and silhouette stay aligned.
- Use solid inspect meshes for ordinary held items, anchor each grip at the palm and mirror left-hand geometry. Keep original sprites in inventory, ground and fixed display contexts.
- Add restrained Vanilla 26.3 metal shading with simulated environment sheen on marked weapon faces, retaining the native fog, lighting and transparency pipeline.
- Reuse immutable animation schedules and skip unnecessary stationary samples while preserving inherited motion and full turns.
- Give menus a quieter frame and use the original pumpkin, deer and prosse glyphs in pack-enabled titles.
- Add persistent local supervision: keep a dev server and client available independently of the desktop app, restart exited processes and preserve the dev world.

## 1.2.2 — 2026-10-10

- Make Fusion HD the default build, embedded pack, development-client pack and runtime export. Store the supplied server font definition, seven glyph PNGs and pack icon permanently in the project with exact source hashes.
- Regenerate the MCCases namespace together so current 128px textures, item models and ring-knife geometry stay compatible. Preserve custom server assets byte-for-byte.
- Refresh old/edited installed packs from the actual server catalog, preserve custom catalog export receipts, skip unchanged restarts and keep the previous ZIP on incomplete exports or missing-font errors.
- Verify all generated/preserved assets, model/texture references, fonts, deterministic merging and installation/failure recovery.

## 1.2.1 — 2026-10-10

- Rebuild the standard pack with 128px skin and inspect textures, fractional premultiplied alpha filtering, clean cutout edges and an unclipped knife rotation canvas.
- Turn Karambit and Talon presentation into reverse grip, spin around the actual finger ring, center the moving knife and restore the held pose after reverse catches. Upgrade unchanged stock timelines while preserving custom profiles.
- Persist direct-trade history independently of CASE/TRADE_IN/admin provenance. Show “Knife traded (original source)” in normal and precise lore; backfill recorded older direct trades when upgrading to schema 4.
- Add localized permission feedback before hidden command roots reject player input, including aliases and plugin namespaces.
- Restore black stained glass and enlarge the complete wheel/grid by 3×, including icons, text, markers and click targets. Keep nine simultaneous openings, bounded display windows, cached icons and a shared tick track. Validate scale configuration with English error feedback.
- Keep English as the fixed default for every client; publish the English README, current reference, release report and legal information with matching versioned downloads and verification evidence.

## 1.2.0 — refreshed build 2026-10-10

- Package the current 815-skin/63-model catalog, 252 inspect variants, simultaneous 1–9 world openings and trade-in improvements in the public JAR and matching standard pack.
- Replace the README with a German installation and player guide; retain the complete English command/configuration reference in `docs/REFERENCE.md`.
- Include project contact, trademark/asset notices and the stated Italy/free-currency operating model in the JAR, exported packs and first-run defaults.
- Add an Italian privacy template with explicit fields for operator identity, hosting and retention; document actual storage and disclosure behavior.
- Preserve the existing project license status; document separately licensed build tooling and the unresolved rights of additional Fusion-HD artwork.
- Refresh release checksums; historical visual evidence remains dated separately.

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
