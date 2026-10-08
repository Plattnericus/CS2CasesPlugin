package dev.plattnericus.cases.gui.menu;

import dev.plattnericus.cases.catalog.CaseDefinition;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.items.CaseItems;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** /cases: every case with the player's case and key counts. */
public final class CasesMenu extends Menu {

    private enum Sort { VALUE, PRICE_ASC, PRICE_DESC, COMMUNITY, OWNED, NAME }
    private Sort sort = Sort.VALUE;
    private int page;
    private String search = "", knife = "all";
    private long budget = Long.MAX_VALUE;
    private boolean ownedOnly;

    public CasesMenu(CasesContext ctx, Player viewer) {
        super(ctx, viewer);
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected Component title() {
        return ctx.messages(viewer).get("gui.cases.title");
    }

    @Override
    protected void build() {
        List<CaseDefinition> cases = new ArrayList<>();
        for (CaseDefinition def : ctx.catalog().cases()) {
            if (def.enabled()) {
                cases.add(def);
            }
        }
        CaseItems items = ctx.caseItems();
        java.util.Map<String, Integer> ownedCounts = new java.util.HashMap<>();
        for (CaseDefinition d : cases) {
            ownedCounts.put(d.id(), items.count(viewer, CaseItems.TYPE_CASE, d.id()));
        }
        var guide = ctx.caseGuide();
        var offers = ctx.shop().offers();
        java.util.Map<String, dev.plattnericus.cases.catalog.CaseGuide.Metrics> metrics = new java.util.HashMap<>();
        for (CaseDefinition def : cases) metrics.put(def.id(), guide.measure(def, ctx.catalog(), offers));
        cases.removeIf(d -> {
            var m = metrics.get(d.id());
            return (!search.isBlank() && !(d.name() + " " + d.id()).toLowerCase(java.util.Locale.ROOT).contains(search))
                    || (ownedOnly && ownedCounts.get(d.id()) == 0)
                    || (budget != Long.MAX_VALUE && (m.cost() == null || m.cost() > budget))
                    || (knife.equals("favorites") && m.favoriteChance() <= 0)
                    || (!knife.equals("all") && !knife.equals("favorites") && !m.knives().containsKey(knife));
        });
        Comparator<CaseDefinition> comparator = switch (sort) {
            case VALUE -> Comparator.comparingDouble((CaseDefinition d) -> metrics.get(d.id()).value()).reversed();
            case COMMUNITY -> Comparator.comparingDouble((CaseDefinition d) -> metrics.get(d.id()).communityScore()).reversed();
            case PRICE_ASC -> Comparator.comparingLong(d -> metrics.get(d.id()).cost() == null ? Long.MAX_VALUE : metrics.get(d.id()).cost());
            case PRICE_DESC -> Comparator.comparingLong((CaseDefinition d) -> metrics.get(d.id()).cost() == null ? -1 : metrics.get(d.id()).cost()).reversed();
            case OWNED -> Comparator.comparingInt((CaseDefinition d) -> ownedCounts.get(d.id())).reversed();
            case NAME -> Comparator.comparing(CaseDefinition::name);
        };
        cases.sort(comparator.thenComparing(CaseDefinition::name).thenComparing(CaseDefinition::id));
        int perPage = GuiItems.CONTENT.length;
        int pages = GuiItems.pages(cases.size(), perPage);
        page = Math.min(page, pages - 1);
        set(4, GuiItems.icon(ctx.messages(viewer), Material.BOOK, "case-guide.info", Text.unparsed("date", guide.snapshot()),
                Text.unparsed("tiers", ctx.catalog().raritiesOrdered().stream().map(r -> String.format(java.util.Locale.ROOT, "%g", guide.tierPoints(r.id()))).collect(java.util.stream.Collectors.joining(" / "))),
                Text.unparsed("threshold", guide.favoriteThreshold())));
        set(0, GuiItems.icon(ctx.messages(viewer), Material.HOPPER, "case-guide.sort", Text.component("value", ctx.messages(viewer).item("case-guide.sorts." + sort.name().toLowerCase(java.util.Locale.ROOT)))), c -> {
            sort = Sort.values()[(sort.ordinal() + (c.isRightClick() ? Sort.values().length - 1 : 1)) % Sort.values().length]; page = 0; playClick(); render();
        });
        String knifeName = knife.equals("all") || knife.equals("favorites") ? ctx.messages(viewer).raw("case-guide.knives." + knife) : ctx.catalog().weapon(knife) == null ? knife : ctx.catalog().weapon(knife).name();
        set(2, GuiItems.icon(ctx.messages(viewer), Material.IRON_SWORD, "case-guide.knife", Text.unparsed("value", knifeName)), c -> {
            List<String> choices = new ArrayList<>(List.of("all", "favorites"));
            ctx.catalog().weapons().stream().filter(dev.plattnericus.cases.catalog.WeaponType::isKnife).sorted(Comparator.comparing(dev.plattnericus.cases.catalog.WeaponType::name)).forEach(w -> choices.add(w.id()));
            int index = Math.max(0, choices.indexOf(knife)); knife = choices.get((index + (c.isRightClick() ? choices.size() - 1 : 1)) % choices.size()); page = 0; playClick(); render();
        });
        String currency = ctx.shop().currency().name();
        set(6, GuiItems.icon(ctx.messages(viewer), Material.DIAMOND, "case-guide.budget", Text.unparsed("value", budget == Long.MAX_VALUE ? ctx.messages(viewer).raw("case-guide.unlimited") : budget), Text.unparsed("currency", currency)), c -> {
            if (c.isRightClick()) { budget = Long.MAX_VALUE; page = 0; render(); return; }
            ctx.commerce().input().ask(viewer, "case-guide.budget-prompt", value -> {
                try { long n = Long.parseLong(value); if (n < 0 || n > 4294967294L) throw new NumberFormatException(); budget = n; page = 0; }
                catch (NumberFormatException invalid) { ctx.messages(viewer).send(viewer, "case-guide.bad-budget"); }
                open();
            }, this::open);
        });
        set(8, GuiItems.icon(ctx.messages(viewer), Material.NAME_TAG, "case-guide.search", Text.unparsed("value", search.isBlank() ? ctx.messages(viewer).raw("case-guide.unlimited") : search)), c -> {
            if (c.isRightClick()) { search = ""; page = 0; render(); return; }
            ctx.commerce().input().ask(viewer, "case-guide.search-prompt", value -> { search = value.toLowerCase(java.util.Locale.ROOT); page = 0; open(); }, this::open);
        });
        set(46, GuiItems.icon(ctx.messages(viewer), Material.CHEST, "case-guide.owned", Text.component("value", ctx.messages(viewer).item(ownedOnly ? "case-guide.knives.owned" : "case-guide.knives.all"))), c -> { ownedOnly = !ownedOnly; page = 0; playClick(); render(); });
        set(52, GuiItems.icon(ctx.messages(viewer), Material.MILK_BUCKET, "case-guide.reset"), c -> { search = ""; knife = "all"; ownedOnly = false; budget = Long.MAX_VALUE; sort = Sort.VALUE; page = 0; playClick(); render(); });
        if (cases.isEmpty()) set(22, GuiItems.icon(ctx.messages(viewer), Material.PAPER, "case-guide.empty"));
        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= cases.size()) {
                break;
            }
            CaseDefinition def = cases.get(idx);
            int owned = ownedCounts.get(def.id());
            int keys = items.count(viewer, CaseItems.TYPE_KEY, def.keyId());
            ItemStack icon = items.caseIcon(def, ctx.messages(viewer));
            icon.editMeta(meta -> {
                List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
                lore.addAll(ctx.messages(viewer).itemList("gui.cases.entry",
                        Text.unparsed("owned", owned), Text.unparsed("keys", keys)));
                var m = metrics.get(def.id());
                lore.addAll(ctx.messages(viewer).itemList("case-guide.metrics", Text.unparsed("cost", m.cost() == null ? ctx.messages(viewer).raw("case-guide.unavailable") : m.cost()),
                        Text.unparsed("currency", currency), Text.unparsed("points", number(m.expectedPoints())),
                        Text.unparsed("value", m.value() < 0 ? "—" : number(m.value())), Text.unparsed("knives", number(m.knifeChance() * 100)),
                        Text.unparsed("favorites", number(m.favoriteChance() * 100)), Text.unparsed("community", number(m.communityScore()))));
                if (!knife.equals("all") && !knife.equals("favorites")) lore.add(ctx.messages(viewer).item("case-guide.target", Text.unparsed("knife", knifeName), Text.unparsed("chance", number(m.knives().getOrDefault(knife, 0d) * 100))));
                if (owned > 0 && keys > 0) {
                    lore.add(ctx.messages(viewer).item("gui.cases.can-open"));
                }
                meta.lore(lore);
                if (owned > 0) {
                    meta.setEnchantmentGlintOverride(true);
                }
            });
            set(GuiItems.CONTENT[i], icon, click -> {
                playClick();
                if (click.isRightClick() && owned > 0 && keys > 0) {
                    ctx.openings().open(viewer, def, false, false);
                    render();
                } else {
                    new CasePreviewMenu(ctx, viewer, def, this::reopen).open();
                }
            });
        }
        set(GuiItems.SLOT_BACK, GuiItems.icon(ctx.messages(viewer), Material.ENDER_CHEST, "gui.cases.to-skins"), c -> {
            playClick();
            ctx.gallery().open(viewer, null);
        });
        set(GuiItems.SLOT_PREV, GuiItems.previous(ctx.messages(viewer), page, pages), c -> {
            if (page > 0) {
                page--;
                playClick();
                render();
            }
        });
        set(53, GuiItems.icon(ctx.messages(viewer), Material.CLOCK, "opening.active-button"), c -> new dev.plattnericus.cases.opening.ActiveOpeningsMenu(ctx, viewer).open());
        set(GuiItems.SLOT_CENTER, GuiItems.close(ctx.messages(viewer)), c -> viewer.closeInventory());
        set(GuiItems.SLOT_NEXT, GuiItems.next(ctx.messages(viewer), page, pages), c -> {
            if (page + 1 < pages) {
                page++;
                playClick();
                render();
            }
        });
    }

    private void reopen() {
        open();
    }

    private static String number(double value) { return String.format(java.util.Locale.ROOT, "%.3f", value); }
}
