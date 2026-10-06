package dev.plattnericus.cases.gui.menu;

import dev.plattnericus.cases.catalog.CaseDefinition;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.items.SkinIcons;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** All knives a case can drop. */
public final class RareSpecialMenu extends Menu {

    private final CaseDefinition def;
    private final List<SkinDefinition> specials;
    private final Runnable back;
    private int page;

    public RareSpecialMenu(CasesContext ctx, Player viewer, CaseDefinition def, List<SkinDefinition> specials, Runnable back) {
        super(ctx, viewer);
        this.def = def;
        this.specials = new ArrayList<>(specials);
        this.specials.sort(Comparator.comparing(SkinDefinition::displayName));
        this.back = back;
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected Component title() {
        return ctx.messages(viewer).get("gui.rare.title", Text.unparsed("case", def.name()));
    }

    @Override
    protected void build() {
        int perPage = GuiItems.CONTENT.length;
        int pages = GuiItems.pages(specials.size(), perPage);
        page = Math.min(page, pages - 1);
        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= specials.size()) {
                break;
            }
            SkinDefinition skin = specials.get(idx);
            List<Component> lore = new ArrayList<>(ctx.formatter(viewer).previewLore(skin));
            lore.add(ctx.messages(viewer).item("gui.preview.click-preview"));
            set(GuiItems.CONTENT[i], SkinIcons.icon(skin, ctx.formatter(viewer).name(skin, null), lore, ctx.settings(), true), c -> {
                playClick();
                int keep = page;
                double fl = Math.max(skin.minFloat(), Math.min(skin.maxFloat(), ctx.settings().preview().showcaseFloat()));
                viewer.closeInventory();
                ctx.previews().show(viewer, skin, ctx.settings().preview().showcaseSeed(), fl, 0,
                        ctx.formatter(viewer).name(skin, null), () -> {
                            RareSpecialMenu again = new RareSpecialMenu(ctx, viewer, def, specials, back);
                            again.page = keep;
                            again.open();
                        });
            });
        }
        set(GuiItems.SLOT_BACK, GuiItems.back(ctx.messages(viewer)), c -> {
            playClick();
            back.run();
        });
        set(GuiItems.SLOT_PREV, GuiItems.previous(ctx.messages(viewer), page, pages), c -> {
            if (page > 0) {
                page--;
                playClick();
                render();
            }
        });
        set(GuiItems.SLOT_CENTER, GuiItems.close(ctx.messages(viewer)), c -> viewer.closeInventory());
        set(GuiItems.SLOT_NEXT, GuiItems.next(ctx.messages(viewer), page, pages), c -> {
            if (page + 1 < pages) {
                page++;
                playClick();
                render();
            }
        });
    }
}
