package dev.plattnericus.cases.gui.menu;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.gui.MenuStates;
import dev.plattnericus.cases.gui.SkinQuery;
import dev.plattnericus.cases.items.SkinIcons;
import dev.plattnericus.cases.profile.PlayerProfile;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /skins and /knife. Left click inspects, right click equips a knife, shift click toggles favorite.
 */
public final class SkinInventoryMenu extends Menu {

    private static final Material[] TAB_ICONS = {Material.CHEST, Material.CROSSBOW, Material.IRON_SWORD,
            Material.COMPARATOR, Material.NETHER_STAR, Material.CLOCK};

    private final MenuStates.State state;

    /** Whose inventory is shown; null = the viewer's own. */
    private final PlayerProfile owner;
    private final String ownerName;
    private final boolean readOnly;
    /** Admin management: clicking edits the skin, plus give and clear-all buttons. */
    private boolean admin;

    public SkinInventoryMenu(CasesContext ctx, Player viewer) {
        this(ctx, viewer, null, viewer.getName(), false);
    }

    public SkinInventoryMenu(CasesContext ctx, Player viewer, PlayerProfile owner, String ownerName, boolean readOnly) {
        super(ctx, viewer);
        this.state = ctx.menuStates().get(viewer.getUniqueId());
        this.owner = owner;
        this.ownerName = ownerName;
        this.readOnly = readOnly;
    }

    private PlayerProfile profile() {
        return owner != null ? owner : ctx.profiles().get(viewer);
    }

    /** Opens this inventory as admin management view. */
    public SkinInventoryMenu admin() {
        this.admin = true;
        return this;
    }

    private SkinInventoryMenu again() {
        SkinInventoryMenu m = new SkinInventoryMenu(ctx, viewer, owner, ownerName, readOnly);
        m.admin = admin;
        return m;
    }

