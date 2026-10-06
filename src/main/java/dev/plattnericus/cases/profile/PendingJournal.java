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

/**
 * Crash-safety journal stored in the player's own data file. When a case is opened, the removed
 * case/key and the rolled reward are written to the same file in one save, so after any crash
 * exactly one of two states exists on disk: "case still there, no reward" or "case gone, reward
 * journaled". The journal is replayed into the database (idempotently) on the next login.
 */
public final class PendingJournal {

    private final PluginKeys keys;

    public PendingJournal(PluginKeys keys) {
        this.keys = keys;
    }

    public void add(Player player, SkinInstance instance) {
        List<String> entries = new ArrayList<>(read(player));
        entries.add(InstanceCodec.encode(instance));
        write(player, entries);
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
            SkinInstance decoded = InstanceCodec.decode(raw);
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
