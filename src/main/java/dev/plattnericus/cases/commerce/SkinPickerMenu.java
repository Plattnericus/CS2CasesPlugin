package dev.plattnericus.cases.commerce;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.items.SkinIcons;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class SkinPickerMenu extends Menu {
    private int page;
    public SkinPickerMenu(CasesContext ctx, Player player) { super(ctx, player); }
    @Override protected int rows() { return 6; }
    @Override protected Component title() { return ctx.messages(viewer).get("market.choose-title"); }
    @Override protected void build() {
        var profile = ctx.profiles().get(viewer); if (profile == null) return;
        var skins = profile.owned().stream().filter(s -> ctx.catalog().skin(s.skinId()) != null
                && s.origin() != dev.plattnericus.cases.skin.SkinInstance.Origin.TEST && !ctx.commerce().locked(s.id())).toList();
        int pages = GuiItems.pages(skins.size(), GuiItems.CONTENT.length); page = Math.min(page, pages - 1);
        if (skins.isEmpty()) set(31, GuiItems.icon(ctx.messages(viewer), Material.LIGHT_GRAY_DYE, "market.no-skins"));
        for (int i = 0; i < GuiItems.CONTENT.length && page * GuiItems.CONTENT.length + i < skins.size(); i++) {
            var skin = skins.get(page * GuiItems.CONTENT.length + i); var def = ctx.catalog().skin(skin.skinId());
            var lore = new java.util.ArrayList<>(ctx.formatter(viewer).lore(def, skin, true)); lore.add(ctx.messages(viewer).item("market.select-skin"));
            set(GuiItems.CONTENT[i], SkinIcons.icon(def, ctx.formatter(viewer).fullName(def, skin), lore, ctx.settings(), false), c -> new SellMenu(ctx, viewer, skin, 100).open());
        }
        set(45, GuiItems.back(ctx.messages(viewer)), c -> new MarketMenu(ctx, viewer).open());
        set(48, GuiItems.previous(ctx.messages(viewer), page, pages), c -> { if (page > 0) { page--; render(); } });
        set(50, GuiItems.next(ctx.messages(viewer), page, pages), c -> { if (page + 1 < pages) { page++; render(); } });
    }
}
