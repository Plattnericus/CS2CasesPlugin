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

public final class SellMenu extends Menu {
    private final SkinInstance skin;
    private long price;
    private boolean pending;
    public SellMenu(CasesContext ctx, Player viewer, SkinInstance skin, long price) {
        super(ctx, viewer); this.skin = skin; this.price = Math.clamp(price, 1, ctx.commerce().maxPrice());
    }
    @Override protected int rows() { return 4; }
    @Override protected Component title() { return ctx.messages(viewer).get("market.sell-title"); }
    @Override protected void build() {
        var def = ctx.catalog().skin(skin.skinId()); if (def == null) return;
        set(4, SkinIcons.icon(def, ctx.formatter(viewer).fullName(def, skin), ctx.formatter(viewer).lore(def, skin, true), ctx.settings(), false));
        set(13, GuiItems.icon(ctx.messages(viewer), Material.GOLD_INGOT, "market.price", Text.unparsed("price", price), Text.unparsed("currency", ctx.commerce().currency())));
        long[] changes = {-100, -10, -1, 1, 10, 100}; int[] slots = {10,11,12,14,15,16};
        for (int i = 0; i < changes.length; i++) {
            long delta = changes[i];
            set(slots[i], GuiItems.icon(ctx.messages(viewer), delta < 0 ? Material.RED_DYE : Material.LIME_DYE, "market.change-price",
                    Text.unparsed("change", delta > 0 ? "+" + delta : delta)), c -> { if (!pending) { price = Math.clamp(price + delta, 1, ctx.commerce().maxPrice()); render(); } });
        }
        set(22, GuiItems.icon(ctx.messages(viewer), Material.PAPER, "market.sell-info"));
        set(29, GuiItems.icon(ctx.messages(viewer), Material.LIME_CONCRETE, pending ? "trade.saving" : "market.publish",
                Text.unparsed("price", price), Text.unparsed("currency", ctx.commerce().currency())), c -> {
            if (pending) return; pending = true; ctx.commerce().list(viewer, skin, price);
            if (viewer.getOpenInventory().getTopInventory().getHolder(false) == this) viewer.closeInventory();
        });
        set(33, GuiItems.back(ctx.messages(viewer)), c -> new SkinPickerMenu(ctx, viewer).open());
    }
}
