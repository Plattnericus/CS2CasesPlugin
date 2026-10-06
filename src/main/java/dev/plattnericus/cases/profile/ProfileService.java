package dev.plattnericus.cases.profile;

import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.config.Messages;
import dev.plattnericus.cases.items.SkinFormatter;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.storage.SkinRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Loads a player's skins asynchronously on join, recovers interrupted openings and keeps the
 * profile in memory while the player is online. All mutations happen on the server thread and are
 * written through to the database immediately.
 */
public final class ProfileService implements Listener {

    private final Plugin plugin;
    private final SkinRepository repository;
    private final PendingJournal journal;
    private final Supplier<Messages> messages;
    private final Supplier<SkinFormatter> formatter;
    private final Function<String, SkinDefinition> skins;
    private final Map<UUID, PlayerProfile> profiles = new HashMap<>();
    private final Map<UUID, Long> revisions = new HashMap<>();
    private java.util.function.Predicate<UUID> locked = id -> false;
    private final List<Consumer<Player>> loadListeners = new ArrayList<>();

    public ProfileService(Plugin plugin, SkinRepository repository, PendingJournal journal, Supplier<Messages> messages,
                          Supplier<SkinFormatter> formatter, Function<String, SkinDefinition> skins) {
        this.plugin = plugin;
        this.repository = repository;
        this.journal = journal;
        this.messages = messages;
        this.formatter = formatter;
        this.skins = skins;
    }

    public PendingJournal journal() {
        return journal;
    }

    /** Runs after a profile finished loading (knife cosmetics, reserved slot...). */
    public void onLoad(Consumer<Player> listener) {
        loadListeners.add(listener);
    }

    public void lockCheck(java.util.function.Predicate<UUID> check) { locked = check; }

    public PlayerProfile get(Player player) {
        return profiles.get(player.getUniqueId());
    }

    public PlayerProfile get(UUID player) {
        return profiles.get(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        profiles.remove(event.getPlayer().getUniqueId());
    }

    public void load(Player player) {
        UUID id = player.getUniqueId();
        long revision = revisions.getOrDefault(id, 0L);
        // replay the journal first; insert-ignore makes this safe even if the rows already exist
        List<SkinInstance> journaled = journal.entries(player);
        CompletableFuture<Void> replay = CompletableFuture.completedFuture(null);
        for (SkinInstance entry : journaled) {
            replay = replay.thenCompose(v -> repository.insert(entry));
        }
        CompletableFuture<List<SkinInstance>> skinsFuture = replay.thenCompose(v -> repository.loadActive(id));
        CompletableFuture<java.util.Map<String, UUID>> knifeFuture = replay.thenCompose(v -> repository.equippedAll(id));
        skinsFuture.thenCombine(knifeFuture, (list, knife) -> new Object[]{list, knife}).whenComplete((result, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (revision != revisions.getOrDefault(id, 0L)) { load(player); return; }
                    if (error != null) {
                        plugin.getLogger().log(Level.SEVERE, "Could not load skins of " + player.getName(), error);
                        messages.get().send(player, "profile.load-failed");
                        return;
                    }
                    for (SkinInstance entry : journaled) {
                        journal.remove(player, entry.id());
                    }
                    @SuppressWarnings("unchecked")
                    List<SkinInstance> list = (List<SkinInstance>) result[0];
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, UUID> equipped = (java.util.Map<String, UUID>) result[1];
                    apply(player, list, equipped);
                }));
    }

    private void apply(Player player, List<SkinInstance> list, java.util.Map<String, UUID> equipped) {
        PlayerProfile profile = new PlayerProfile(player.getUniqueId());
        List<SkinInstance> recovered = new ArrayList<>();
        for (SkinInstance s : list) {
            profile.put(s);
            if (s.status() == SkinInstance.Status.PENDING) {
                recovered.add(s);
            }
        }
        equipped.forEach((slotId, instance) -> {
            EquipSlot slot = EquipSlot.byId(slotId);
            if (slot != null && profile.get(instance) != null) {
                profile.setEquipped(slot, instance);
            }
        });
        profiles.put(player.getUniqueId(), profile);
        if (!recovered.isEmpty()) {
            repository.finalizePending(player.getUniqueId());
            for (SkinInstance s : recovered) {
                s.setStatus(SkinInstance.Status.OWNED);
                SkinDefinition def = skins.apply(s.skinId());
                if (def != null) {
                    messages.get().send(player, "profile.recovered",
                            dev.plattnericus.cases.util.Text.component("skin", formatter.get().forAudience(player).fullName(def, s)));
                }
            }
            plugin.getLogger().info("Recovered " + recovered.size() + " interrupted case reward(s) for " + player.getName());
        }
        for (Consumer<Player> l : loadListeners) {
            l.accept(player);
        }
    }

    /**
     * Read-only snapshot of any player's skins (for viewing other inventories). Online players use the
     * live profile; offline players are loaded from the database and never written back.
     */
    public CompletableFuture<PlayerProfile> snapshot(UUID owner) {
        PlayerProfile live = profiles.get(owner);
        if (live != null) {
            return CompletableFuture.completedFuture(live);
        }
        return repository.loadActive(owner).thenCombine(repository.equippedAll(owner), (list, equipped) -> {
            PlayerProfile p = new PlayerProfile(owner);
            for (SkinInstance s : list) {
                if (s.status() == SkinInstance.Status.OWNED) {
                    p.put(s);
                }
            }
            equipped.forEach((slotId, instance) -> {
                EquipSlot slot = EquipSlot.byId(slotId);
                if (slot != null) {
                    p.setEquipped(slot, instance);
                }
            });
            return p;
        });
    }

    public void setFavorite(Player player, SkinInstance instance, boolean favorite) {
        PlayerProfile profile = get(player);
        if (profile == null || profile.get(instance.id()) != instance || locked.test(instance.id())
                || instance.status() != SkinInstance.Status.OWNED) {
            return;
        }
        instance.setFavorite(favorite);
        repository.setFavorite(instance.id(), favorite);
    }

    /** Adds an instance that is already stored in the database. */
    public void addLoaded(UUID owner, SkinInstance instance) {
        revisions.merge(owner, 1L, Long::sum);
        PlayerProfile profile = profiles.get(owner);
        if (profile != null) {
            profile.put(instance);
        }
    }

    public void removeLoaded(UUID owner, UUID instance) {
        revisions.merge(owner, 1L, Long::sum);
        PlayerProfile profile = profiles.get(owner);
        if (profile != null) profile.remove(instance);
    }
}
