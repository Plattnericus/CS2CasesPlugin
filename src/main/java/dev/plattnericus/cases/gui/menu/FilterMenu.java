package dev.plattnericus.cases.gui.menu;

import dev.plattnericus.cases.catalog.Rarity;
import dev.plattnericus.cases.catalog.WearTier;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.gui.MenuStates;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Rarity, exterior and StatTrak filters of the skin inventory. */
public final class FilterMenu extends Menu {

    private final MenuStates.State state;
    private final Runnable back;

    public FilterMenu(CasesContext ctx, Player viewer, Runnable back) {
        super(ctx, viewer);
        this.state = ctx.menuStates().get(viewer.getUniqueId());
        this.back = back;
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected Component title() {
        return ctx.messages(viewer).get("gui.filter.title");
    }

    @Override
    protected void build() {
        List<Rarity> rarities = ctx.catalog().raritiesOrdered();
        set(4, GuiItems.icon(ctx.messages(viewer), Material.SPYGLASS, "gui.filter.header"));
        int slot = 10;
        for (Rarity r : rarities) {
            if (slot > 16) {
                break;
            }
            boolean on = state.rarities.contains(r.id());
            Material pane = Material.matchMaterial(r.pane());
            set(slot++, GuiItems.glowing(GuiItems.icon(pane == null ? Material.WHITE_STAINED_GLASS_PANE : pane,
                    ctx.messages(viewer).item("gui.filter.rarity", Text.color("rarity_color", r.color()), Text.unparsed("rarity", ctx.messages(viewer).label("rarity." + r.id(), r.name()))),
                    ctx.messages(viewer).itemList(on ? "gui.filter.on" : "gui.filter.off")), on), c -> {
                if (!state.rarities.remove(r.id())) {
                    state.rarities.add(r.id());
                }
                state.page = 0;
                playClick();
                render();
            });
        }
        slot = 19;
        for (WearTier w : ctx.catalog().wear().tiers()) {
            if (slot > 25) {
                break;
            }
            boolean on = state.wears.contains(w.id());
            set(slot++, GuiItems.glowing(GuiItems.icon(Material.PAPER,
                    ctx.messages(viewer).item("gui.filter.wear", Text.unparsed("wear", ctx.formatter(viewer).wearName(w)), Text.unparsed("short", w.shortName())),
                    ctx.messages(viewer).itemList(on ? "gui.filter.on" : "gui.filter.off")), on), c -> {
                if (!state.wears.remove(w.id())) {
                    state.wears.add(w.id());
                }
                state.page = 0;
                playClick();
                render();
            });
        }
        set(31, GuiItems.glowing(GuiItems.icon(Material.COMPARATOR, ctx.messages(viewer).item("gui.filter.stattrak"),
                ctx.messages(viewer).itemList(state.statTrakOnly ? "gui.filter.on" : "gui.filter.off")), state.statTrakOnly), c -> {
            state.statTrakOnly = !state.statTrakOnly;
            state.page = 0;
            playClick();
            render();
        });
        set(GuiItems.SLOT_BACK, GuiItems.back(ctx.messages(viewer)), c -> {
            playClick();
            back.run();
        });
        set(GuiItems.SLOT_CENTER, GuiItems.icon(ctx.messages(viewer), Material.WATER_BUCKET, "gui.filter.reset"), c -> {
            state.resetFilter();
            playClick();
            render();
        });
    }
}
