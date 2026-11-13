package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.catalog.*;
import dev.plattnericus.cases.config.Messages;
import dev.plattnericus.cases.items.SkinFormatter;
import dev.plattnericus.cases.render.TextureStore;
import dev.plattnericus.cases.skin.*;
import dev.plattnericus.cases.tradeup.*;
import dev.plattnericus.cases.util.Text;
import java.io.File;
import java.util.*;

/** Contract browsing uses the real catalog, filters, selection planner and lore formatter. */
public final class TradeUpChecks {
    private TradeUpChecks() { }
    public static void run(File root) {
        var catalog = new CatalogLoader(new File(root, "catalog"), new TextureStore(new File(root, "textures"))).load(1).catalog();
        var source = catalog.caseDefinition("kilowatt_case");
        var tier = catalog.raritiesOrdered().getFirst();
        var a = source.skins(tier).getFirst();
        var b = source.skins(tier).stream().filter(s -> !s.weapon().id().equals(a.weapon().id())).findFirst().orElseThrow();
        UUID owner = UUID.randomUUID(); var skins = new ArrayList<SkinInstance>();
        for (int i = 0; i < 48; i++) skins.add(instance(owner, i % 2 == 0 ? a : b, source.id(), i / 50.0, i, false, SkinInstance.Origin.CASE));
        skins.getFirst().setFavorite(true);
        for (var sort : TradeUpSelection.Sort.values()) {
            var ordered = TradeUpSelection.sorted(catalog, skins, sort);
            require(new HashSet<>(ordered).equals(new HashSet<>(skins)), "sort lost skin identities");
            require(ordered.equals(TradeUpSelection.sorted(catalog, skins.reversed(), sort)), "unstable sorting " + sort);
        }
        var ascending = TradeUpSelection.sorted(catalog, skins, TradeUpSelection.Sort.FLOAT_ASC);
        var descending = TradeUpSelection.sorted(catalog, skins, TradeUpSelection.Sort.FLOAT_DESC);
        require(ascending.getFirst().floatValue() == 0 && descending.getFirst().floatValue() == .94, "float order wrong");
        require(TradeUpSelection.sorted(catalog, skins, TradeUpSelection.Sort.NEWEST).getFirst() == skins.getLast(), "newest order wrong");
        var onlyA = TradeUpSelection.filter(catalog, descending, a.weapon().id(), tier.id(), 1);
        require(onlyA.size() == 24 && onlyA.stream().allMatch(s -> s.skinId().equals(a.id())), "weapon/rarity filter leaked another weapon");
        require(TradeUpSelection.filter(catalog, skins, null, null, 2).isEmpty(), "StatTrak filter leaked normal skins");
        var filled = TradeUpSelection.fill(catalog, onlyA, List.of(), true);
        require(filled.size() == 10 && filled.getFirst() == onlyA.getFirst() && filled.stream().noneMatch(SkinInstance::favorite), "fill ignored filter/order/favorites");
        require(!TradeUpRules.validate(catalog, filled, true).rareSpecial(), "filtered weapon contract failed");
        var chosen = filled.subList(0, 3);
        var remaining = TradeUpSelection.fill(catalog, onlyA, chosen, true);
        require(remaining.size() == 7 && remaining.stream().noneMatch(chosen::contains), "fill duplicated selections or crossed pages incorrectly");
        require(TradeUpSelection.fill(catalog, onlyA.subList(0, 4), List.of(), true).isEmpty(), "incomplete automatic contract selected");
        var stat = instance(owner, a, source.id(), .3, 90, true, SkinInstance.Origin.CASE);
        var higher = instance(owner, source.skins(catalog.raritiesOrdered().get(1)).getFirst(), source.id(), .3, 91, false, SkinInstance.Origin.CASE);
        require(!TradeUpRules.compatible(catalog, skins.getFirst(), stat, true)
                && !TradeUpRules.compatible(catalog, skins.getFirst(), higher, true), "incompatible input selectable");
        require(!TradeUpRules.eligible(catalog, instance(owner, a, null, .3, 92, false, SkinInstance.Origin.CASE), true), "unknown real case provenance eligible");
        var directAdmin = instance(owner, a, "admin", .3, 92, false, SkinInstance.Origin.ADMIN);
        require(TradeUpRules.eligible(catalog, directAdmin, true) && !TradeUpRules.eligible(catalog, directAdmin, false), "configured direct admin weapon cannot be selected");
        require(TradeUpRules.sources(catalog, directAdmin, TradeUpRules.target(catalog,a)).stream()
                .allMatch(c -> c.pool().values().stream().flatMap(List::stream).anyMatch(def -> def.id().equals(a.id()))), "admin case inference used an unrelated skin");
        var adminTrade = instance(owner, a, source.id(), .3, 93, false, SkinInstance.Origin.ADMIN_TRADE_IN);
        require(adminTrade.origin().admin() && adminTrade.origin().tradeUp()
                && !TradeUpRules.eligible(catalog, adminTrade, false) && TradeUpRules.eligible(catalog, adminTrade, true), "admin lineage lost after trade-up");
        require(InstanceCodec.decode(InstanceCodec.encode(adminTrade)).origin() == adminTrade.origin(), "journal dropped contract provenance");
        randomRewards(catalog, owner);
        var messages = new Messages(new File(root, "messages_en.yml"), null, null);
        var formatter = new SkinFormatter(messages, catalog, 6, 8, "yyyy-MM-dd");
        for (SkinDefinition output : List.of(a, source.skins(catalog.raritiesOrdered().getLast()).getFirst())) {
            for (var origin : List.of(SkinInstance.Origin.TRADE_IN, SkinInstance.Origin.ADMIN_TRADE_IN)) {
                var reward = instance(owner, output, source.id(), .3, 94, false, origin);
                for (boolean precise : List.of(false, true)) require(formatter.lore(output, reward, precise).stream()
                        .map(Text::plain).anyMatch(line -> line.equals("Source: TRADE UP")), "weapon/knife inventory source incorrect");
                require(reward.sourceCase().equals(source.id()), "display source destroyed the case used for the next contract");
            }
        }
        System.out.println("PASS: five stable trade-up sorts, exact weapon/rarity/StatTrak filters, 48-item cross-page filling, favorite protection, 10/5 input validation and weapon/knife Source: TRADE UP with retained admin lineage.");
    }
    private static void randomRewards(Catalog catalog, UUID owner) {
        var first = catalog.caseDefinition("kilowatt_case");
        var second = catalog.caseDefinition("glove_case");
        var inputTier = catalog.raritiesOrdered().getFirst();
        var target = catalog.raritiesOrdered().get(1);
        for (boolean stat : List.of(false, true)) {
            var inputs = new ArrayList<SkinInstance>();
            for (int i = 0; i < 10; i++) {
                var source = i < 7 ? first : second;
                inputs.add(instance(owner, source.skins(inputTier).getFirst(), source.id(), .2, i, stat, SkinInstance.Origin.CASE));
            }
            TradeUpRules.validate(catalog, inputs, false);
            var chances = TradeUpRules.chances(catalog, inputs, target);
            require(chances.size() > 1 && Math.abs(chances.stream().mapToDouble(TradeUpRules.Chance::probability).sum() - 1) < 1e-12,
                    "random reward preview missing outcomes or not totaling 100%");
            var firstPool = first.skins(target).stream().filter(s -> !stat || s.statTrakEligible()).toList();
            var secondPool = second.skins(target).stream().filter(s -> !stat || s.statTrakEligible()).toList();
            var counts = new HashMap<SkinDefinition, Integer>(); var random = new Random(124);
            for (int i = 0; i < 6000; i++) {
                var outcome = TradeUpRules.roll(catalog, inputs, target, random);
                var source = catalog.caseDefinition(outcome.sourceCase());
                require(source.skins(target).contains(outcome.skin()) && (!stat || outcome.skin().statTrakEligible()), "random reward outside its legal source/tier/StatTrak pool");
                counts.merge(outcome.skin(), 1, Integer::sum);
            }
            for (var chance : chances) {
                double expected = (firstPool.contains(chance.skin()) ? .7 / firstPool.size() : 0)
                        + (secondPool.contains(chance.skin()) ? .3 / secondPool.size() : 0);
                require(Math.abs(chance.probability() - expected) < 1e-12, "preview does not weight cases by input count");
                require(counts.containsKey(chance.skin()) && Math.abs(counts.get(chance.skin()) / 6000.0 - expected) < .03,
                        "repeated random draws omit an eligible reward or disagree with preview odds");
            }
        }
        require(TradeUpRules.chances(catalog, List.of(), target).isEmpty(), "empty selection has reward odds");
        System.out.println("PASS: 12,000 independent trade-up draws cover every eligible normal/StatTrak outcome; exact mixed-case odds total 100% and match observed frequencies.");
    }
    private static SkinInstance instance(UUID owner, SkinDefinition def, String source, double fl, long time, boolean stat, SkinInstance.Origin origin) {
        return new SkinInstance(UUID.randomUUID(), owner, def.id(), fl, 0, time, stat, 0, PatternInfo.NONE, source, origin, time, false, SkinInstance.Status.OWNED);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