    public SkinInventoryMenu category(MenuStates.Category category) {
        state.category = category;
        state.page = 0;
        return this;
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected Component title() {
        if (admin) {
            return ctx.messages(viewer).get("admin.manage.title", dev.plattnericus.cases.util.Text.unparsed("player", ownerName));
        }
        return readOnly ? ctx.messages(viewer).get("gui.skins.title-other", dev.plattnericus.cases.util.Text.unparsed("player", ownerName))
                : ctx.messages(viewer).get("gui.skins.title");
    }

    @Override
    protected void build() {
        PlayerProfile profile = profile();
        if (profile == null) {
            set(22, GuiItems.icon(ctx.messages(viewer), Material.CLOCK, "gui.loading"));
            return;
        }
        MenuStates.Category[] categories = MenuStates.Category.values();
        for (int i = 0; i < categories.length; i++) {
            MenuStates.Category c = categories[i];
            String key = "gui.skins.tab." + c.name().toLowerCase(Locale.ROOT);
            set(1 + i, GuiItems.glowing(GuiItems.icon(ctx.messages(viewer), TAB_ICONS[i], key), c == state.category), click -> {
                if (state.category != c) {
                    state.category = c;
                    state.page = 0;
                    playClick();
                    render();
                }
            });
        }
        List<SkinQuery.Entry> entries = SkinQuery.run(ctx.catalog(), profile.owned(), state);
        int perPage = GuiItems.CONTENT.length;
        int pages = GuiItems.pages(entries.size(), perPage);
        state.page = Math.max(0, Math.min(state.page, pages - 1));
        if (entries.isEmpty()) {
            set(31, GuiItems.icon(ctx.messages(viewer), Material.LIGHT_GRAY_DYE, "gui.skins.empty"));
        }
        for (int i = 0; i < perPage; i++) {
            int idx = state.page * perPage + i;
            if (idx >= entries.size()) {
                break;
            }
            SkinQuery.Entry e = entries.get(idx);
            boolean equipped = profile.isEquipped(e.instance().id());
            List<Component> lore = new ArrayList<>(ctx.formatter(viewer).lore(e.definition(), e.instance(), false));
            if (equipped) {
                lore.add(ctx.messages(viewer).item("gui.skins.equipped"));
            }
            if (e.instance().favorite()) {
                lore.add(ctx.messages(viewer).item("gui.skins.favorite"));
            }
            if (!readOnly) {
                lore.addAll(ctx.messages(viewer).itemList(e.definition().isKnife() ? "gui.skins.hint-knife" : "gui.skins.hint"));
            }
            set(GuiItems.CONTENT[i], SkinIcons.icon(e.definition(), ctx.formatter(viewer).fullName(e.definition(), e.instance()),
                    lore, ctx.settings(), equipped), click -> onSkinClick(e.instance(), click));
        }
        set(GuiItems.SLOT_BACK, GuiItems.icon(ctx.messages(viewer), Material.CHEST_MINECART, "gui.skins.to-cases"), c -> {
            playClick();
            new CasesMenu(ctx, viewer).open();
        });
        set(GuiItems.SLOT_SORT, GuiItems.icon(ctx.messages(viewer), Material.HOPPER, "gui.skins.sort",
                Text.unparsed("sort", ctx.messages(viewer).raw("gui.skins.sorts." + state.sort.name().toLowerCase(Locale.ROOT)))), c -> {
            MenuStates.Sort[] sorts = MenuStates.Sort.values();
            int dir = c.isRightClick() ? -1 : 1;
            state.sort = sorts[Math.floorMod(state.sort.ordinal() + dir, sorts.length)];
            playClick();
            render();
        });
        set(GuiItems.SLOT_PREV, GuiItems.previous(ctx.messages(viewer), state.page, pages), c -> {
            if (state.page > 0) {
                state.page--;
                playClick();
                render();
            }
        });
        set(GuiItems.SLOT_CENTER, GuiItems.icon(ctx.messages(viewer), Material.BOOK, "gui.skins.info",
                Text.unparsed("count", entries.size()), Text.unparsed("total", profile.owned().size())), c -> viewer.closeInventory());
        set(GuiItems.SLOT_NEXT, GuiItems.next(ctx.messages(viewer), state.page, pages), c -> {
            if (state.page + 1 < pages) {
                state.page++;
                playClick();
                render();
            }
        });
        set(GuiItems.SLOT_FILTER, GuiItems.glowing(GuiItems.icon(ctx.messages(viewer), Material.SPYGLASS, "gui.skins.filter"),
                state.hasFilter()), c -> {
            playClick();
            if (c == ClickType.RIGHT && state.hasFilter()) {
                state.resetFilter();
                render();
            } else {
                new FilterMenu(ctx, viewer, () -> again().open()).open();
            }
        });
        SkinInstance knife = profile.equippedKnifeInstance();
        if (knife != null && ctx.catalog().skin(knife.skinId()) != null) {
            var def = ctx.catalog().skin(knife.skinId());
            List<Component> lore = new ArrayList<>(ctx.messages(viewer).itemList("gui.skins.active-knife"));
            set(GuiItems.SLOT_EXTRA, SkinIcons.icon(def, ctx.formatter(viewer).fullName(def, knife), lore, ctx.settings(), true), c -> {
                playClick();
                new SkinInspectMenu(ctx, viewer, knife, owner, readOnly, () -> again().open()).open();
            });
        }
        if (admin) {
            set(46, GuiItems.icon(ctx.messages(viewer), Material.EMERALD, "admin.manage.give"), c -> {
                playClick();
                new dev.plattnericus.cases.admin.AdminGiveMenu(ctx, viewer, profile, ownerName, null, () -> again().open()).open();
            });
            set(52, GuiItems.icon(ctx.messages(viewer), Material.LAVA_BUCKET, "admin.manage.clear",
                    dev.plattnericus.cases.util.Text.unparsed("count", profile.owned().size())), c -> {
                if (profile.owned().isEmpty()) {
                    playError();
                    return;
                }
                new ConfirmMenu(ctx, viewer, GuiItems.icon(ctx.messages(viewer), Material.LAVA_BUCKET, "admin.manage.clear",
                        dev.plattnericus.cases.util.Text.unparsed("count", profile.owned().size())), () -> {
                    var actions = new dev.plattnericus.cases.admin.AdminActions(ctx);
                    int n = 0;
                    for (SkinInstance s : new java.util.ArrayList<>(profile.owned())) {
                        actions.remove(profile, s);
                        n++;
                    }
                    ctx.messages(viewer).send(viewer, "admin.manage.cleared", dev.plattnericus.cases.util.Text.unparsed("count", n),
                            dev.plattnericus.cases.util.Text.unparsed("player", ownerName));
                    again().open();
                }, () -> again().open()).open();
            });
        }
        if (!readOnly && !admin) {
            if (viewer.hasPermission("mccases.market")) set(46, GuiItems.icon(ctx.messages(viewer), Material.EMERALD, "market.browse"), c -> new dev.plattnericus.cases.commerce.MarketMenu(ctx, viewer).open());
            if (viewer.hasPermission("mccases.tradein")) set(46, GuiItems.icon(ctx.messages(viewer), Material.GOLD_INGOT, "tradein.button"), c -> new dev.plattnericus.cases.tradein.TradeInMenu(ctx, viewer).open());
            if (viewer.hasPermission("mccases.trade")) set(52, GuiItems.icon(ctx.messages(viewer), Material.WRITABLE_BOOK, "trade.choose-partner"), c -> new dev.plattnericus.cases.commerce.TradePlayersMenu(ctx, viewer).open());
        }
    }

    private void onSkinClick(SkinInstance instance, ClickType click) {
        if (admin) {
            playClick();
            new dev.plattnericus.cases.admin.AdminSkinMenu(ctx, viewer, profile(), ownerName, instance, () -> again().open()).open();
            return;
        }
        if (readOnly) {
            playClick();
            new SkinInspectMenu(ctx, viewer, instance, owner, true, () -> again().open()).open();
            return;
        }
        var def = ctx.catalog().skin(instance.skinId());
        if (def == null) {
            return;
        }
        if (click.isShiftClick()) {
            ctx.profiles().setFavorite(viewer, instance, !instance.favorite());
            playClick();
            render();
            return;
        }
        if (click.isRightClick() && def.isKnife()) {
            ctx.knives().toggle(viewer, instance, dev.plattnericus.cases.profile.EquipSlot.KNIFE);
            render();
            return;
        }
        if (click.isRightClick() && ctx.settings().knives().bowSkins()) {
            ctx.knives().toggle(viewer, instance, dev.plattnericus.cases.profile.EquipSlot.BOW);
            render();
            return;
        }
        playClick();
        new SkinInspectMenu(ctx, viewer, instance, () -> again().open()).open();
    }
}
