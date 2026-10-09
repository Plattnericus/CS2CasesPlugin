package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.catalog.*;
import dev.plattnericus.cases.config.Messages;
import dev.plattnericus.cases.items.SkinFormatter;
import dev.plattnericus.cases.render.TextureStore;
import dev.plattnericus.cases.skin.*;
import dev.plattnericus.cases.tradein.*;
import dev.plattnericus.cases.util.Text;
import java.io.File;
import java.util.*;

/** Contract browsing uses the real catalog, filters, selection planner and lore formatter. */
public final class TradeInChecks {
    private TradeInChecks() { }
    public static void run(File root) {
        var catalog = new CatalogLoader(new File(root, "catalog"), new TextureStore(new File(root, "textures"))).load(1).catalog();
        var source = catalog.caseDefinition("kilowatt_case");
        var tier = catalog.raritiesOrdered().getFirst();
        var a = source.skins(tier).getFirst();
        var b = source.skins(tier).stream().filter(s -> !s.weapon().id().equals(a.weapon().id())).findFirst().orElseThrow();
        UUID owner = UUID.randomUUID(); var skins = new ArrayList<SkinInstance>();
        for (int i = 0; i < 48; i++) skins.add(instance(owner, i % 2 == 0 ? a : b, source.id(), i / 50.0, i, false, SkinInstance.Origin.CASE));
        skins.getFirst().setFavorite(true);
        for (var sort : TradeInSelection.Sort.values()) {
            var ordered = TradeInSelection.sorted(catalog, skins, sort);
            require(new HashSet<>(ordered).equals(new HashSet<>(skins)), "sort lost skin identities");
            require(ordered.equals(TradeInSelection.sorted(catalog, skins.reversed(), sort)), "unstable sorting " + sort);
        }
        var ascending = TradeInSelection.sorted(catalog, skins, TradeInSelection.Sort.FLOAT_ASC);
        var descending = TradeInSelection.sorted(catalog, skins, TradeInSelection.Sort.FLOAT_DESC);
        require(ascending.getFirst().floatValue() == 0 && descending.getFirst().floatValue() == .94, "float order wrong");
        require(TradeInSelection.sorted(catalog, skins, TradeInSelection.Sort.NEWEST).getFirst() == skins.getLast(), "newest order wrong");
        var onlyA = TradeInSelection.filter(catalog, descending, a.weapon().id(), tier.id(), 1);
        require(onlyA.size() == 24 && onlyA.stream().allMatch(s -> s.skinId().equals(a.id())), "weapon/rarity filter leaked another weapon");
        require(TradeInSelection.filter(catalog, skins, null, null, 2).isEmpty(), "StatTrak filter leaked normal skins");
        var filled = TradeInSelection.fill(catalog, onlyA, List.of(), true);
        require(filled.size() == 10 && filled.getFirst() == onlyA.getFirst() && filled.stream().noneMatch(SkinInstance::favorite), "fill ignored filter/order/favorites");
        require(!TradeInRules.validate(catalog, filled, true).rareSpecial(), "filtered weapon contract failed");
        var chosen = filled.subList(0, 3);
        var remaining = TradeInSelection.fill(catalog, onlyA, chosen, true);
        require(remaining.size() == 7 && remaining.stream().noneMatch(chosen::contains), "fill duplicated selections or crossed pages incorrectly");
        require(TradeInSelection.fill(catalog, onlyA.subList(0, 4), List.of(), true).isEmpty(), "incomplete automatic contract selected");
        var stat = instance(owner, a, source.id(), .3, 90, true, SkinInstance.Origin.CASE);
        var higher = instance(owner, source.skins(catalog.raritiesOrdered().get(1)).getFirst(), source.id(), .3, 91, false, SkinInstance.Origin.CASE);
        require(!TradeInRules.compatible(catalog, skins.getFirst(), stat, true)
                && !TradeInRules.compatible(catalog, skins.getFirst(), higher, true), "incompatible input selectable");
        require(!TradeInRules.eligible(catalog, instance(owner, a, null, .3, 92, false, SkinInstance.Origin.CASE), true), "unknown real case provenance eligible");
        var directAdmin = instance(owner, a, "admin", .3, 92, false, SkinInstance.Origin.ADMIN);
        require(TradeInRules.eligible(catalog, directAdmin, true) && !TradeInRules.eligible(catalog, directAdmin, false), "configured direct admin weapon cannot be selected");
        require(TradeInRules.sources(catalog, directAdmin, TradeInRules.target(catalog,a)).stream()
                .allMatch(c -> c.pool().values().stream().flatMap(List::stream).anyMatch(def -> def.id().equals(a.id()))), "admin case inference used an unrelated skin");
        var adminTrade = instance(owner, a, source.id(), .3, 93, false, SkinInstance.Origin.ADMIN_TRADE_IN);
        require(adminTrade.origin().admin() && adminTrade.origin().tradeIn()
                && !TradeInRules.eligible(catalog, adminTrade, false) && TradeInRules.eligible(catalog, adminTrade, true), "admin lineage lost after trade-in");
        require(InstanceCodec.decode(InstanceCodec.encode(adminTrade)).origin() == adminTrade.origin(), "journal dropped contract provenance");
        var messages = new Messages(new File(root, "messages_en.yml"), null, null);
        var formatter = new SkinFormatter(messages, catalog, 6, 8, "yyyy-MM-dd");
        for (SkinDefinition output : List.of(a, source.skins(catalog.raritiesOrdered().getLast()).getFirst())) {
            for (var origin : List.of(SkinInstance.Origin.TRADE_IN, SkinInstance.Origin.ADMIN_TRADE_IN)) {
                var reward = instance(owner, output, source.id(), .3, 94, false, origin);
                for (boolean precise : List.of(false, true)) require(formatter.lore(output, reward, precise).stream()
                        .map(Text::plain).anyMatch(line -> line.equals("Source: TRADE IN")), "weapon/knife inventory source incorrect");
                require(reward.sourceCase().equals(source.id()), "display source destroyed the case used for the next contract");
            }
        }
        System.out.println("PASS: five stable trade-in sorts, exact weapon/rarity/StatTrak filters, 48-item cross-page filling, favorite protection, 10/5 input validation and weapon/knife Source: TRADE IN with retained admin lineage.");
    }
    private static SkinInstance instance(UUID owner, SkinDefinition def, String source, double fl, long time, boolean stat, SkinInstance.Origin origin) {
        return new SkinInstance(UUID.randomUUID(), owner, def.id(), fl, 0, time, stat, 0, PatternInfo.NONE, source, origin, time, false, SkinInstance.Status.OWNED);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
