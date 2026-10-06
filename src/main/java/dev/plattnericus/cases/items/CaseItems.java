package dev.plattnericus.cases.items;

import dev.plattnericus.cases.catalog.CaseDefinition;
import dev.plattnericus.cases.catalog.KeyDefinition;
import dev.plattnericus.cases.config.Messages;
import dev.plattnericus.cases.config.PluginSettings;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Creates, identifies, counts and consumes case and key items. */
public final class CaseItems {

    public static final String TYPE_CASE = "case";
    public static final String TYPE_KEY = "key";

    /** What a plugin item is. {@code test} marks admin test items. */
    public record Identity(String type, String id, boolean test) {
    }

    /** Original flags are retained separately so refunds return exactly the consumed item types. */
    public record Consumption(boolean caseTest, boolean keyTest) {
        public boolean test() { return caseTest || keyTest; }
    }

    private final PluginKeys keys;
    private final ItemSigner signer;
    private final Supplier<PluginSettings> settings;
    private final Supplier<Messages> messages;

    public CaseItems(PluginKeys keys, ItemSigner signer, Supplier<PluginSettings> settings, Supplier<Messages> messages) {
        this.keys = keys;
        this.signer = signer;
        this.settings = settings;
        this.messages = messages;
    }

    public ItemStack caseItem(CaseDefinition def, int amount, boolean test) {
        return caseItem(def, amount, test, messages.get());
    }

    public ItemStack caseItem(CaseDefinition def, int amount, boolean test, Messages m) {
        PluginSettings s = settings.get();
        ItemStack item = new ItemStack(s.items().caseMaterial(), Math.max(1, Math.min(64, amount)));
        item.editMeta(meta -> {
            meta.displayName(m.item("items.case.name", Text.unparsed("name", def.name()), Text.color("case_color", def.color())));
            List<Component> lore = new ArrayList<>();
            for (String line : def.description()) {
                lore.add(Text.item("<gray>" + Text.escape(line)));
            }
            lore.addAll(m.itemList("items.case.lore", Text.unparsed("count", def.size())));
            if (test) {
                lore.add(m.item("items.test-marker"));
            }
            meta.lore(lore);
            model(meta, s, s.items().caseModel(), "case/" + def.id());
            mark(meta.getPersistentDataContainer(), TYPE_CASE, def.id(), test);
        });
        return item;
    }

    public ItemStack keyItem(KeyDefinition def, int amount, boolean test) {
        return keyItem(def, amount, test, messages.get());
    }

    public ItemStack keyItem(KeyDefinition def, int amount, boolean test, Messages m) {
        PluginSettings s = settings.get();
        ItemStack item = new ItemStack(s.items().keyMaterial(), Math.max(1, Math.min(64, amount)));
        item.editMeta(meta -> {
            meta.displayName(m.item("items.key.name", Text.unparsed("name", m.label("catalog.key." + def.id(), def.name())), Text.color("key_color", def.color())));
            List<Component> lore = new ArrayList<>();
            for (String line : def.lore()) {
                lore.add(Text.item("<gray>" + Text.escape(line)));
            }
            lore.addAll(m.itemList("items.key.lore"));
            if (test) {
                lore.add(m.item("items.test-marker"));
            }
            meta.lore(lore);
            model(meta, s, s.items().keyModel(), "key/" + def.id());
            mark(meta.getPersistentDataContainer(), TYPE_KEY, def.id(), test);
        });
        return item;
    }

    /** Menu icon of a case: same look as the item, but without any marker, so it is never a valid case. */
    public ItemStack caseIcon(CaseDefinition def) {
        return caseIcon(def, messages.get());
    }

    public ItemStack caseIcon(CaseDefinition def, Messages m) {
        ItemStack item = caseItem(def, 1, false, m);
        item.editMeta(meta -> clearMarks(meta.getPersistentDataContainer()));
        return item;
    }

    public ItemStack keyIcon(KeyDefinition def) {
        return keyIcon(def, messages.get());
    }

    public ItemStack keyIcon(KeyDefinition def, Messages m) {
        ItemStack item = keyItem(def, 1, false, m);
        item.editMeta(meta -> clearMarks(meta.getPersistentDataContainer()));
        return item;
    }

