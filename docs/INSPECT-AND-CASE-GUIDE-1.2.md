# MCCases 1.2: weapon rigs and case guide

All 55 catalog weapons have three individually named inspect timelines: 20 knife models and 35 weapons, 165 profiles in total. The 713 existing skins and 21 cases keep their IDs and pools. The CS20 Case adds 17 weapon skins and 13 Classic Knife finishes, bringing the catalog to 743 skins in 22 cases. Each normal inspect randomly selects a variant and avoids the previous variant. Gold-drop showcases keep their short reveal animation.

## Models and joints

The pack includes a textured inspect mesh for every skin, separate from the inventory and equipped-item model. Its silhouette comes from the existing weapon canvas. Contiguous opaque pixels become textured cuboids; blades, handles and gun bodies have different thicknesses. This makes the model visible from either side, rather than rotating a single flat item sprite. Front/back UVs preserve the finish; side faces carry the same texture.

Butterfly knives have a blade and two independent handles around the tang. Flip, Navaja, Stiletto and Falchion have a blade hinge. Shadow Daggers and Dual Berettas have two independent weapon groups. Other guns have a separate inspect mechanism for the slide, bolt, pump, cover or magazine region. These are cosmetic checks and do not consume ammunition or change combat behavior.

Knife gestures cover ring spins, reverse grips, hinge flicks, Butterfly rollovers/helixes/fans, Falchion balance, Skeleton tosses and heavier blade rolls. Choreography, timing, pivots and variants are editable in `inspect-profiles.yml`. `scripts/generate_inspect_profiles.py` reproduces the bundled file.

The geometry and movement are CS-inspired adaptations for Minecraft, not Valve meshes, original CS animation files or an exact CS hand model. The shipped silhouettes are cuboid meshes made from the project's existing artwork. Pack rendering uses the skin definition's showcase texture; individual float/pattern textures are still handled by the existing map render system, not generated as one pack model per owned instance.

## Player perspectives

The inspecting player gets a private view-relative scene; other players get the separate body-hand scene. The owner cannot see both simultaneously, and other players cannot see the private eye scene. Left-hand mode mirrors both scenes. `/inspect hand` explicitly chooses the body-hand anchor for F5; `/inspect eye` returns to the view-relative anchor. Paper cannot read the client's F5 setting, so changing F5 alone does not switch the server anchor.

The new default eye anchor is 1.75 blocks forward and 0.43 to the player's right. Frame checks cover the entire timeline at the default 70-degree FOV, including a 4:3 screen, in both hands and with/without the pack. The loader applies this anchor only to the old stock `1.25 / 0.57 / -0.07` values. Explicit server anchor edits remain unchanged. Disable profiles with `inspect-profiles.yml -> enabled: false` to use the existing `inspect.yml` animation pools and anchor.

These are world display entities, so terrain can occlude them. Arbitrary client zoom, custom anchors and custom resource packs are outside the default framing check. Rigs do not replace Minecraft's first-person arms. Display cleanup covers normal completion, quit, death, teleport, world/slot changes, disable and interrupted scene creation. Invalid scale, brightness, nonfinite transforms, cyclic joints and oversized timelines are rejected or bounded before a scene starts.

## Case filters

`/cases` defaults to best value. Controls allow value, full price ascending/descending, knife preference, owned count or name; all/favorite/specific knife models; total-price budget; case name/ID search; owned-only cases; and reset. Right-click reverses sort/knife cycling or clears search/budget. Preview and Back preserve the chosen filters. Case opening remains the normal single-case action; this does not add bulk opening or change odds.

Full price is the current dealer's case price plus its matching key price, from `shop.yml`. An excluded case or unavailable key has an unknown dealer price and cannot qualify under a numeric budget. Prices use long arithmetic, including two `Integer.MAX_VALUE` offers, to avoid overflow. The currency label follows the dealer setting; the bundled setting is Diamonds.

Value is expected reward points divided by full dealer price:

```
EV = sum over nonempty tiers(tier chance * average points of skins in that tier)
value = EV / (dealer case price + matching key price)
points = rarity points * knife factor * finish multiplier
knife factor = 0.5 + curated knife score / 100 (ordinary weapons use 1)
```

Tier chances use the same `RewardRoller.chance` normalization as actual rewards. Skins within a tier are equally likely. Specific/favorite knife chances sum their actual skin probabilities, not the preference score. The average knife preference is conditional on obtaining a knife; it is not a different drop rate. Float, pattern and StatTrak add no point premium. Points are an editable preference system, not currency or promised trading proceeds.

`case-guide.yml` contains the tier points, finish multipliers, 20 knife scores, favorite threshold and source snapshot. Defaults are 1 / 8 / 40 / 220 / 1800 points across the five tiers; favorite threshold 85. The GUI shows the configured tier values and current dealer cost.

The CS20 contents and rarities were checked against the [Valve Steam container listing](https://steamcommunity.com/market/listings/730/CS20%20Case); Valve's [original release post](https://blog.counter-strike.net/2019/10/25884/) confirms the Classic Knife. The new paint palettes follow the project's procedural style system.

## Preference sources

The top-six preset follows the publisher's demand order in the [CS2.IO knife catalog](https://cs2.io/en/skins/knives), retrieved 2026-10-08: Butterfly, Skeleton, Talon, Karambit, M9 Bayonet and Stiletto. [Community Butterfly discussions](https://www.reddit.com/r/csmoneyofficial/comments/1vzzwxp/knives_tier_list_after_your_votes_navaja_knife_is/) supply additional qualitative context. Numeric scores and the remaining model order are editorial server preferences, not percentages from an official poll, a universal community consensus or a live feed. Administrators can replace the entire preset.

## Install

Use `release/MCCases-1.2.0.jar` with `release/MCCases-ResourcePack-1.2.0.zip`. Update both together: the new inspect meshes require the new pack. Enable `resource-pack.enabled: true` only after clients receive that pack, or configure the built-in distribution as described in the README. Without the pack, articulated block models remain available. The pack stays within the configured namespace and does not replace vanilla files. Existing admin config files and catalog data are preserved; new profile/guide files and missing language entries are added. `inspect-profiles.yml` supplies the new individual pools independently of the old file.

As in 1.1, stop the server and back up the plugin data folder, database and player files together before replacing the plugin. The development checks JAR and client capture harness are audit tools; install neither on the production server.
