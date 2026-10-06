package dev.plattnericus.cases.knife;

import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.config.Messages;
import dev.plattnericus.cases.config.PluginSettings;
import dev.plattnericus.cases.items.PluginKeys;
import dev.plattnericus.cases.items.SkinFormatter;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Applies the equipped knife's identity to a sword and restores it later. Only name, lore, glint
 * and item model are touched; damage, enchantments, durability and every other component stay
 * exactly as they were. The original cosmetic values are stored on the item itself.
 */
public final class KnifeCosmetics {

    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();

    private final PluginKeys keys;

    public KnifeCosmetics(PluginKeys keys) {
        this.keys = keys;
    }

    public UUID instanceOf(ItemStack item) {
        String v = item == null || item.isEmpty() ? null
                : item.getPersistentDataContainer().get(keys.knifeInstance, PersistentDataType.STRING);
        return parse(v);
    }

    public UUID ownerOf(ItemStack item) {
        String v = item == null || item.isEmpty() ? null
                : item.getPersistentDataContainer().get(keys.knifeOwner, PersistentDataType.STRING);
        return parse(v);
    }

    public boolean isCosmetic(ItemStack item) {
        return instanceOf(item) != null;
    }

    /**
     * Applies (or refreshes) the knife look and returns the item. If the item already shows exactly
     * this state, it is returned untouched so no needless slot update reaches the client.
     */
    public ItemStack apply(ItemStack item, SkinDefinition def, SkinInstance inst, SkinFormatter formatter,
                           Messages messages, PluginSettings settings, long catalogVersion) {
        String state = inst.id() + ":" + inst.kills() + ":" + inst.statTrak() + ":" + inst.floatValue() + ":"
                + inst.pattern() + ":" + def.id() + ":" + catalogVersion + ":" + settings.resourcePack().enabled()
                + ":" + settings.knives().glint() + ":" + messages.language();
        if (state.equals(item.getPersistentDataContainer().get(keys.knifeState, PersistentDataType.STRING))) {
            return item;
        }
        ItemStack target = isCosmetic(item) ? strip(item) : item;
        ItemMeta meta = target.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.origName, PersistentDataType.STRING, meta.hasDisplayName() ? GSON.serialize(meta.displayName()) : "");
        List<Component> originalLore = meta.hasLore() ? meta.lore() : List.of();
        pdc.set(keys.origLore, PersistentDataType.LIST.strings(), originalLore.stream().map(GSON::serialize).toList());
        byte glint = meta.hasEnchantmentGlintOverride() ? (byte) (Boolean.TRUE.equals(meta.getEnchantmentGlintOverride()) ? 1 : 2) : 0;
        pdc.set(keys.origGlint, PersistentDataType.BYTE, glint);
        pdc.set(keys.origModel, PersistentDataType.STRING, meta.hasItemModel() ? meta.getItemModel().asString() : "");

        meta.displayName(formatter.name(def, inst));
        List<Component> lore = new ArrayList<>(formatter.lore(def, inst, false));
        lore.add(messages.item("knife.sword-footer", Text.component("base", Text.materialName(target.getType()))));
        lore.addAll(originalLore);
        meta.lore(lore);
        if (settings.knives().glint()) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (settings.resourcePack().enabled()) {
            meta.setItemModel(new NamespacedKey(settings.resourcePack().namespace(), "skin/" + def.id()));
        }
        pdc.set(keys.knifeInstance, PersistentDataType.STRING, inst.id().toString());
        pdc.set(keys.knifeOwner, PersistentDataType.STRING, inst.owner().toString());
        pdc.set(keys.knifeState, PersistentDataType.STRING, state);
        target.setItemMeta(meta);
        return target;
    }

    /** Restores the original name, lore, glint and model. */
    public ItemStack strip(ItemStack item) {
        if (!isCosmetic(item)) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String name = pdc.get(keys.origName, PersistentDataType.STRING);
        meta.displayName(name == null || name.isEmpty() ? null : GSON.deserialize(name));
        List<String> lore = pdc.get(keys.origLore, PersistentDataType.LIST.strings());
        meta.lore(lore == null || lore.isEmpty() ? null : lore.stream().map(GSON::deserialize).toList());
        Byte glint = pdc.get(keys.origGlint, PersistentDataType.BYTE);
        meta.setEnchantmentGlintOverride(glint == null || glint == 0 ? null : glint == 1);
        String model = pdc.get(keys.origModel, PersistentDataType.STRING);
        meta.setItemModel(model == null || model.isEmpty() ? null : NamespacedKey.fromString(model));
        for (NamespacedKey k : new NamespacedKey[]{keys.knifeInstance, keys.knifeOwner, keys.origName, keys.origLore,
                keys.origGlint, keys.origModel, keys.knifeState}) {
            pdc.remove(k);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static UUID parse(String v) {
        if (v == null) {
            return null;
        }
        try {
            return UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