    /** Updates text only; signatures, amounts and all unrelated metadata remain intact. */
    public void refreshLanguage(Player player, dev.plattnericus.cases.catalog.Catalog catalog) {
        Messages m = messages.get().forAudience(player);
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            Identity identity = identify(stack);
            if (identity == null) {
                continue;
            }
            ItemStack translated = null;
            if (TYPE_CASE.equals(identity.type())) {
                CaseDefinition def = catalog.caseDefinition(identity.id());
                if (def != null) translated = caseItem(def, stack.getAmount(), identity.test(), m);
            } else if (TYPE_KEY.equals(identity.type())) {
                KeyDefinition def = catalog.key(identity.id());
                if (def != null) translated = keyItem(def, stack.getAmount(), identity.test(), m);
            }
            if (translated != null) {
                ItemMeta text = translated.getItemMeta();
                ItemMeta current = stack.getItemMeta();
                if (!java.util.Objects.equals(text.displayName(), current.displayName())
                        || !java.util.Objects.equals(text.lore(), current.lore())) {
                    current.displayName(text.displayName());
                    current.lore(text.lore());
                    stack.setItemMeta(current);
                    player.getInventory().setItem(i, stack);
                }
            }
        }
    }

    private void clearMarks(PersistentDataContainer pdc) {
        pdc.remove(keys.itemType);
        pdc.remove(keys.itemId);
        pdc.remove(keys.signature);
        pdc.remove(keys.testItem);
    }

    /**
     * Brings the item model of every case/key item in the inventory in line with the current
     * settings (e.g. after resource-pack.enabled was switched). Only touches items that differ.
     */
    public void refreshModels(Player player) {
        PluginSettings s = settings.get();
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            Identity identity = identify(stack);
            if (identity == null) {
                continue;
            }
            boolean isCase = identity.type().equals(TYPE_CASE);
            NamespacedKey wanted = modelKey(s, isCase ? s.items().caseModel() : s.items().keyModel(),
                    (isCase ? "case/" : "key/") + identity.id());
            NamespacedKey current = stack.getItemMeta().hasItemModel() ? stack.getItemMeta().getItemModel() : null;
            if (java.util.Objects.equals(wanted, current)) {
                continue;
            }
            stack.editMeta(meta -> meta.setItemModel(wanted));
            player.getInventory().setItem(i, stack);
        }
    }

    private static NamespacedKey modelKey(PluginSettings s, String vanillaModel, String packPath) {
        if (s.resourcePack().enabled()) {
            return new NamespacedKey(s.resourcePack().namespace(), packPath);
        }
        return vanillaModel == null || vanillaModel.isBlank() ? null : NamespacedKey.fromString(vanillaModel);
    }

    private void model(ItemMeta meta, PluginSettings s, String vanillaModel, String packPath) {
        NamespacedKey key = null;
        if (s.resourcePack().enabled()) {
            key = new NamespacedKey(s.resourcePack().namespace(), packPath);
        } else if (vanillaModel != null && !vanillaModel.isBlank()) {
            key = NamespacedKey.fromString(vanillaModel);
        }
        if (key != null) {
            meta.setItemModel(key);
        }
    }

    private void mark(PersistentDataContainer pdc, String type, String id, boolean test) {
        pdc.set(keys.itemType, PersistentDataType.STRING, type);
        pdc.set(keys.itemId, PersistentDataType.STRING, id);
        if (test) {
            pdc.set(keys.testItem, PersistentDataType.BYTE, (byte) 1);
        }
        pdc.set(keys.signature, PersistentDataType.STRING, signer.sign(type, id, test));
    }

    /** @return identity of a genuine plugin item, or null for anything else (including forged items). */
    public Identity identify(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        var pdc = item.getPersistentDataContainer();
        String type = pdc.get(keys.itemType, PersistentDataType.STRING);
        String id = pdc.get(keys.itemId, PersistentDataType.STRING);
        if (type == null || id == null) {
            return null;
        }
        boolean test = pdc.has(keys.testItem, PersistentDataType.BYTE);
        if (!signer.verify(type, id, test, pdc.get(keys.signature, PersistentDataType.STRING))) {
            return null;
        }
        return new Identity(type, id, test);
    }

    /** True for any item carrying plugin markers, valid or not (used to block crafting etc.). */
    public boolean isMarked(ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(keys.itemType, PersistentDataType.STRING);
    }

    public int count(Player player, String type, String id) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            Identity identity = identify(stack);
            if (identity != null && identity.type().equals(type) && identity.id().equals(id)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Whether the case stack the player would consume next is a test item. */
    public boolean nextIsTest(Player player, String type, String id) {
        int slot = find(player.getInventory(), type, id);
        return slot >= 0 && identify(player.getInventory().getItem(slot)).test();
    }

    /**
     * Removes exactly one case and one key in a single step. Either both are removed or nothing is.
     *
     * @return whether either consumed item was a test item, or null if the player lacks either item
     */
    public Boolean consumeCaseAndKey(Player player, String caseId, String keyId) {
        Consumption consumed = consumePair(player, caseId, keyId);
        return consumed == null ? null : consumed.test();
    }

    public Consumption consumePair(Player player, String caseId, String keyId) {
        PlayerInventory inv = player.getInventory();
        int caseSlot = find(inv, TYPE_CASE, caseId);
        int keySlot = find(inv, TYPE_KEY, keyId);
        if (caseSlot < 0 || keySlot < 0) {
            return null;
        }
        Consumption consumed = new Consumption(identify(inv.getItem(caseSlot)).test(), identify(inv.getItem(keySlot)).test());
        decrement(inv, caseSlot);
        decrement(inv, keySlot);
        return consumed;
    }

    private int find(PlayerInventory inv, String type, String id) {
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            Identity identity = identify(contents[i]);
            if (identity != null && identity.type().equals(type) && identity.id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static void decrement(PlayerInventory inv, int slot) {
        ItemStack stack = inv.getItem(slot);
        if (stack == null) {
            return;
        }
        if (stack.getAmount() <= 1) {
            inv.setItem(slot, null);
        } else {
            stack.setAmount(stack.getAmount() - 1);
            inv.setItem(slot, stack);
        }
    }

    /** Adds items; anything that does not fit is dropped at the player's feet. */
    public static void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> rest = player.getInventory().addItem(item);
        for (ItemStack left : rest.values()) {
            Item dropped = player.getWorld().dropItem(player.getLocation(), left);
            dropped.setOwner(player.getUniqueId());
        }
    }

    /** Whether the inventory can take the whole stack without dropping anything. */
    public static boolean fits(Player player, ItemStack item) {
        int remaining = item.getAmount();
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.isEmpty()) {
                remaining -= item.getMaxStackSize();
            } else if (slot.isSimilar(item)) {
                remaining -= Math.max(0, slot.getMaxStackSize() - slot.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }
}
