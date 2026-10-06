package dev.plattnericus.cases.items;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Every persistent-data key the plugin writes. Items are identified by these, never by name. */
public final class PluginKeys {

    public final NamespacedKey itemType;
    public final NamespacedKey itemId;
    public final NamespacedKey signature;
    public final NamespacedKey testItem;
    public final NamespacedKey menuToken;
    public final NamespacedKey knifeInstance;
    public final NamespacedKey knifeOwner;
    public final NamespacedKey origName;
    public final NamespacedKey origLore;
    public final NamespacedKey origGlint;
    public final NamespacedKey origModel;
    public final NamespacedKey knifeState;
    public final NamespacedKey journal;
    public final NamespacedKey displayEntity;
    public final NamespacedKey shopVillager;

    public PluginKeys(Plugin plugin) {
        itemType = new NamespacedKey(plugin, "item_type");
        itemId = new NamespacedKey(plugin, "item_id");
        signature = new NamespacedKey(plugin, "sig");
        testItem = new NamespacedKey(plugin, "test");
        menuToken = new NamespacedKey(plugin, "skin_menu_token");
        knifeInstance = new NamespacedKey(plugin, "knife_instance");
        knifeOwner = new NamespacedKey(plugin, "knife_owner");
        origName = new NamespacedKey(plugin, "orig_name");
        origLore = new NamespacedKey(plugin, "orig_lore");
        origGlint = new NamespacedKey(plugin, "orig_glint");
        origModel = new NamespacedKey(plugin, "orig_model");
        knifeState = new NamespacedKey(plugin, "knife_state");
        journal = new NamespacedKey(plugin, "pending_rewards");
        displayEntity = new NamespacedKey(plugin, "display");
        shopVillager = new NamespacedKey(plugin, "shop_villager");
    }
}
