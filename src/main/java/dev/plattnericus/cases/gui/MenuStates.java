package dev.plattnericus.cases.gui;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Remembers category, sorting and filters of each player's skin inventory for the session. */
public final class MenuStates {

    public enum Category { ALL, WEAPONS, KNIVES, STATTRAK, FAVORITES, RECENT, GLOVES }

    public enum Sort { RARITY, FLOAT, NAME, WEAR, PATTERN, NEWEST, OLDEST }

    public static final class State {
        public Category category = Category.ALL;
        public Sort sort = Sort.RARITY;
        public final Set<String> rarities = new HashSet<>();
        public final Set<String> wears = new HashSet<>();
        public boolean statTrakOnly;
        public int page;
        /** Invalidates asynchronous collection requests when another view is requested or opened. */
        public long inventoryRequestVersion;

        public boolean hasFilter() {
            return !rarities.isEmpty() || !wears.isEmpty() || statTrakOnly;
        }

        public void resetFilter() {
            rarities.clear();
            wears.clear();
            statTrakOnly = false;
        }
    }

    private final Map<UUID, State> states = new HashMap<>();

    public State get(UUID player) {
        return states.computeIfAbsent(player, k -> new State());
    }

    public void clear(UUID player) {
        states.remove(player);
    }

    public static Set<Category> all() {
        return EnumSet.allOf(Category.class);
    }
}
