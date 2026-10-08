package dev.plattnericus.cases.commerce;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Receipt and changed inventory are written in the same player save. Never clear on SQL failure. */
public final class ItemReceipts {
    private final NamespacedKey key;
    public ItemReceipts(Plugin plugin) { key = new NamespacedKey(plugin, "emerald_receipts_v1"); }
    private List<String> entries(Player p) {
        var entries = p.getPersistentDataContainer().get(key, PersistentDataType.LIST.strings());
        return entries == null ? List.of() : entries;
    }
    public List<UUID> ids(Player p, String kind) {
        List<UUID> ids = new ArrayList<>();
        for (String value : entries(p)) {
            String[] parts = value.split(":");
            if (parts.length == 3 && parts[0].equals(kind)) try { ids.add(UUID.fromString(parts[1])); } catch (IllegalArgumentException ignored) { }
        }
        return List.copyOf(ids);
    }
    private String receipt(String kind, UUID id, long amount) { return kind + ":" + id + ":" + amount; }
    public boolean has(Player p, String kind, UUID id, long amount) { return entries(p).contains(receipt(kind, id, amount)); }
    public void save(Player p, String kind, UUID id, long amount) {
        var entries = new ArrayList<>(entries(p)); String value = receipt(kind, id, amount);
        if (!entries.contains(value)) entries.add(value);
        p.getPersistentDataContainer().set(key, PersistentDataType.LIST.strings(), entries);
        p.saveData();
    }
    public void clear(Player p, String kind, UUID id, long amount) {
        var entries = new ArrayList<>(entries(p)); entries.remove(receipt(kind, id, amount));
        if (entries.isEmpty()) p.getPersistentDataContainer().remove(key);
        else p.getPersistentDataContainer().set(key, PersistentDataType.LIST.strings(), entries);
        p.saveData();
    }
}
