package dev.plattnericus.cases.tradein;

import dev.plattnericus.cases.catalog.Catalog;
import dev.plattnericus.cases.catalog.Rarity;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.skin.SkinInstance;
import java.util.List;
import java.util.random.RandomGenerator;

/** CS2-inspired: ten same-tier weapons; five Covert weapons for a rare special item. */
public final class TradeInRules {
    private TradeInRules() { }
    public static Rarity target(Catalog catalog, SkinDefinition input) {
        if (input == null || input.rarity().rareSpecial()) return null;
        return catalog.raritiesOrdered().stream().filter(r -> r.order() > input.rarity().order()).findFirst().orElse(null);
    }
    public static int required(Rarity target) { return target != null && target.rareSpecial() ? 5 : 10; }
    public static List<SkinDefinition> pool(Catalog catalog, SkinInstance input, Rarity target) {
        var source = catalog.caseDefinition(input.sourceCase()); return source == null ? List.of() : source.skins(target);
    }
    public static Rarity validate(Catalog catalog, List<SkinInstance> inputs, boolean allowAdmin) {
        if (inputs.isEmpty() || inputs.stream().map(SkinInstance::id).distinct().count() != inputs.size()) throw new IllegalArgumentException("tradein.invalid");
        var first = catalog.skin(inputs.getFirst().skinId()); var tier = target(catalog, first);
        if (tier == null || inputs.size() != required(tier)) throw new IllegalArgumentException("tradein.count");
        for (var s : inputs) {
            var def = catalog.skin(s.skinId());
            if (s.status() != SkinInstance.Status.OWNED || def == null || !s.owner().equals(inputs.getFirst().owner())
                    || !def.rarity().id().equals(first.rarity().id()) || s.statTrak() != inputs.getFirst().statTrak()
                    || s.origin() == SkinInstance.Origin.TEST || !allowAdmin && s.origin() == SkinInstance.Origin.ADMIN
                    || pool(catalog, s, tier).isEmpty()) throw new IllegalArgumentException("tradein.invalid");
        }
        return tier;
    }
    public static SkinDefinition choose(Catalog catalog, List<SkinInstance> inputs, Rarity target, RandomGenerator random) {
        var pool = pool(catalog, inputs.get(random.nextInt(inputs.size())), target); return pool.get(random.nextInt(pool.size()));
    }
    public static double outputFloat(List<SkinInstance> inputs, SkinDefinition output) {
        double average = inputs.stream().mapToDouble(SkinInstance::floatValue).average().orElseThrow();
        return Math.clamp(output.minFloat() + average * (output.maxFloat() - output.minFloat()), output.minFloat(), output.maxFloat());
    }
}
