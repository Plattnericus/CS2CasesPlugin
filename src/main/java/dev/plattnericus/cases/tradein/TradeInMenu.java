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
    public TradeInMenu(CasesContext ctx, Player p) { super(ctx, p); }
    @Override protected int rows() { return 6; }
    @Override protected Component title() { return ctx.messages(viewer).get("tradein.title"); }
    @Override protected boolean canClick(int slot) { return !ctx.tradeIns().busy(viewer); }
    @Override protected void build() {
        var profile = ctx.profiles().get(viewer); if (profile == null) return;
        var selected = ctx.tradeIns().selected(viewer);
        var skins = profile.owned().stream().filter(s -> {
            var def = ctx.catalog().skin(s.skinId());
            return def != null && !def.rarity().rareSpecial() && s.origin() != SkinInstance.Origin.TEST
                    && (!ctx.commerce().locked(s.id()) || selected.contains(s.id()));
        }).sorted(java.util.Comparator.comparing(SkinInstance::createdAt).reversed()).toList();
        int pages = GuiItems.pages(skins.size(), GuiItems.CONTENT.length); page = Math.min(page, pages - 1);
        for (int i = 0; i < GuiItems.CONTENT.length && page * GuiItems.CONTENT.length + i < skins.size(); i++) {
            var skin = skins.get(page * GuiItems.CONTENT.length + i); var def = ctx.catalog().skin(skin.skinId());
            boolean chosen = selected.contains(skin.id()); var lore = new java.util.ArrayList<>(ctx.formatter(viewer).lore(def, skin, true));
            lore.add(ctx.messages(viewer).item(chosen ? "trade.selected" : "trade.add"));
            var name = ctx.formatter(viewer).fullName(def, skin);
            if (chosen) name = Component.text("✓ ", net.kyori.adventure.text.format.NamedTextColor.GREEN).append(name);
            set(GuiItems.CONTENT[i], SkinIcons.tradeIcon(def, name, lore, ctx.settings(), chosen), c -> { ctx.tradeIns().toggle(viewer, skin.id()); render(); });
        }
        set(4, GuiItems.icon(ctx.messages(viewer), Material.WRITABLE_BOOK, "tradein.rules"));
        set(45, GuiItems.back(ctx.messages(viewer)), c -> { ctx.tradeIns().cancel(viewer); new dev.plattnericus.cases.gui.menu.SkinInventoryMenu(ctx, viewer).open(); });
        set(48, GuiItems.previous(ctx.messages(viewer), page, pages), c -> { if (page > 0) { page--; render(); } });
        set(50, GuiItems.next(ctx.messages(viewer), page, pages), c -> { if (page + 1 < pages) { page++; render(); } });
        var subject = GuiItems.icon(ctx.messages(viewer), Material.GOLD_INGOT, "tradein.confirm", Text.unparsed("count", selected.size()));
        set(49, subject, c -> {
            if (selected.isEmpty()) return;
            confirming = true;
            new ContractConfirmation(ctx, viewer, subject, this).open();
        });
        set(53, GuiItems.close(ctx.messages(viewer)), c -> viewer.closeInventory());
    }
    @Override protected void onClose() { if (!confirming) ctx.tradeIns().cancel(viewer); }
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
