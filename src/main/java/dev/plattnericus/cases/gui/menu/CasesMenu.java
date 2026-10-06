package dev.plattnericus.cases.gui.menu;

import dev.plattnericus.cases.catalog.CaseDefinition;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.items.CaseItems;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** /cases: every case with the player's case and key counts. */
public final class CasesMenu extends Menu {

    private int page;

    public CasesMenu(CasesContext ctx, Player viewer) {
        super(ctx, viewer);
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected Component title() {
        return ctx.messages(viewer).get("gui.cases.title");
    }

    @Override
    protected void build() {
        List<CaseDefinition> cases = new ArrayList<>();
        for (CaseDefinition def : ctx.catalog().cases()) {
            if (def.enabled()) {
                cases.add(def);
            }
        }
        CaseItems items = ctx.caseItems();
        java.util.Map<String, Integer> ownedCounts = new java.util.HashMap<>();
        for (CaseDefinition d : cases) {
            ownedCounts.put(d.id(), items.count(viewer, CaseItems.TYPE_CASE, d.id()));
        }
        cases.sort(Comparator.comparingInt((CaseDefinition d) -> -ownedCounts.get(d.id()))
                .thenComparing(CaseDefinition::name));
        int perPage = GuiItems.CONTENT.length;
        int pages = GuiItems.pages(cases.size(), perPage);
        page = Math.min(page, pages - 1);
        set(4, GuiItems.icon(ctx.messages(viewer), Material.CHEST, "gui.cases.header"));
        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= cases.size()) {
                break;
            }
            CaseDefinition def = cases.get(idx);
            int owned = ownedCounts.get(def.id());
            int keys = items.count(viewer, CaseItems.TYPE_KEY, def.keyId());
            ItemStack icon = items.caseIcon(def, ctx.messages(viewer));
            icon.editMeta(meta -> {
                List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
                lore.addAll(ctx.messages(viewer).itemList("gui.cases.entry",
                        Text.unparsed("owned", owned), Text.unparsed("keys", keys)));
                if (owned > 0 && keys > 0) {
                    lore.add(ctx.messages(viewer).item("gui.cases.can-open"));
                }
                meta.lore(lore);
                if (owned > 0) {
                    meta.setEnchantmentGlintOverride(true);
                }
            });
            set(GuiItems.CONTENT[i], icon, click -> {
                playClick();
                if (click.isRightClick() && owned > 0 && keys > 0) {
                    viewer.closeInventory();
                    ctx.openings().open(viewer, def, false, false);
                } else {
                    new CasePreviewMenu(ctx, viewer, def, this::reopen).open();
                }
            });
        }
        set(GuiItems.SLOT_BACK, GuiItems.icon(ctx.messages(viewer), Material.ENDER_CHEST, "gui.cases.to-skins"), c -> {
            playClick();
            ctx.gallery().open(viewer, null);
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

    private void reopen() {
        new CasesMenu(ctx, viewer).openAt(page);
    }

    private void openAt(int targetPage) {
        this.page = targetPage;
        open();
    }
}
