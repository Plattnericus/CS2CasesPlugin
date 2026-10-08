package dev.plattnericus.cases.opening;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The 3-row roulette: row 2 is the moving strip, rows 1 and 3 carry the rarity color of the item
 * below/above them and the fixed marker in the middle column.
 */
final class OpeningMenu extends Menu implements OpeningView {

    static final int CENTER = 13;
    private static final int MARKER_TOP = 4;
    private static final int MARKER_BOTTOM = 22;

    private final OpeningSession session;
    private final OpeningService service;
    private final Map<String, ItemStack> iconCache = new HashMap<>();
    private final Map<String, ItemStack> paneCache = new HashMap<>();
    private int offset;
    private boolean result;

    OpeningMenu(CasesContext ctx, Player viewer, OpeningSession session, OpeningService service) {
        super(ctx, viewer);
        this.session = session;
        this.service = service;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected Component title() {
        return ctx.messages(viewer).get(session.adminTest ? "opening.title-test" : "opening.title", Text.unparsed("case", session.caseDef.name()));
    }

    @Override
    public void frame(double center) {
        int target = (int) Math.floor(center) - 4;
        if (target != offset || inventoryMissing()) {
            showOffset(target);
        }
    }

    @Override
    public void reveal() {
        showOffset(session.winnerIndex - 4);
    }

    @Override
    public void result() {
        showResult();
    }

    @Override
    public void close() {
        if (viewer.getOpenInventory().getTopInventory().getHolder(false) == this) {
            viewer.closeInventory();
        }
    }

    private boolean inventoryMissing() {
        return getInventory() == null;
    }

    void showOffset(int offset) {
        this.offset = offset;
        render();
    }

    void showResult() {
        this.result = true;
        render();
    }

    @Override
    protected void build() {
        List<SkinDefinition> reel = session.reel;
        for (int col = 0; col < 9; col++) {
            int idx = offset + col;
            if (idx < 0 || idx >= reel.size()) {
                continue;
            }
            SkinDefinition def = reel.get(idx);
            boolean isWinnerSlot = result && col == 4;
            set(9 + col, isWinnerSlot ? winnerIcon() : reelIcon(def, idx == session.winnerIndex && session.revealed));
            if (!result) {
                set(col, pane(def.rarity()));
                set(18 + col, pane(def.rarity()));
            }
        }
        if (!result) {
            set(MARKER_TOP, marker(ctx.settings().opening().markerTop(), "opening.marker"));
            set(MARKER_BOTTOM, marker(ctx.settings().opening().markerBottom(), "opening.marker"));
            int cases = ctx.caseItems().count(viewer, CaseItems.TYPE_CASE, session.caseDef.id());
            int keys = ctx.caseItems().count(viewer, CaseItems.TYPE_KEY, session.caseDef.keyId());
            if (!session.adminTest) set(18, GuiItems.icon(ctx.messages(viewer), Material.LIME_DYE, "opening.buttons.again",
                    Text.unparsed("cases", cases), Text.unparsed("keys", keys)), c -> service.open(viewer, session.caseDef, false, false));
            set(26, GuiItems.icon(ctx.messages(viewer), Material.CLOCK, "opening.active-button"), c -> new ActiveOpeningsMenu(ctx, viewer).open());
            return;
        }
        ItemStack winnerPane = pane(session.reward.rarity());
        for (int col = 0; col < 9; col++) {
            set(col, winnerPane);
            set(18 + col, winnerPane);
        }
        set(CENTER, winnerIcon(), c -> service.openInspect(viewer, session));
        set(MARKER_BOTTOM, GuiItems.icon(ctx.messages(viewer), Material.SPYGLASS, "opening.buttons.inspect"),
                c -> service.openInspect(viewer, session));
        int cases = ctx.caseItems().count(viewer, CaseItems.TYPE_CASE, session.caseDef.id());
        int keys = ctx.caseItems().count(viewer, CaseItems.TYPE_KEY, session.caseDef.keyId());
        if (!session.adminTest && cases > 0 && keys > 0) {
            set(18, GuiItems.icon(ctx.messages(viewer), Material.LIME_DYE, "opening.buttons.again",
                    Text.unparsed("cases", cases), Text.unparsed("keys", keys)), c -> service.openAgain(viewer, session));
        }
        set(26, GuiItems.close(ctx.messages(viewer)), c -> viewer.closeInventory());
    }

    private ItemStack winnerIcon() {
        if (session.instance == null) {
            return reelIcon(session.reward, true);
        }
        return SkinIcons.icon(session.reward, ctx.formatter(viewer).fullName(session.reward, session.instance),
                ctx.formatter(viewer).lore(session.reward, session.instance, false), ctx.settings(), true);
    }

    private ItemStack reelIcon(SkinDefinition def, boolean revealedWinner) {
        if (def.rarity().rareSpecial() && !revealedWinner) {
            return iconCache.computeIfAbsent("?mystery", k -> GuiItems.glowing(GuiItems.icon(Material.GOLD_INGOT,
                    ctx.messages(viewer).item("opening.mystery", Text.color("rarity_color", def.rarity().color()),
                            Text.unparsed("rarity", ctx.messages(viewer).label("rarity." + def.rarity().id(), def.rarity().name()))), List.of()), true));
        }
        return iconCache.computeIfAbsent(def.id(), k -> SkinIcons.icon(def, ctx.formatter(viewer).name(def, null),
                List.of(ctx.messages(viewer).item("skin.lore.rarity", Text.color("rarity_color", def.rarity().color()),
                        Text.unparsed("rarity", ctx.messages(viewer).label("rarity." + def.rarity().id(), def.rarity().name())))), ctx.settings(), false));
    }

    private ItemStack pane(Rarity rarity) {
        return paneCache.computeIfAbsent(rarity.id(), k -> {
            Material m = Material.matchMaterial(rarity.pane());
            return GuiItems.filler(m == null || !m.isItem() ? Material.WHITE_STAINED_GLASS_PANE : m);
        });
    }

    private ItemStack marker(Material material, String key) {
        return paneCache.computeIfAbsent("marker:" + material.name(), k -> GuiItems.icon(material,
                ctx.messages(viewer).item(key), List.of()));
    }

    @Override
    protected void onLanguageChange() {
        iconCache.clear();
        paneCache.clear();
    }

    @Override
    protected void onClose() {
        service.onMenuClosed(viewer, session);
    }
}
