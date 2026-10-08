package dev.plattnericus.cases.commerce;

import dev.plattnericus.cases.catalog.WeaponCategory;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.util.Text;
import org.bukkit.entity.Player;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Persistent view state shared by sale, direct-trade and contract selection menus. */
public final class CollectionSelection {
    int page, category, rarity, sort;
    String query = "";
    public List<SkinInstance> filter(CasesContext ctx, Player p, List<SkinInstance> skins) {
        rarity = Math.clamp(rarity, 0, ctx.catalog().raritiesOrdered().size());
        category = Math.clamp(category, 0, WeaponCategory.values().length);
        Comparator<SkinInstance> order = switch (sort) {
            case 1 -> Comparator.comparing(s -> ctx.catalog().skin(s.skinId()).displayName());
            case 2 -> Comparator.comparingDouble(SkinInstance::floatValue);
            case 3 -> Comparator.comparingLong(SkinInstance::createdAt).reversed();
            default -> Comparator.<SkinInstance>comparingInt(s -> ctx.catalog().skin(s.skinId()).rarity().order()).reversed();
        };
        return skins.stream().filter(s -> {
            var def = ctx.catalog().skin(s.skinId());
            return def != null && (category == 0 || def.weapon().category() == WeaponCategory.values()[category - 1])
                    && (rarity == 0 || def.rarity().id().equals(ctx.catalog().raritiesOrdered().get(rarity - 1).id()))
                    && (query.isBlank() || (def.id() + " " + Text.plain(ctx.formatter(p).fullName(def, s))).toLowerCase(Locale.ROOT).contains(query));
        }).sorted(order.thenComparing(SkinInstance::id)).toList();
    }
    String category(CasesContext ctx, Player p) { return ctx.messages(p).raw("browser.categories." + (category == 0 ? "ALL" : WeaponCategory.values()[category - 1].name())); }
    String rarity(CasesContext ctx, Player p) {
        if (rarity == 0) return ctx.messages(p).raw("browser.all-rarities");
        var r = ctx.catalog().raritiesOrdered().get(rarity - 1); return ctx.messages(p).label("rarity." + r.id(), r.name());
    }
}
