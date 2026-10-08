package dev.plattnericus.cases.profile;

import dev.plattnericus.cases.items.PluginKeys;
import dev.plattnericus.cases.skin.InstanceCodec;
import dev.plattnericus.cases.skin.SkinInstance;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Consumption and reward receipt share the player save. SQL replay is idempotent; storage durability still depends on Minecraft and the filesystem. */
public final class PendingJournal {

    private final PluginKeys keys;
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();
    private record Entry(int version, String skin, dev.plattnericus.cases.storage.OpeningRecord opening) { }

    public PendingJournal(PluginKeys keys) {
        this.keys = keys;
    }

    public void add(Player player, SkinInstance instance) {
        List<String> entries = new ArrayList<>(read(player));
        entries.add(InstanceCodec.encode(instance));
        write(player, entries);
    }

    public void add(Player player, SkinInstance instance, dev.plattnericus.cases.storage.OpeningRecord opening) {
        List<String> entries = new ArrayList<>(read(player));
        entries.add(GSON.toJson(new Entry(2, InstanceCodec.encode(instance), opening))); write(player, entries);
    }
    private Entry envelope(String raw) {
        try { Entry entry = GSON.fromJson(raw, Entry.class); return entry != null && entry.version() == 2 && entry.skin() != null ? entry : null; }
        catch (com.google.gson.JsonParseException error) { return null; }
    }
    public dev.plattnericus.cases.storage.OpeningRecord opening(Player player, UUID instanceId) {
        for (String raw : read(player)) {
            Entry entry = envelope(raw); if (entry == null) continue;
            SkinInstance instance = InstanceCodec.decode(entry.skin());
            if (instance != null && instance.id().equals(instanceId) && instance.owner().equals(player.getUniqueId()) && entry.opening() != null
                    && entry.opening().owner().equals(instance.owner()) && entry.opening().instanceId().equals(instanceId)) return entry.opening();
        }
        return null;
    }

    public void remove(Player player, UUID instanceId) {
        List<String> entries = new ArrayList<>(read(player));
        String needle = instanceId.toString();
        entries.removeIf(e -> e.contains(needle));
        write(player, entries);
    }

    public List<SkinInstance> entries(Player player) {
        List<SkinInstance> out = new ArrayList<>();
        for (String raw : read(player)) {
            Entry entry = envelope(raw);
            SkinInstance decoded = InstanceCodec.decode(entry == null ? raw : entry.skin());
            if (decoded != null && decoded.owner().equals(player.getUniqueId())) {
                out.add(decoded);
            }
        }
        return out;
    }

    private List<String> read(Player player) {
        List<String> list = player.getPersistentDataContainer().get(keys.journal, PersistentDataType.LIST.strings());
        return list == null ? List.of() : list;
    }

    private void write(Player player, List<String> entries) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (entries.isEmpty()) {
            pdc.remove(keys.journal);
        } else {
            pdc.set(keys.journal, PersistentDataType.LIST.strings(), entries);
        }
    }
}
