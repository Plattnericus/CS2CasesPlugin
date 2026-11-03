package dev.plattnericus.cases.items;

import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.config.PluginSettings;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Menu icons for skins: vanilla material per weapon, or the pack sprite when the pack is enabled. */
public final class SkinIcons {

    private SkinIcons() {
    }

    public static Material material(SkinDefinition def) {
        Material m = Material.matchMaterial(def.weapon().icon());
        return m == null || !m.isItem() ? Material.IRON_SWORD : m;
    }

    public static ItemStack icon(SkinDefinition def, Component name, List<Component> lore, PluginSettings settings, boolean glint) {
        ItemStack item = new ItemStack(material(def));
        item.editMeta(meta -> {
            meta.displayName(name);
            meta.lore(lore);
            if (settings.resourcePack().enabled()) {
                meta.setItemModel(new NamespacedKey(settings.resourcePack().namespace(), "skin/" + def.id()));
            }
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
            meta.setMaxStackSize(1);
        });
        hideVanillaTooltip(item);
        return item;
    }

    /** Menu icon for a skin that can be picked; a selected skin glows. Uses vanilla effects only. */
    public static ItemStack tradeIcon(SkinDefinition def, Component name, List<Component> lore, PluginSettings settings, boolean selected) {
        return icon(def, name, lore, settings, selected);
    }

    /** Hides attribute, enchantment and similar vanilla tooltip lines on menu icons. */
    public static void hideVanillaTooltip(ItemStack item) {
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.ENCHANTMENTS,
                        DataComponentTypes.UNBREAKABLE, DataComponentTypes.DYED_COLOR, DataComponentTypes.MAP_ID)
                .build());
    }
}
