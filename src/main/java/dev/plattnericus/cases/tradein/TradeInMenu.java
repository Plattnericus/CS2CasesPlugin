package dev.plattnericus.cases.tradein;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.items.SkinIcons;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Contract selection with a separate, explicit final confirmation. */
public final class TradeInMenu extends Menu {
    private int page;
    private boolean confirming;
    private TradeInSelection.Sort sort = TradeInSelection.Sort.NEWEST;
    private String weaponFilter, rarityFilter;
    private int statTrakFilter; // 0 = all, 1 = normal, 2 = StatTrak
    public TradeInMenu(CasesContext ctx, Player p) { super(ctx, p); }
    @Override protected int rows() { return 6; }
    @Override protected Component title() { return GuiItems.brandedTitle(ctx, ctx.messages(viewer).get("tradein.title"), '\uE008'); }
    @Override protected boolean canClick(int slot) { return !ctx.tradeIns().busy(viewer); }
    @Override protected void build() {
        var profile = ctx.profiles().get(viewer); if (profile == null) return;
        var selected = ctx.tradeIns().selected(viewer);
        var inputs = selected.stream().map(profile::get).filter(java.util.Objects::nonNull).toList();
        var first = inputs.isEmpty() ? null : inputs.getFirst();
        var target = first == null ? null : TradeInRules.target(ctx.catalog(), ctx.catalog().skin(first.skinId()));
        int required = TradeInRules.required(target);
        var candidates = profile.owned().stream().filter(s ->
                TradeInRules.eligible(ctx.catalog(), s, ctx.tradeIns().allowAdmin())
                && (!ctx.commerce().locked(s.id()) || selected.contains(s.id()))
                && (first == null || TradeInRules.compatible(ctx.catalog(), first, s, ctx.tradeIns().allowAdmin()))).toList();
        var skins = TradeInSelection.sorted(ctx.catalog(), TradeInSelection.filter(ctx.catalog(), candidates,
                weaponFilter, rarityFilter, statTrakFilter), sort);
        int pages = GuiItems.pages(skins.size(), GuiItems.CONTENT.length); page = Math.min(page, pages - 1);
        if (skins.isEmpty()) set(31, GuiItems.icon(ctx.messages(viewer), Material.GRAY_DYE, "tradein.empty"));
        for (int i = 0; i < GuiItems.CONTENT.length && page * GuiItems.CONTENT.length + i < skins.size(); i++) {
            var skin = skins.get(page * GuiItems.CONTENT.length + i); var def = ctx.catalog().skin(skin.skinId());
            boolean chosen = selected.contains(skin.id()); var lore = new java.util.ArrayList<>(ctx.formatter(viewer).lore(def, skin, true));
            lore.add(ctx.messages(viewer).item(chosen ? "trade.selected" : "trade.add"));
            var name = ctx.formatter(viewer).fullName(def, skin);
            if (chosen) name = Component.text("✓ ", net.kyori.adventure.text.format.NamedTextColor.GREEN).append(name);
            set(GuiItems.CONTENT[i], SkinIcons.tradeIcon(def, name, lore, ctx.settings(), chosen), c -> { ctx.tradeIns().toggle(viewer, skin.id()); playClick(); render(); });
        }
        set(4, GuiItems.icon(ctx.messages(viewer), Material.WRITABLE_BOOK, "tradein.rules"));
        String all = ctx.messages(viewer).raw("tradein.all");
        set(1, GuiItems.icon(ctx.messages(viewer), Material.CROSSBOW, "tradein.weapon", Text.unparsed("value",
                weaponFilter == null ? all : ctx.catalog().weapon(weaponFilter).name())), c -> {
            confirming = true; new WeaponChooser(ctx, viewer, this, candidates).open();
        });
        set(2, GuiItems.icon(ctx.messages(viewer), Material.PRISMARINE_CRYSTALS, "tradein.rarity", Text.unparsed("value",
                rarityFilter == null ? all : ctx.catalog().rarity(rarityFilter).name())), c -> {
            var choices = new java.util.ArrayList<String>(); choices.add(null);
            ctx.catalog().raritiesOrdered().stream().filter(r -> !r.rareSpecial()).forEach(r -> choices.add(r.id()));
            rarityFilter = choices.get(Math.floorMod(choices.indexOf(rarityFilter) + (c.isRightClick() ? -1 : 1), choices.size()));
            page = 0; playClick(); render();
        });
        set(3, GuiItems.icon(ctx.messages(viewer), Material.REDSTONE, "tradein.stattrak", Text.unparsed("value",
                statTrakFilter == 0 ? all : statTrakFilter == 1 ? ctx.messages(viewer).raw("tradein.normal") : "StatTrak")), c -> {
            statTrakFilter = Math.floorMod(statTrakFilter + (c.isRightClick() ? -1 : 1), 3); page = 0; playClick(); render();
        });
        set(5, GuiItems.icon(ctx.messages(viewer), Material.COMPASS, "tradein.reset-filters"), c -> {
            weaponFilter = null; rarityFilter = null; statTrakFilter = 0; page = 0; playClick(); render();
        });
        set(0, GuiItems.icon(ctx.messages(viewer), Material.HOPPER, "tradein.sort", Text.unparsed("sort",
                ctx.messages(viewer).raw("tradein.sorts." + sort.name().toLowerCase(java.util.Locale.ROOT)))), c -> {
            var sorts = TradeInSelection.Sort.values();
            sort = sorts[Math.floorMod(sort.ordinal() + (c.isRightClick() ? -1 : 1), sorts.length)]; page = 0; playClick(); render();
        });
        set(7, GuiItems.icon(ctx.messages(viewer), Material.BUNDLE, "tradein.fill"), c -> {
            var fill = TradeInSelection.fill(ctx.catalog(), skins, inputs, ctx.tradeIns().allowAdmin());
            fill.forEach(s -> ctx.tradeIns().toggle(viewer, s.id()));
            if (fill.isEmpty()) ctx.messages(viewer).send(viewer, "tradein.no-fill");
            playClick(); render();
        });
        set(8, GuiItems.icon(ctx.messages(viewer), Material.MILK_BUCKET, "tradein.clear"), c -> { ctx.tradeIns().cancel(viewer); page = 0; playClick(); render(); });
        set(45, GuiItems.back(ctx.messages(viewer)), c -> { ctx.tradeIns().cancel(viewer); new dev.plattnericus.cases.gui.menu.SkinInventoryMenu(ctx, viewer).open(); });
        set(48, GuiItems.previous(ctx.messages(viewer), page, pages), c -> { if (page > 0) { page--; render(); } });
        set(50, GuiItems.next(ctx.messages(viewer), page, pages), c -> { if (page + 1 < pages) { page++; render(); } });
        boolean ready = target != null && selected.size() == required;
        var subject = GuiItems.icon(ctx.messages(viewer), ready ? Material.LIME_CONCRETE : Material.GRAY_DYE, "tradein.review",
                Text.unparsed("count", selected.size()), Text.unparsed("required", required),
                Text.unparsed("target", target == null ? ctx.messages(viewer).raw("tradein.choose-tier") : target.name()),
                Text.unparsed("float", Text.formatFloat(inputs.stream().mapToDouble(SkinInstance::floatValue).average().orElse(0), 6)));
        set(49, subject, c -> {
            if (!ready) { ctx.messages(viewer).send(viewer, "tradein.count"); playError(); return; }
            confirming = true;
            new ContractConfirmation(ctx, viewer, subject, this).open();
        });
        set(53, GuiItems.close(ctx.messages(viewer)), c -> viewer.closeInventory());
    }
    @Override protected void onClose() { if (!confirming) ctx.tradeIns().cancel(viewer); }

