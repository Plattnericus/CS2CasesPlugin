package dev.plattnericus.cases.gui;

import dev.plattnericus.cases.catalog.Catalog;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.skin.SkinInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Applies category, filters and sorting to a player's owned skins. */
public final class SkinQuery {

    public record Entry(SkinInstance instance, SkinDefinition definition) {
    }

    private static final int RECENT_LIMIT = 36;

    private SkinQuery() {
    }

    public static List<Entry> run(Catalog catalog, List<SkinInstance> owned, MenuStates.State state) {
        List<Entry> out = new ArrayList<>();
        for (SkinInstance inst : owned) {
            SkinDefinition def = catalog.skin(inst.skinId());
            if (def == null || !matchesCategory(state.category, inst, def) || !matchesFilter(catalog, state, inst, def)) {
                continue;
            }
            out.add(new Entry(inst, def));
        }
        Comparator<Entry> order = switch (state.category == MenuStates.Category.RECENT ? MenuStates.Sort.NEWEST : state.sort) {
            case RARITY -> Comparator.<Entry>comparingInt(e -> e.definition().rarity().order()).reversed()
                    .thenComparing(e -> e.definition().displayName());
            case FLOAT -> Comparator.comparingDouble(e -> e.instance().floatValue());
            case NAME -> Comparator.comparing(e -> e.definition().displayName());
            case WEAR -> Comparator.<Entry>comparingInt(e -> catalog.wear().tiers().indexOf(catalog.wear().of(e.instance().floatValue())))
                    .thenComparingDouble(e -> e.instance().floatValue());
            case PATTERN -> Comparator.comparingInt(e -> e.instance().pattern());
            case NEWEST -> Comparator.<Entry>comparingLong(e -> e.instance().createdAt()).reversed();
            case OLDEST -> Comparator.comparingLong(e -> e.instance().createdAt());
        };
        out.sort(order);
        if (state.category == MenuStates.Category.RECENT && out.size() > RECENT_LIMIT) {
            return new ArrayList<>(out.subList(0, RECENT_LIMIT));
        }
        return out;
    }

    private static boolean matchesCategory(MenuStates.Category c, SkinInstance inst, SkinDefinition def) {
        return switch (c) {
            case ALL, RECENT -> true;
            case WEAPONS -> !def.weapon().category().isStarItem();
            case KNIVES -> def.isKnife();
            case STATTRAK -> inst.statTrak();
            case FAVORITES -> inst.favorite();
        };
    }

    private static boolean matchesFilter(Catalog catalog, MenuStates.State s, SkinInstance inst, SkinDefinition def) {
        if (!s.rarities.isEmpty() && !s.rarities.contains(def.rarity().id())) {
            return false;
        }
        if (!s.wears.isEmpty() && (!def.hasWear() || !s.wears.contains(catalog.wear().of(inst.floatValue()).id()))) {
            return false;
        }
        return !s.statTrakOnly || inst.statTrak();
    }
}
