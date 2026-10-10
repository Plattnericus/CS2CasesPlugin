package dev.plattnericus.cases.gui.menu;

import dev.plattnericus.cases.catalog.CaseDefinition;
import dev.plattnericus.cases.catalog.Rarity;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.items.CaseItems;
import dev.plattnericus.cases.items.SkinIcons;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Case contents: every skin by rarity (highest first) and the rare special items behind a
 * mystery icon, like the CS2 case inspect. Clicking a skin shows its rendered preview.
 */
public final class CasePreviewMenu extends Menu {

    private final CaseDefinition def;
    private final Runnable back;
    private int page;
    private int quantityIndex;
    private static final int[] QUANTITIES = {9, 18, 25, 50, 90, 100};

    public CasePreviewMenu(CasesContext ctx, Player viewer, CaseDefinition def, Runnable back) {
        super(ctx, viewer);
        this.def = def;
        this.back = back;
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected Component title() {
        return ctx.messages(viewer).get("gui.preview.title", Text.unparsed("case", def.name()));
    }

    @Override
    protected void build() {
        List<Object> entries = new ArrayList<>();
        List<Rarity> rarities = new ArrayList<>(ctx.catalog().raritiesOrdered());
        java.util.Collections.reverse(rarities);
        List<SkinDefinition> rare = new ArrayList<>();
        for (Rarity r : rarities) {
            List<SkinDefinition> skins = def.skins(r);
            if (r.rareSpecial()) {
                rare.addAll(skins);
            } else {
                entries.addAll(skins);
            }
        }
        if (!rare.isEmpty()) {
            entries.addFirst(rare);
        }
        int perPage = GuiItems.CONTENT.length;
        int pages = GuiItems.pages(entries.size(), perPage);
        page = Math.min(page, pages - 1);
        set(4, ctx.caseItems().caseIcon(def, ctx.messages(viewer)));
        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= entries.size()) {
                break;
            }
            Object entry = entries.get(idx);
            if (entry instanceof SkinDefinition skin) {
                List<Component> lore = new ArrayList<>(ctx.formatter(viewer).previewLore(skin));
                lore.add(ctx.messages(viewer).item("gui.preview.click-preview"));
                ItemStack icon = SkinIcons.icon(skin, ctx.formatter(viewer).name(skin, null), lore, ctx.settings(), false);
                set(GuiItems.CONTENT[i], icon, c -> showPreview(skin));
            } else {
                @SuppressWarnings("unchecked")
                List<SkinDefinition> specials = (List<SkinDefinition>) entry;
                set(GuiItems.CONTENT[i], rareIcon(specials), c -> {
                    playClick();
                    new RareSpecialMenu(ctx, viewer, def, specials, () -> new CasePreviewMenu(ctx, viewer, def, back).openAt(page)).open();
                });
            }
        }
        set(GuiItems.SLOT_BACK, GuiItems.back(ctx.messages(viewer)), c -> {
            playClick();
            if (back != null) {
                back.run();
            } else {
                viewer.closeInventory();
            }
        });
        set(GuiItems.SLOT_PREV, GuiItems.previous(ctx.messages(viewer), page, pages), c -> {
            if (page > 0) {
                page--;
                playClick();
                render();
            }
        });
        set(GuiItems.SLOT_NEXT, GuiItems.next(ctx.messages(viewer), page, pages), c -> {
            if (page + 1 < pages) {
                page++;
                playClick();
                render();
            }
        });
        int owned = ctx.caseItems().count(viewer, CaseItems.TYPE_CASE, def.id());
        int keys = ctx.caseItems().count(viewer, CaseItems.TYPE_KEY, def.keyId());
        int available = ctx.openings().available(viewer, def);
        boolean canOpen = available > 0;
        String keyName = ctx.catalog().key(def.keyId()) == null ? def.keyId() : ctx.catalog().key(def.keyId()).name();
        set(GuiItems.SLOT_CENTER, GuiItems.icon(ctx.messages(viewer), canOpen ? Material.LIME_DYE : Material.GRAY_DYE,
                canOpen ? "gui.preview.open" : "gui.preview.cannot-open",
                Text.unparsed("owned", owned), Text.unparsed("keys", keys), Text.unparsed("key", keyName)), c -> {
            if (!canOpen) {
                playError();
                return;
            }
            playClick();
            ctx.openings().open(viewer, def, false, false);
            render();
        });
        int quantity = QUANTITIES[quantityIndex];
        set(GuiItems.SLOT_FILTER, GuiItems.icon(ctx.messages(viewer), available >= quantity ? Material.ENDER_CHEST : Material.GRAY_DYE,
                quantityIndex == 0 ? "gui.preview.open-nine" : "gui.preview.queue-start", Text.unparsed("amount", QUANTITIES[quantityIndex]),
                Text.unparsed("owned", owned), Text.unparsed("keys", keys),
                Text.unparsed("available", available), Text.unparsed("key", keyName)), c -> {
            playClick();
            ctx.openings().queue(viewer, def, QUANTITIES[quantityIndex]);
            render();
        });
        set(47, GuiItems.icon(ctx.messages(viewer), Material.COMPARATOR, "gui.preview.queue-quantity",
                Text.unparsed("amount", quantity)), c -> new dev.plattnericus.cases.gui.ChoiceMenu<>(ctx, viewer, "menus.quantity-title",
                java.util.Arrays.stream(QUANTITIES).boxed().toList(), quantity, value -> value + " cases",
                value -> { for (int i = 0; i < QUANTITIES.length; i++) if (QUANTITIES[i] == value) quantityIndex = i; open(); },
                this::open, () -> { }).open());
        set(GuiItems.SLOT_EXTRA, GuiItems.close(ctx.messages(viewer)), c -> viewer.closeInventory());
    }

    private ItemStack rareIcon(List<SkinDefinition> specials) {
        Rarity rarity = specials.getFirst().rarity();
        List<Component> lore = new ArrayList<>(ctx.messages(viewer).itemList("gui.preview.rare.lore",
                Text.unparsed("count", specials.size())));
        java.util.LinkedHashSet<String> weapons = new java.util.LinkedHashSet<>();
        for (SkinDefinition s : specials) {
            weapons.add(s.weapon().name());
        }
        for (String w : weapons) {
            lore.add(Text.item("<dark_gray>• <gray>★ " + Text.escape(w)));
        }
        return GuiItems.icon(Material.GOLD_INGOT, ctx.messages(viewer).item("gui.preview.rare.name",
                Text.color("rarity_color", rarity.color()), Text.unparsed("rarity", ctx.messages(viewer).label("rarity." + rarity.id(), rarity.name()))), lore);
    }

    private void showPreview(SkinDefinition skin) {
        playClick();
        int seed = ctx.settings().preview().showcaseSeed();
        double fl = Math.max(skin.minFloat(), Math.min(skin.maxFloat(), ctx.settings().preview().showcaseFloat()));
        int keepPage = page;
        viewer.closeInventory();
        ctx.previews().show(viewer, skin, seed, fl, 0, ctx.formatter(viewer).name(skin, null),
                () -> new CasePreviewMenu(ctx, viewer, def, back).openAt(keepPage));
    }

    void openAt(int targetPage) {
        this.page = targetPage;
        open();
    }
}