    /** A direct weapon choice avoids cycling through dozens of models to find an AK or AWP. */
    private static final class WeaponChooser extends Menu {
        private final TradeInMenu source;
        private final java.util.List<SkinInstance> candidates;
        private int page;
        private boolean navigating;
        WeaponChooser(CasesContext ctx, Player p, TradeInMenu source, java.util.List<SkinInstance> candidates) {
            super(ctx, p); this.source = source; this.candidates = candidates;
        }
        @Override protected int rows() { return 6; }
        @Override protected Component title() { return ctx.messages(viewer).get("tradein.weapon-title"); }
        @Override protected void build() {
            var counts = candidates.stream().collect(java.util.stream.Collectors.groupingBy(
                    s -> ctx.catalog().skin(s.skinId()).weapon().id(), java.util.stream.Collectors.counting()));
            var weapons = ctx.catalog().weapons().stream().filter(w -> counts.containsKey(w.id()))
                    .sorted(java.util.Comparator.comparing(dev.plattnericus.cases.catalog.WeaponType::name)).toList();
            int pages = GuiItems.pages(weapons.size(), GuiItems.CONTENT.length); page = Math.min(page, pages - 1);
            for (int i = 0; i < GuiItems.CONTENT.length && page * GuiItems.CONTENT.length + i < weapons.size(); i++) {
                var weapon = weapons.get(page * GuiItems.CONTENT.length + i);
                var def = candidates.stream().map(s -> ctx.catalog().skin(s.skinId()))
                        .filter(s -> s.weapon().id().equals(weapon.id())).findFirst().orElseThrow();
                set(GuiItems.CONTENT[i], SkinIcons.icon(def, Component.text(weapon.name()),
                        ctx.messages(viewer).itemList("tradein.weapon-choice.lore", Text.unparsed("count", counts.get(weapon.id()))),
                        ctx.settings(), weapon.id().equals(source.weaponFilter)), c -> choose(weapon.id()));
            }
            set(4, GuiItems.icon(ctx.messages(viewer), Material.CHEST, "tradein.all-weapons"), c -> choose(null));
            set(45, GuiItems.back(ctx.messages(viewer)), c -> choose(source.weaponFilter));
            set(48, GuiItems.previous(ctx.messages(viewer), page, pages), c -> { if (page > 0) { page--; render(); } });
            set(50, GuiItems.next(ctx.messages(viewer), page, pages), c -> { if (page + 1 < pages) { page++; render(); } });
            set(53, GuiItems.close(ctx.messages(viewer)), c -> viewer.closeInventory());
        }
        private void choose(String weapon) {
            navigating = true; source.weaponFilter = weapon; source.page = 0; source.confirming = false; source.open();
        }
        @Override protected void onClose() { if (!navigating) ctx.tradeIns().cancel(viewer); }
    }
    private static final class ContractConfirmation extends Menu {
        private final org.bukkit.inventory.ItemStack subject;
        private final TradeInMenu source;
        private boolean navigating;
        ContractConfirmation(CasesContext ctx, Player p, org.bukkit.inventory.ItemStack subject, TradeInMenu source) { super(ctx, p); this.subject = subject; this.source = source; }
        @Override protected int rows() { return 3; }
        @Override protected Component title() { return ctx.messages(viewer).get("gui.confirm.title"); }
        @Override protected void build() {
            set(13, subject);
            set(11, GuiItems.icon(ctx.messages(viewer), Material.LIME_CONCRETE, "gui.confirm.yes"), c -> {
                if (ctx.tradeIns().busy(viewer)) return;
                ctx.tradeIns().confirm(viewer);
                if (ctx.tradeIns().busy(viewer)) { navigating = true; viewer.closeInventory(); }
            });
            set(15, GuiItems.back(ctx.messages(viewer)), c -> { navigating = true; source.confirming = false; source.open(); });
        }
        @Override protected void onClose() { if (!navigating) ctx.tradeIns().cancel(viewer); }
    }
}
