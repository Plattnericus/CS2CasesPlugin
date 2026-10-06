package dev.plattnericus.cases.admin;

import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.catalog.WearScale;
import dev.plattnericus.cases.catalog.WearTier;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.gui.menu.ConfirmMenu;
import dev.plattnericus.cases.items.SkinIcons;
import dev.plattnericus.cases.profile.EquipSlot;
import dev.plattnericus.cases.profile.PlayerProfile;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Admin editing of one skin instance: remove, StatTrak, float by exterior, pattern seed, equip.
 * The same actions as the setfloat/setpattern/setstattrak/removeskin/equip commands.
 */
public final class AdminSkinMenu extends Menu {

    private final PlayerProfile owner;
    private final String ownerName;
    private final SkinInstance instance;
    private final Runnable back;
    private final AdminActions actions;

    public AdminSkinMenu(CasesContext ctx, Player viewer, PlayerProfile owner, String ownerName, SkinInstance instance,
                         Runnable back) {
        super(ctx, viewer);
        this.owner = owner;
        this.ownerName = ownerName;
        this.instance = instance;
        this.back = back;
        this.actions = new AdminActions(ctx);
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override protected boolean canClick(int slot) {
        if (slot == GuiItems.SLOT_BACK || slot == GuiItems.SLOT_CENTER) return true;
        if (ctx.commerce().mutable(owner, instance)) return true;
        ctx.messages(viewer).send(viewer, "commerce.locked"); return false;
    }

    @Override
    protected Component title() {
        return ctx.messages(viewer).get("admin.manage.skin-title", Text.unparsed("player", ownerName));
    }

    @Override
    protected void build() {
        SkinDefinition def = ctx.catalog().skin(instance.skinId());
        if (def == null || owner.get(instance.id()) == null) {
            set(22, GuiItems.icon(ctx.messages(viewer), Material.BARRIER, "gui.inspect.missing"));
            set(GuiItems.SLOT_BACK, GuiItems.back(ctx.messages(viewer)), c -> back.run());
            return;
        }
        set(4, skinIcon(def));

        // StatTrak
        if (def.weapon().statTrak()) {
            set(19, GuiItems.glowing(GuiItems.icon(ctx.messages(viewer), Material.COMPARATOR, "admin.manage.stattrak",
                    Text.unparsed("state", instance.statTrak() ? "✔" : "✘")), instance.statTrak()), c -> {
                instance.setStatTrak(!instance.statTrak());
                save(false);
            });
        }

        // float by exterior: middle of the part of each tier this skin can reach
        if (def.hasWear()) {
            WearScale scale = ctx.catalog().wear();
            int slot = 20;
            for (WearTier tier : scale.tiers()) {
                if (slot > 24) {
                    break;
                }
                double lo = Math.max(tier.min(), def.minFloat());
                double hi = Math.min(tier.max(), def.maxFloat());
                boolean reachable = WearScale.reachable(tier, def.minFloat(), def.maxFloat()) && hi > lo;
                boolean current = scale.of(instance.floatValue()).id().equals(tier.id());
                double value = (lo + hi) / 2;
                set(slot++, GuiItems.glowing(GuiItems.icon(ctx.messages(viewer), reachable ? Material.PAPER : Material.GRAY_DYE,
                        "admin.manage.wear", Text.unparsed("wear", ctx.formatter(viewer).wearName(tier)),
                        Text.unparsed("float", Text.formatFloat(value, 4))), current), c -> {
                    if (!reachable) {
                        playError();
                        return;
                    }
                    instance.setFloatValue(value);
                    save(false);
                });
            }
        }

        // pattern seed
        int[] steps = {-10, -1, 0, 1, 10};
        Material[] icons = {Material.RED_STAINED_GLASS_PANE, Material.PINK_STAINED_GLASS_PANE, Material.ENDER_EYE,
                Material.LIME_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE};
        for (int i = 0; i < steps.length; i++) {
            int step = steps[i];
            String key = step == 0 ? "admin.manage.pattern-random" : "admin.manage.pattern-step";
            set(29 + i, GuiItems.icon(ctx.messages(viewer), icons[i], key, Text.unparsed("step", (step > 0 ? "+" : "") + step),
                    Text.unparsed("pattern", instance.pattern())), c -> {
                int min = ctx.catalog().patterns().seedMin();
                int max = ctx.catalog().patterns().seedMax();
                int range = max - min + 1;
                int next = step == 0 ? min + ctx.openings().roller().random().nextInt(range)
                        : min + Math.floorMod(instance.pattern() - min + step, range);
                instance.setPattern(next);
                save(true);
            });
        }

        // equip for the owner (only while they are online)
        Player online = Bukkit.getPlayer(owner.owner());
        if (online != null && ctx.profiles().get(online) == owner) {
            EquipSlot slot = def.isKnife() ? EquipSlot.KNIFE : EquipSlot.BOW;
            boolean equipped = owner.slotOf(instance.id()) != null;
            set(40, GuiItems.glowing(GuiItems.icon(ctx.messages(viewer), equipped ? Material.LIME_DYE : Material.IRON_SWORD,
                    equipped ? "admin.manage.unequip" : "admin.manage.equip"), equipped), c -> {
                if (equipped) {
                    ctx.knives().unequip(online, owner.slotOf(instance.id()));
                } else {
                    ctx.knives().equip(online, instance, slot);
                }
                render();
            });
        }

        set(GuiItems.SLOT_BACK, GuiItems.back(ctx.messages(viewer)), c -> {
            playClick();
            back.run();
        });
        set(GuiItems.SLOT_EXTRA, GuiItems.icon(ctx.messages(viewer), Material.LAVA_BUCKET, "admin.manage.remove"), c ->
                new ConfirmMenu(ctx, viewer, skinIcon(def), () -> {
                    actions.remove(owner, instance);
                    ctx.messages(viewer).send(viewer, "admin.manage.removed", Text.component("skin", ctx.formatter(viewer).fullName(def, instance)),
                            Text.unparsed("player", ownerName));
                    back.run();
                }, () -> new AdminSkinMenu(ctx, viewer, owner, ownerName, instance, back).open()).open());
    }

    private ItemStack skinIcon(SkinDefinition def) {
        return SkinIcons.icon(def, ctx.formatter(viewer).fullName(def, instance), ctx.formatter(viewer).lore(def, instance, true),
                ctx.settings(), false);
    }

    private void save(boolean reanalyse) {
        playClick();
        actions.saveRoll(owner, instance, reanalyse, () -> {
            if (viewer.getOpenInventory().getTopInventory().getHolder(false) == this) {
                render();
            }
        });
        render();
    }
}
