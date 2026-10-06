package dev.plattnericus.cases.admin;

import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.items.SkinIcons;
import dev.plattnericus.cases.pattern.PatternReport;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Admin tool: step through seeds of a skin and see the analysis of each. */
public final class PatternBrowserMenu extends Menu {

    private final SkinDefinition skin;
    private int seed;
    private PatternReport report;

    public PatternBrowserMenu(CasesContext ctx, Player viewer, SkinDefinition skin, int seed) {
        super(ctx, viewer);
        this.skin = skin;
        this.seed = clamp(seed);
    }

    private int clamp(int s) {
        int min = ctx.catalog().patterns().seedMin();
        int max = ctx.catalog().patterns().seedMax();
        int range = max - min + 1;
        return min + Math.floorMod(s - min, range);
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected Component title() {
        return ctx.messages(viewer).get("admin.browser.title", Text.unparsed("skin", skin.displayName()));
    }

    @Override
    public void open() {
        super.open();
        analyse();
    }

    private void analyse() {
        int requested = seed;
        report = null;
        ctx.render().report(skin, requested).whenComplete((r, e) -> Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
            if (seed == requested && viewer.getOpenInventory().getTopInventory().getHolder(false) == this) {
                report = r;
                render();
            }
        }));
    }

    @Override
    protected void build() {
        List<Component> lore = new ArrayList<>();
        lore.add(ctx.messages(viewer).item("admin.debug.seed", Text.unparsed("seed", seed)));
        if (report == null) {
            lore.add(ctx.messages(viewer).item("admin.debug.analysing"));
        } else {
            if (report.variantName() != null) {
                lore.add(ctx.messages(viewer).item("admin.debug.variant", Text.unparsed("variant", report.variantName())));
            }
            lore.add(ctx.messages(viewer).item("admin.debug.classification", Text.unparsed("classification", report.classification() == null ? "-" : report.classification()),
                    Text.unparsed("mode", report.manual() ? ctx.messages(viewer).raw("admin.labels.manual") : "")));
            if (report.fadePercent() != null) {
                lore.add(ctx.messages(viewer).item("admin.debug.fade", Text.unparsed("fade", report.fadePercent())));
            }
            for (Map.Entry<String, Double> m : report.metrics().entrySet()) {
                lore.add(Text.item("<dark_gray>" + m.getKey() + ": <gray>" + Text.formatFloat(m.getValue() * (m.getKey().equals("fade") ? 1 : 100), 1)
                        + (m.getKey().equals("fade") ? "" : "%")));
            }
        }
        set(13, SkinIcons.icon(skin, ctx.formatter(viewer).name(skin, null), lore, ctx.settings(), false));
        set(22, GuiItems.icon(ctx.messages(viewer), Material.FILLED_MAP, "admin.debug.preview", Text.unparsed("seed", seed)), c -> {
            viewer.closeInventory();
            double fl = Math.max(skin.minFloat(), Math.min(skin.maxFloat(), 0.01));
            int keep = seed;
            ctx.previews().show(viewer, skin, keep, fl, 0, ctx.messages(viewer).item("admin.debug.seed", Text.unparsed("seed", keep)),
                    () -> new PatternBrowserMenu(ctx, viewer, skin, keep).open());
        });
        step(19, -10);
        step(20, -1);
        step(24, 1);
        step(25, 10);
        set(31, GuiItems.icon(ctx.messages(viewer), Material.ENDER_EYE, "admin.debug.random"), c -> {
            seed = clamp(ctx.openings().roller().random().nextInt(1000));
            analyse();
            render();
        });
        set(GuiItems.SLOT_CENTER, GuiItems.close(ctx.messages(viewer)), c -> viewer.closeInventory());
    }

    private void step(int slot, int delta) {
        Material m = delta < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE;
        set(slot, GuiItems.icon(m, Text.item((delta < 0 ? "<red>" : "<green>+") + delta), List.of()), c -> {
            seed = clamp(seed + delta);
            playClick();
            analyse();
            render();
        });
    }
}
