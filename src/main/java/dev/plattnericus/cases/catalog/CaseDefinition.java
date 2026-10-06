package dev.plattnericus.cases.catalog;

import java.util.List;
import java.util.Map;

/** A case and its drop pool, grouped by rarity id. */
public record CaseDefinition(String id, String name, String keyId, int color, String icon,
                             List<String> description, Map<String, List<SkinDefinition>> pool,
                             double statTrakChance, boolean enabled) {

    public CaseDefinition {
        description = List.copyOf(description);
        pool = Map.copyOf(pool);
    }

    public List<SkinDefinition> skins(Rarity rarity) {
        return pool.getOrDefault(rarity.id(), List.of());
    }

    public int size() {
        return pool.values().stream().mapToInt(List::size).sum();
    }
}
