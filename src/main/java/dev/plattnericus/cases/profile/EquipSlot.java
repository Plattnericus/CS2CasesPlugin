package dev.plattnericus.cases.profile;

import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.config.PluginSettings;
import org.bukkit.Material;

import java.util.Locale;

/**
 * Where an owned skin can be equipped. Knives go on swords; weapon skins (any gun) go on bows and
 * crossbows. The id is what the database stores in the equipped table.
 */
public enum EquipSlot {
    KNIFE,
    BOW,
    CROSSBOW;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static EquipSlot byId(String id) {
        for (EquipSlot s : values()) {
            if (s.id().equalsIgnoreCase(id)) {
                return s;
            }
        }
        return null;
    }

    /** Whether this kind of skin fits the slot. */
    public boolean accepts(SkinDefinition def) {
        return this == KNIFE ? def.isKnife() : !def.weapon().category().isStarItem();
    }

    public boolean enabled(PluginSettings settings) {
        return switch (this) {
            case KNIFE -> true;
            case BOW -> settings.knives().bowSkins();
            case CROSSBOW -> settings.knives().crossbowSkins();
        };
    }

    /** The slot an item in the hand belongs to, or null if it carries no skins. */
    public static EquipSlot forItem(Material material, PluginSettings settings) {
        if (settings.knives().swordMaterials().contains(material)) {
            return KNIFE;
        }
        if (material == Material.BOW && settings.knives().bowSkins()) {
            return BOW;
        }
        if (material == Material.CROSSBOW && settings.knives().crossbowSkins()) {
            return CROSSBOW;
        }
        return null;
    }
}
