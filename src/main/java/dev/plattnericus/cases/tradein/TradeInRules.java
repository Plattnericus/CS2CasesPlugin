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
        return sources(catalog, input, target).stream().flatMap(source -> source.skins(target).stream())
                .filter(def -> !input.statTrak() || def.statTrakEligible()).distinct().toList();
    }
    /** Direct admin grants have no original case; infer only cases containing that exact skin. */
    public static List<dev.plattnericus.cases.catalog.CaseDefinition> sources(Catalog catalog, SkinInstance input, Rarity target) {
        if (input == null || target == null) return List.of();
        var source = input.sourceCase() == null ? null : catalog.caseDefinition(input.sourceCase());
        if (source != null) return List.of(source);
        if (!input.origin().admin()) return List.of();
        return catalog.casesForSkin(input.skinId()).stream()
                .filter(c -> c.skins(target).stream().anyMatch(def -> !input.statTrak() || def.statTrakEligible())).toList();
    }
    /** Eligibility is shared by selection, automatic filling and final validation. */
    public static boolean eligible(Catalog catalog, SkinInstance input, boolean allowAdmin) {
        if (input == null || input.status() != SkinInstance.Status.OWNED || input.origin() == SkinInstance.Origin.TEST
                || !allowAdmin && input.origin().admin()) return false;
        var tier = target(catalog, catalog.skin(input.skinId()));
        return tier != null && !pool(catalog, input, tier).isEmpty();
    }
    public static boolean compatible(Catalog catalog, SkinInstance first, SkinInstance candidate, boolean allowAdmin) {
        return eligible(catalog, first, allowAdmin) && eligible(catalog, candidate, allowAdmin)
                && first.owner().equals(candidate.owner()) && first.statTrak() == candidate.statTrak()
                && catalog.skin(first.skinId()).rarity().id().equals(catalog.skin(candidate.skinId()).rarity().id());
    }
    public static Rarity validate(Catalog catalog, List<SkinInstance> inputs, boolean allowAdmin) {
        if (inputs.isEmpty() || inputs.stream().map(SkinInstance::id).distinct().count() != inputs.size()) throw new IllegalArgumentException("tradein.invalid");
        var first = catalog.skin(inputs.getFirst().skinId()); var tier = target(catalog, first);
        if (tier == null || inputs.size() != required(tier)) throw new IllegalArgumentException("tradein.count");
        for (var s : inputs) {
            if (!compatible(catalog, inputs.getFirst(), s, allowAdmin)) throw new IllegalArgumentException("tradein.invalid");
        }
        return tier;
    }
    public record Outcome(SkinDefinition skin, String sourceCase) { }
    public static Outcome roll(Catalog catalog, List<SkinInstance> inputs, Rarity target, RandomGenerator random) {
        var input = inputs.get(random.nextInt(inputs.size()));
        var cases = sources(catalog, input, target); var source = cases.get(random.nextInt(cases.size()));
        var pool = source.skins(target).stream().filter(def -> !input.statTrak() || def.statTrakEligible()).toList();
        return new Outcome(pool.get(random.nextInt(pool.size())), source.id());
    }
    public static SkinDefinition choose(Catalog catalog, List<SkinInstance> inputs, Rarity target, RandomGenerator random) {
        return roll(catalog, inputs, target, random).skin();
    }
    public record Chance(SkinDefinition skin, double probability) { }
    /** Exact probabilities of the same input -> source case -> skin draw used on confirmation. */
    public static List<Chance> chances(Catalog catalog, List<SkinInstance> inputs, Rarity target) {
        if (inputs.isEmpty() || target == null) return List.of();
        var probabilities = new java.util.LinkedHashMap<SkinDefinition, Double>();
        for (var input : inputs) {
            var cases = sources(catalog, input, target);
            for (var source : cases) {
                var pool = source.skins(target).stream().filter(def -> !input.statTrak() || def.statTrakEligible()).toList();
                double probability = 1.0 / inputs.size() / cases.size() / pool.size();
                for (var skin : pool) probabilities.merge(skin, probability, Double::sum);
            }
        }
        return probabilities.entrySet().stream().map(e -> new Chance(e.getKey(), e.getValue()))
                .sorted(java.util.Comparator.comparing(c -> c.skin().displayName(), String.CASE_INSENSITIVE_ORDER)).toList();
    }
    public static double outputFloat(List<SkinInstance> inputs, SkinDefinition output) {
        double average = inputs.stream().mapToDouble(SkinInstance::floatValue).average().orElseThrow();
        return Math.clamp(output.minFloat() + average * (output.maxFloat() - output.minFloat()), output.minFloat(), output.maxFloat());
    }
}
