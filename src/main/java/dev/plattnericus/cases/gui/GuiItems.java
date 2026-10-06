package dev.plattnericus.cases.gui;

import dev.plattnericus.cases.config.Messages;
import dev.plattnericus.cases.items.SkinIcons;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Shared icon builders so every menu looks the same. */
public final class GuiItems {

    /** Fixed navigation positions in 6-row menus. */
    public static final int SLOT_BACK = 45;
    public static final int SLOT_SORT = 47;
    public static final int SLOT_PREV = 48;
    public static final int SLOT_CENTER = 49;
    public static final int SLOT_NEXT = 50;
    public static final int SLOT_FILTER = 51;
    public static final int SLOT_EXTRA = 53;

    /** Content slots of 6-row list menus (rows 1-4, 36 entries). */
    public static final int[] CONTENT = range(9, 45);

    private GuiItems() {
    }

    private static int[] range(int from, int to) {
        int[] r = new int[to - from];
        for (int i = 0; i < r.length; i++) {
            r[i] = from + i;
        }
        return r;
    }

    public static ItemStack filler(Material material) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.setHideTooltip(true));
        return item;
    }

    public static ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name);
            meta.lore(lore);
        });
        SkinIcons.hideVanillaTooltip(item);
        return item;
    }

    public static ItemStack icon(Messages m, Material material, String key, TagResolver... r) {
        return icon(material, m.item(key + ".name", r), m.itemList(key + ".lore", r));
    }

    public static ItemStack playerHead(Messages m, Player player, String key, TagResolver... r) {
        ItemStack head = icon(m, Material.PLAYER_HEAD, key, r);
        var profile = player.getPlayerProfile();
        // UUID and name make this a static snapshot, including texture-less offline players.
        head.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile().uuid(player.getUniqueId())
                .name(player.getName()).addProperties(profile.getProperties()).build());
        return head;
    }

    public static ItemStack glowing(ItemStack item, boolean glow) {
        if (glow) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return item;
    }

    public static ItemStack back(Messages m) {
        return icon(m, Material.ARROW, "gui.back");
    }

    public static ItemStack close(Messages m) {
        return icon(m, Material.BARRIER, "gui.close");
    }

    public static ItemStack previous(Messages m, int page, int pages) {
        return icon(m, page > 0 ? Material.SPECTRAL_ARROW : Material.GRAY_DYE, "gui.previous",
                dev.plattnericus.cases.util.Text.unparsed("page", page + 1), dev.plattnericus.cases.util.Text.unparsed("pages", pages));
    }

    public static ItemStack next(Messages m, int page, int pages) {
        return icon(m, page + 1 < pages ? Material.SPECTRAL_ARROW : Material.GRAY_DYE, "gui.next",
                dev.plattnericus.cases.util.Text.unparsed("page", page + 1), dev.plattnericus.cases.util.Text.unparsed("pages", pages));
    }

    public static int pages(int entries, int perPage) {
        return Math.max(1, (entries + perPage - 1) / perPage);
    }
}
