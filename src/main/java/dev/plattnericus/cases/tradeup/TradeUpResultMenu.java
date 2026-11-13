package dev.plattnericus.cases.tradeup;

import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.items.SkinIcons;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Shows the skin received from a trade-up on a rarity-colored background. */
public final class TradeUpResultMenu extends Menu {
    private final SkinDefinition def;
    private final SkinInstance result;
    private final int inputs;

    public TradeUpResultMenu(CasesContext ctx, Player viewer, SkinDefinition def, SkinInstance result, int inputs) {
        super(ctx, viewer);
        this.def = def;
        this.result = result;
        this.inputs = inputs;
    }

    @Override protected int rows() { return 5; }

    @Override protected Component title() { return ctx.messages(viewer).get("tradeup.result-title"); }

    @Override protected void build() {
        Material pane = Material.matchMaterial(def.rarity().pane());
        var frame = GuiItems.filler(pane == null || !pane.isItem() ? Material.WHITE_STAINED_GLASS_PANE : pane);
        for (int slot = 0; slot < 45; slot++) {
            int row = slot / 9, col = slot % 9;
            if (row == 0 || row == 4 || col == 0 || col == 8) set(slot, frame);
        }
        for (int slot : new int[]{12, 14, 21, 23, 30, 32}) set(slot, frame);
        set(4, GuiItems.icon(ctx.messages(viewer), Material.PAPER, "tradeup.received", Text.unparsed("count", inputs)));
        set(22, SkinIcons.icon(def, ctx.formatter(viewer).fullName(def, result),
                ctx.formatter(viewer).lore(def, result, false), ctx.settings(), true));
        set(38, GuiItems.icon(ctx.messages(viewer), Material.GOLD_INGOT, "tradeup.again"), c -> new TradeUpMenu(ctx, viewer).open());
        set(42, GuiItems.close(ctx.messages(viewer)), c -> viewer.closeInventory());
    }
}
