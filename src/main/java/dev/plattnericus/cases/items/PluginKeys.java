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
    public final NamespacedKey origModelData;
    public final NamespacedKey addedProfile;
    public final NamespacedKey journal;
    public final NamespacedKey displayEntity;
    public final NamespacedKey shopVillager;
    public final NamespacedKey shopLink;
    public final NamespacedKey shopWander;
    public final NamespacedKey shopChunk;
    public final NamespacedKey shopVillages;
    public final NamespacedKey shopVillageCount;

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
        origModelData = new NamespacedKey(plugin, "orig_model_data");
        addedProfile = new NamespacedKey(plugin, "added_profile");
        journal = new NamespacedKey(plugin, "pending_rewards");
        displayEntity = new NamespacedKey(plugin, "display");
        shopVillager = new NamespacedKey(plugin, "shop_villager");
        shopLink = new NamespacedKey(plugin, "shop_link");
        shopWander = new NamespacedKey(plugin, "shop_wander");
        shopChunk = new NamespacedKey(plugin, "shop_chunk_checked");
        shopVillages = new NamespacedKey(plugin, "shop_villages_seen");
        shopVillageCount = new NamespacedKey(plugin, "shop_village_count");
    }
}
