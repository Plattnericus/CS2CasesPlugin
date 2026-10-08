package dev.plattnericus.cases.opening;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.items.SkinIcons;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** One visible chest may browse many independent server-side sessions. */
public final class ActiveOpeningsMenu extends Menu {
    private int page;
    public ActiveOpeningsMenu(CasesContext ctx, Player p) { super(ctx, p); }
    @Override protected int rows() { return 6; }
    @Override protected Component title() { return ctx.messages(viewer).get("opening.active-title"); }
    @Override protected void build() {
        var active = ctx.openings().active(viewer);
        var recent = ctx.openings().recent(viewer);
        int pages = GuiItems.pages(active.size() + recent.size(), GuiItems.CONTENT.length); page = Math.min(page, pages - 1);
        for (int i = 0; i < GuiItems.CONTENT.length; i++) {
            int index = page * GuiItems.CONTENT.length + i;
            if (index < active.size()) {
                var session = active.get(index);
                set(GuiItems.CONTENT[i], GuiItems.icon(ctx.messages(viewer), Material.CLOCK, "opening.active-session",
                        Text.unparsed("case", session.caseName()), Text.unparsed("id", session.id().toString().substring(0, 8)),
                        Text.unparsed("state", ctx.messages(viewer).raw("opening.states." + session.state().name()))), c -> ctx.openings().show(viewer, session.id()));
            } else if (index - active.size() < recent.size()) {
                var instance = recent.get(index - active.size()); var def = ctx.catalog().skin(instance.skinId());
                if (def != null) set(GuiItems.CONTENT[i], SkinIcons.icon(def, ctx.formatter(viewer).fullName(def, instance),
                        ctx.formatter(viewer).lore(def, instance, true), ctx.settings(), false));
            }
        }
        set(45, GuiItems.back(ctx.messages(viewer)), c -> new dev.plattnericus.cases.gui.menu.CasesMenu(ctx, viewer).open());
        set(48, GuiItems.previous(ctx.messages(viewer), page, pages), c -> { if (page > 0) { page--; render(); } });
        set(49, GuiItems.icon(ctx.messages(viewer), Material.CLOCK, "opening.active-refresh"), c -> render());
        set(50, GuiItems.next(ctx.messages(viewer), page, pages), c -> { if (page + 1 < pages) { page++; render(); } });
        set(53, GuiItems.close(ctx.messages(viewer)), c -> viewer.closeInventory());
    }
}
