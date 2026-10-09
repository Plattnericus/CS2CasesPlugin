package dev.plattnericus.cases.tradein;

import dev.plattnericus.cases.catalog.Catalog;
import dev.plattnericus.cases.skin.SkinInstance;
import java.util.Comparator;
import java.util.List;

/** Stable ordering and a compatible fill plan, independent of menu pages. */
public final class TradeInSelection {
    private TradeInSelection() { }
    public enum Sort { NEWEST, NAME, RARITY, FLOAT_ASC, FLOAT_DESC }
    public static List<SkinInstance> filter(Catalog catalog, List<SkinInstance> skins, String weapon, String rarity, int statTrak) {
        return skins.stream().filter(s -> (weapon == null || catalog.skin(s.skinId()).weapon().id().equals(weapon))
                && (rarity == null || catalog.skin(s.skinId()).rarity().id().equals(rarity))
                && (statTrak == 0 || s.statTrak() == (statTrak == 2))).toList();
    }
    public static List<SkinInstance> sorted(Catalog catalog, List<SkinInstance> skins, Sort sort) {
        Comparator<SkinInstance> order = switch (sort) {
            case NEWEST -> Comparator.comparingLong(SkinInstance::createdAt).reversed();
            case NAME -> Comparator.comparing(s -> catalog.skin(s.skinId()).displayName(), String.CASE_INSENSITIVE_ORDER);
            case RARITY -> Comparator.<SkinInstance>comparingInt(s -> catalog.skin(s.skinId()).rarity().order()).reversed();
            case FLOAT_ASC -> Comparator.comparingDouble(SkinInstance::floatValue);
            case FLOAT_DESC -> Comparator.comparingDouble(SkinInstance::floatValue).reversed();
        };
        return skins.stream().sorted(order.thenComparing(SkinInstance::id)).toList();
    }
    public static List<SkinInstance> fill(Catalog catalog, List<SkinInstance> candidates,
                                          List<SkinInstance> selected, boolean allowAdmin) {
        var safe = candidates.stream().filter(s -> TradeInRules.eligible(catalog, s, allowAdmin)
                && !s.favorite() && selected.stream().noneMatch(chosen -> chosen.id().equals(s.id()))).toList();
        record Group(java.util.UUID owner, String rarity, boolean statTrak) { }
        java.util.function.Function<SkinInstance, Group> key = s -> new Group(s.owner(), catalog.skin(s.skinId()).rarity().id(), s.statTrak());
        var counts = safe.stream().collect(java.util.stream.Collectors.groupingBy(key, java.util.stream.Collectors.counting()));
        SkinInstance first = selected.isEmpty() ? safe.stream().filter(s -> counts.get(key.apply(s))
                >= TradeInRules.required(TradeInRules.target(catalog, catalog.skin(s.skinId())))).findFirst().orElse(null)
                : selected.getFirst();
        if (first == null) return List.of();
        int remaining = Math.max(0, TradeInRules.required(TradeInRules.target(catalog, catalog.skin(first.skinId()))) - selected.size());
        return safe.stream().filter(s -> TradeInRules.compatible(catalog, first, s, allowAdmin)).limit(remaining).toList();
    }
}
