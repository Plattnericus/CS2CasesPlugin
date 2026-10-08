package dev.plattnericus.cases.commerce;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.items.SkinIcons;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Full 36-item collection browser for either direct trades or marketplace selling. */
public final class SkinPickerMenu extends Menu {
    private final TradeSession trade;
    private final CollectionSelection selection = new CollectionSelection();
    private boolean navigating;
    public SkinPickerMenu(CasesContext ctx, Player player) { this(ctx, player, null); }
    public SkinPickerMenu(CasesContext ctx, Player player, TradeSession trade) { super(ctx, player); this.trade = trade; }
    public boolean belongsTo(TradeSession session) { return trade == session; }
    @Override protected int rows() { return 6; }
    @Override protected Component title() { return ctx.messages(viewer).get(trade == null ? "market.choose-title" : "trade.choose-title"); }
    @Override protected boolean canClick(int slot) {
        if (trade == null) return ctx.commerce().available();
        if (ctx.commerce().trade(viewer.getUniqueId()) != trade || trade.committing()) return false;
        ctx.commerce().touch(trade); return true;
    }
    @Override protected void build() {
        var profile = ctx.profiles().get(viewer); if (profile == null) return;
        var selected = trade == null ? java.util.List.<java.util.UUID>of() : trade.items(viewer.getUniqueId());
        var skins = selection.filter(ctx, viewer, profile.owned().stream().filter(s -> s.origin() != SkinInstance.Origin.TEST
                && (!ctx.commerce().locked(s.id()) || selected.contains(s.id()))).toList());
        int pages = GuiItems.pages(skins.size(), GuiItems.CONTENT.length); selection.page = Math.min(selection.page, pages - 1);
        if (skins.isEmpty()) set(31, GuiItems.icon(ctx.messages(viewer), Material.LIGHT_GRAY_DYE, "market.no-skins"));
        for (int i = 0; i < GuiItems.CONTENT.length && selection.page * GuiItems.CONTENT.length + i < skins.size(); i++) {
            var skin = skins.get(selection.page * GuiItems.CONTENT.length + i); var def = ctx.catalog().skin(skin.skinId());
            boolean offered = selected.contains(skin.id());
            var lore = new java.util.ArrayList<>(ctx.formatter(viewer).lore(def, skin, true)); lore.add(ctx.messages(viewer).item(trade == null ? "market.select-skin" : offered ? "trade.selected" : "trade.add"));
            Component name = ctx.formatter(viewer).fullName(def, skin);
            if (offered) name = Component.text("✓ ", net.kyori.adventure.text.format.NamedTextColor.GREEN).append(name);
            set(GuiItems.CONTENT[i], SkinIcons.tradeIcon(def, name, lore, ctx.settings(), offered), c -> {
                if (trade != null) { ctx.commerce().toggle(viewer, skin.id()); render(); }
                else new SellMenu(ctx, viewer, skin, Math.max(100, ctx.commerce().minPrice())).open();
            });
        }
        set(1, GuiItems.icon(ctx.messages(viewer), Material.SPYGLASS, "browser.category", Text.unparsed("value", selection.category(ctx, viewer))), c -> { selection.category = (selection.category + 1) % (dev.plattnericus.cases.catalog.WeaponCategory.values().length + 1); selection.page = 0; render(); });
        set(3, GuiItems.icon(ctx.messages(viewer), Material.AMETHYST_SHARD, "browser.rarity", Text.unparsed("value", selection.rarity(ctx, viewer))), c -> { selection.rarity = (selection.rarity + 1) % (ctx.catalog().raritiesOrdered().size() + 1); selection.page = 0; render(); });
        set(5, GuiItems.icon(ctx.messages(viewer), Material.HOPPER, "browser.sort", Text.unparsed("value", ctx.messages(viewer).raw("browser.sorts." + selection.sort))), c -> { selection.sort = (selection.sort + 1) % 4; render(); });
        set(7, GuiItems.icon(ctx.messages(viewer), Material.COMPASS, "browser.search", Text.unparsed("query", selection.query)), c -> {
            if (c.isRightClick()) { selection.query = ""; render(); return; }
            navigating = true;
            ctx.commerce().input().ask(viewer, "input.search", text -> {
                selection.query = text.toLowerCase(java.util.Locale.ROOT); selection.page = 0; reopen();
            }, this::reopen);
        });
        set(45, GuiItems.back(ctx.messages(viewer)), c -> {
            navigating = true;
            if (trade == null) new MarketMenu(ctx, viewer).open(); else new TradeMenu(ctx, viewer, trade).open();
        });
        set(48, GuiItems.previous(ctx.messages(viewer), selection.page, pages), c -> { if (selection.page > 0) { selection.page--; render(); } });
        set(49, GuiItems.icon(ctx.messages(viewer), Material.PAPER, "trade.page", Text.unparsed("page", selection.page + 1), Text.unparsed("pages", pages)));
        set(50, GuiItems.next(ctx.messages(viewer), selection.page, pages), c -> { if (selection.page + 1 < pages) { selection.page++; render(); } });
        if (trade != null) set(53, GuiItems.icon(ctx.messages(viewer), Material.BARRIER, "trade.cancel"), c -> ctx.commerce().cancel(viewer));
    }
    private void reopen() { if (trade == null || ctx.commerce().trade(viewer.getUniqueId()) == trade && !trade.committing()) { open(); navigating = false; } }
    @Override protected void onClose() { if (trade != null && !navigating) ctx.commerce().cancel(trade, "trade.cancelled"); }
}
