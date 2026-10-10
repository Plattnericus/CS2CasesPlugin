package dev.plattnericus.cases.gui.menu;

import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.items.SkinIcons;
import dev.plattnericus.cases.profile.EquipSlot;
import dev.plattnericus.cases.profile.PlayerProfile;
import dev.plattnericus.cases.skin.SkinInstance;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Full inspect view of one instance: every relevant value, the rendered map preview in the centre
 * and the actions that apply to it (equip and 3D inspect for knives).
 */
public final class SkinInspectMenu extends Menu {

    private final SkinInstance instance;
    private final Runnable back;
    /** Whose skin this is; null = the viewer's own profile. */
    private final PlayerProfile owner;
    private final boolean readOnly;

    public SkinInspectMenu(CasesContext ctx, Player viewer, SkinInstance instance, Runnable back) {
        this(ctx, viewer, instance, null, false, back);
    }

    /** {@code readOnly} shows another player's skin: values, preview and 3D inspect, but no actions. */
    public SkinInspectMenu(CasesContext ctx, Player viewer, SkinInstance instance, PlayerProfile owner, boolean readOnly,
                           Runnable back) {
        super(ctx, viewer);
        this.instance = instance;
        this.back = back;
        this.owner = owner;
        this.readOnly = readOnly;
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override protected boolean canClick(int slot) {
        if (readOnly || slot == GuiItems.SLOT_BACK || slot == GuiItems.SLOT_CENTER) return true;
        if (ctx.commerce().mutable(owner != null ? owner : ctx.profiles().get(viewer), instance)) return true;
        ctx.messages(viewer).send(viewer, "commerce.locked"); return false;
    }

    @Override
    protected Component title() {
        return GuiItems.brandedTitle(ctx, ctx.messages(viewer).get("gui.inspect.title"), '\uE008');
    }

    @Override
    protected void build() {
        SkinDefinition def = ctx.catalog().skin(instance.skinId());
        PlayerProfile profile = owner != null ? owner : ctx.profiles().get(viewer);
        if (def == null || profile == null || profile.get(instance.id()) == null) {
            set(22, GuiItems.icon(ctx.messages(viewer), Material.BARRIER, "gui.inspect.missing"));
            set(GuiItems.SLOT_BACK, GuiItems.back(ctx.messages(viewer)), c -> back.run());
            return;
        }
        set(13, SkinIcons.icon(def, ctx.formatter(viewer).fullName(def, instance),
                ctx.formatter(viewer).lore(def, instance, true), ctx.settings(), false));
        set(22, GuiItems.icon(Material.FILLED_MAP, ctx.messages(viewer).item("gui.inspect.preview.name"),
                ctx.messages(viewer).itemList("gui.inspect.preview.lore")), c -> {
            playClick();
            viewer.closeInventory();
            ctx.previews().show(viewer, def, instance.pattern(), instance.floatValue(), instance.wearSeed(),
                    ctx.formatter(viewer).fullName(def, instance), () -> new SkinInspectMenu(ctx, viewer, instance, owner, readOnly, back).open());
        });
        boolean equipped = instance.id().equals(profile.equippedKnife());
        if (readOnly) {
            if (def.isKnife()) {
                set(33, GuiItems.icon(ctx.messages(viewer), Material.ARMOR_STAND, "gui.inspect.inspect3d"), c -> {
                    playClick();
                    viewer.closeInventory();
                    ctx.inspect().start(viewer, instance, false);
                });
            }
            set(31, GuiItems.icon(ctx.messages(viewer), Material.PLAYER_HEAD, "gui.inspect.foreign"));
            set(40, GuiItems.icon(Material.PAPER, ctx.messages(viewer).item("gui.inspect.data"),
                    new ArrayList<>(ctx.formatter(viewer).lore(def, instance, true))));
            set(GuiItems.SLOT_BACK, GuiItems.back(ctx.messages(viewer)), c -> {
                playClick();
                back.run();
            });
            set(GuiItems.SLOT_CENTER, GuiItems.close(ctx.messages(viewer)), c -> viewer.closeInventory());
            return;
        }
        set(29, GuiItems.glowing(GuiItems.icon(ctx.messages(viewer), Material.NETHER_STAR,
                instance.favorite() ? "gui.inspect.unfavorite" : "gui.inspect.favorite"), instance.favorite()), c -> {
            ctx.profiles().setFavorite(viewer, instance, !instance.favorite());
            playClick();
            render();
        });
        if (def.isKnife()) {
            set(31, GuiItems.glowing(GuiItems.icon(ctx.messages(viewer), equipped ? Material.LIME_DYE : Material.IRON_SWORD,
                    equipped ? "gui.inspect.unequip" : "gui.inspect.equip"), equipped), c -> {
                if (equipped) {
                    ctx.knives().unequip(viewer);
                } else {
                    ctx.knives().equip(viewer, instance);
                }
                render();
            });
            set(33, GuiItems.icon(ctx.messages(viewer), Material.ARMOR_STAND, "gui.inspect.inspect3d"), c -> {
                playClick();
                viewer.closeInventory();
                ctx.inspect().start(viewer, instance, false);
            });
        } else if (ctx.settings().knives().bowSkins() || ctx.settings().knives().crossbowSkins()) {
            EquipSlot current = profile.slotOf(instance.id());
            if (ctx.settings().knives().bowSkins()) {
                boolean onBow = current == EquipSlot.BOW;
                set(30, GuiItems.glowing(GuiItems.icon(ctx.messages(viewer), onBow ? Material.LIME_DYE : Material.BOW,
                        onBow ? "gui.inspect.unequip-bow" : "gui.inspect.equip-bow"), onBow), c -> {
                    ctx.knives().toggle(viewer, instance, EquipSlot.BOW);
                    render();
                });
            }
            if (ctx.settings().knives().crossbowSkins()) {
                boolean onCrossbow = current == EquipSlot.CROSSBOW;
                set(32, GuiItems.glowing(GuiItems.icon(ctx.messages(viewer), onCrossbow ? Material.LIME_DYE : Material.CROSSBOW,
                        onCrossbow ? "gui.inspect.unequip-crossbow" : "gui.inspect.equip-crossbow"), onCrossbow), c -> {
                    ctx.knives().toggle(viewer, instance, EquipSlot.CROSSBOW);
                    render();
                });
            }
        } else {
            set(31, GuiItems.icon(ctx.messages(viewer), Material.GRAY_DYE, "gui.inspect.not-equippable"));
        }
        if (!def.isKnife()) set(33, GuiItems.icon(ctx.messages(viewer), Material.ARMOR_STAND, "gui.inspect.inspect3d"), c -> {
            playClick();
            viewer.closeInventory();
            ctx.inspect().start(viewer, instance, false);
        });
        List<Component> data = new ArrayList<>(ctx.formatter(viewer).lore(def, instance, true));
        set(40, GuiItems.icon(Material.PAPER, ctx.messages(viewer).item("gui.inspect.data"), data));
        if (viewer.hasPermission("mccases.market")) set(46, GuiItems.icon(ctx.messages(viewer), Material.EMERALD, "market.sell"), c ->
                new dev.plattnericus.cases.commerce.SellMenu(ctx, viewer, instance, 100).open());
        set(GuiItems.SLOT_BACK, GuiItems.back(ctx.messages(viewer)), c -> {
            playClick();
            back.run();
        });
        set(GuiItems.SLOT_CENTER, GuiItems.close(ctx.messages(viewer)), c -> viewer.closeInventory());
        set(GuiItems.SLOT_EXTRA, GuiItems.icon(ctx.messages(viewer), Material.LAVA_BUCKET, "gui.inspect.delete"), c ->
                new ConfirmMenu(ctx, viewer, SkinIcons.icon(def, ctx.formatter(viewer).fullName(def, instance),
                        ctx.formatter(viewer).lore(def, instance, true), ctx.settings(), false), () -> {
                    new dev.plattnericus.cases.admin.AdminActions(ctx).remove(profile, instance);
                    ctx.messages(viewer).send(viewer, "skin.deleted", dev.plattnericus.cases.util.Text.component("skin",
                            ctx.formatter(viewer).fullName(def, instance)));
                    back.run();
                }, () -> new SkinInspectMenu(ctx, viewer, instance, owner, readOnly, back).open()).open());
    }
}
