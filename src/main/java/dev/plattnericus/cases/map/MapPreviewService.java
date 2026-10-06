package dev.plattnericus.cases.map;

import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.config.Messages;
import dev.plattnericus.cases.config.PluginSettings;
import dev.plattnericus.cases.config.SoundBank;
import dev.plattnericus.cases.render.ArgbImage;
import dev.plattnericus.cases.render.MapCardComposer;
import dev.plattnericus.cases.render.RenderService;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Full-size skin preview without a resource pack: the rendered skin is drawn onto a map and the
 * map is shown in the player's hands through a client-side equipment update. The server inventory
 * is never touched, so nothing can be duplicated or lost; any interaction restores the real view.
 */
public final class MapPreviewService implements Listener {

    private record Session(Location origin, BukkitTask timeout, Runnable onEnd, List<Entity> entities, BukkitTask spin) {
    }

    private final Plugin plugin;
    private final MapViewPool pool;
    private final Supplier<RenderService> render;
    private final Supplier<PluginSettings> settings;
    private final Supplier<Messages> messages;
    private final Supplier<SoundBank> sounds;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Object> requests = new HashMap<>();
    private volatile byte[] errorCard;

    public MapPreviewService(Plugin plugin, MapViewPool pool, Supplier<RenderService> render, Supplier<PluginSettings> settings,
                             Supplier<Messages> messages, Supplier<SoundBank> sounds) {
        this.plugin = plugin;
        this.pool = pool;
        this.render = render;
        this.settings = settings;
        this.messages = messages;
        this.sounds = sounds;
    }

    public boolean isPreviewing(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /**
     * Renders asynchronously and shows the result. {@code onEnd} runs on the server thread when the
     * preview is closed by the player (not on quit).
     */
    public void show(Player player, SkinDefinition skin, int seed, double floatValue, long wearSeed,
                     Component title, Runnable onEnd) {
        end(player, false);
        UUID id = player.getUniqueId();
        Object ticket = new Object();
        requests.put(id, ticket);
        player.sendActionBar(messages.get().forAudience(player).get("preview.loading"));
        PluginSettings.Preview cfg = settings.get().preview();
        if (cfg.item() && settings.get().resourcePack().enabled()) {
            // the pack sprite is the skin itself; no rendering needed
            displayItem(player, skin, cfg, title, onEnd);
            requests.remove(id, ticket);
            return;
        }
        if (cfg.hologram()) {
            render.get().hologram(skin, seed, floatValue, wearSeed, cfg.hologramResolution()).whenComplete((rgb, error) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline() || requests.get(id) != ticket) {
                            return;
                        }
                        requests.remove(id, ticket);
                        if (error != null) {
                            plugin.getLogger().log(Level.WARNING, "Hologram for " + skin.id() + " #" + seed + " failed: "
                                    + rootMessage(error));
                            player.sendActionBar(Component.empty());
                            return;
                        }
                        displayHologram(player, rgb, cfg, title, onEnd);
                    }));
            return;
        }
        render.get().mapPreview(skin, seed, floatValue, wearSeed).whenComplete((bytes, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || requests.get(id) != ticket) {
                        return;
                    }
                    requests.remove(id, ticket);
                    byte[] pixels = bytes;
                    if (error != null) {
                        plugin.getLogger().log(Level.WARNING, "Preview for " + skin.id() + " #" + seed + " failed: "
                                + rootMessage(error) + " - showing fallback card");
                        pixels = errorCard();
                    }
                    display(player, pixels, title, onEnd);
                }));
    }

    private void display(Player player, byte[] pixels, Component title, Runnable onEnd) {
        MapView view = pool.acquire(player.getUniqueId());
        PreviewMapRenderer renderer = pool.renderer(view);
        renderer.setPixels(pixels);
        ItemStack map = new ItemStack(Material.FILLED_MAP);
        map.editMeta(MapMeta.class, meta -> {
            meta.setMapView(view);
            meta.displayName(Text.plainItem(title));
        });
        player.sendMap(view);
        Map<EquipmentSlot, ItemStack> fake = new EnumMap<>(EquipmentSlot.class);
        fake.put(EquipmentSlot.HAND, map);
        fake.put(EquipmentSlot.OFF_HAND, ItemStack.empty());
        player.sendEquipmentChange(player, fake);
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> end(player, true),
                settings.get().preview().durationSeconds() * 20L);
        sessions.put(player.getUniqueId(), new Session(player.getLocation(), timeout, onEnd, List.of(), null));
        sounds.get().play(player, "preview.open");
        player.sendActionBar(messages.get().forAudience(player).get("preview.hint"));
    }

    /**
     * Hologram mode: the card is drawn as text-display pixel art in front of the player. Every pixel is
     * a colored block glyph (8x8 with a 9px pitch, so pixels are square with a fine grid in between).
     * Only the viewer can see it.
     */
    private void displayHologram(Player player, int[] rgb, PluginSettings.Preview cfg, Component title, Runnable onEnd) {
        int res = cfg.hologramResolution();
        double pixel = 9 * 0.025;
        float scale = (float) (cfg.hologramSize() / (res * pixel));
        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().normalize();
        Location base = eye.clone().add(forward.multiply(cfg.hologramDistance())).add(0, -cfg.hologramSize() / 2, 0);
        Component image = HologramText.render(rgb, res);
        TextDisplay picture = base.getWorld().spawn(base, TextDisplay.class, d -> {
            hologramEntity(d);
            d.text(image);
            d.setLineWidth(100000);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale),
                    new AxisAngle4f()));
        });
        Location top = base.clone().add(0, cfg.hologramSize() + 0.05, 0);
        TextDisplay caption = base.getWorld().spawn(top, TextDisplay.class, d -> {
            hologramEntity(d);
            d.text(title);
            d.setBackgroundColor(Color.fromARGB(150, 10, 12, 16));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.5f, 0.5f, 0.5f),
                    new AxisAngle4f()));
        });
        player.showEntity(plugin, picture);
        player.showEntity(plugin, caption);
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> end(player, true), cfg.durationSeconds() * 20L);
        sessions.put(player.getUniqueId(), new Session(player.getLocation(), timeout, onEnd, List.of(picture, caption), null));
        sounds.get().play(player, "preview.open");
        player.sendActionBar(messages.get().forAudience(player).get("preview.hint"));
    }

    /**
     * Item mode: the skin item (resource-pack sprite) floats in front of the player and turns slowly.
     * Only the viewer can see it. Turning is done with display interpolation in 120 degree steps.
     */
    private void displayItem(Player player, SkinDefinition skin, PluginSettings.Preview cfg, Component title, Runnable onEnd) {
        end(player, false);
        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().normalize();
        Location at = eye.clone().add(forward.multiply(cfg.itemDistance()));
        at.setYaw(eye.getYaw() + 180);
        at.setPitch(0);
        float size = (float) cfg.itemSize();
        ItemStack icon = dev.plattnericus.cases.items.SkinIcons.icon(skin, title, List.of(), settings.get(), false);
        ItemDisplay item = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            previewEntity(d);
            d.setItemStack(icon);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(size, size, size),
                    new AxisAngle4f()));
        });
        TextDisplay caption = at.getWorld().spawn(at.clone().add(0, size * 0.62, 0), TextDisplay.class, d -> {
            previewEntity(d);
            d.setBillboard(Display.Billboard.CENTER);
            d.text(title);
            d.setBackgroundColor(Color.fromARGB(150, 10, 12, 16));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.45f, 0.45f, 0.45f),
                    new AxisAngle4f()));
        });
        player.showEntity(plugin, item);
        player.showEntity(plugin, caption);
        int[] step = {0};
        BukkitTask spin = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!item.isValid()) {
                return;
            }
            step[0]++;
            float angle = (float) Math.toRadians(step[0] * 120.0);
            item.setInterpolationDelay(0);
            item.setInterpolationDuration(40);
            item.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(angle, 0, 1, 0),
                    new Vector3f(size, size, size), new AxisAngle4f()));
        }, 2L, 40L);
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> end(player, true), cfg.durationSeconds() * 20L);
        sessions.put(player.getUniqueId(), new Session(player.getLocation(), timeout, onEnd, List.of(item, caption), spin));
        sounds.get().play(player, "preview.open");
        player.sendActionBar(messages.get().forAudience(player).get("preview.hint"));
    }

    private void previewEntity(Display d) {
        d.setPersistent(false);
        d.setVisibleByDefault(false);
        d.getPersistentDataContainer().set(new NamespacedKey(plugin, "display"), PersistentDataType.BYTE, (byte) 1);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(0.3f);
        d.setShadowRadius(0);
    }

    private void hologramEntity(TextDisplay d) {
        d.setPersistent(false);
        d.setVisibleByDefault(false);
        d.getPersistentDataContainer().set(new NamespacedKey(plugin, "display"), PersistentDataType.BYTE, (byte) 1);
        d.setBillboard(Display.Billboard.CENTER);
        d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        d.setShadowed(false);
        d.setSeeThrough(false);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(0.3f);
    }

    /** Restores the real hand items. {@code callback} decides whether the menu callback runs. */
    public void end(Player player, boolean callback) {
        boolean pending = requests.remove(player.getUniqueId()) != null;
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) {
            if (pending) player.sendActionBar(Component.empty());
            return;
        }
        session.timeout().cancel();
        if (session.spin() != null) {
            session.spin().cancel();
        }
        for (Entity e : session.entities()) {
            e.remove();
        }
        player.updateInventory();
        player.sendActionBar(Component.empty());
        if (callback && session.onEnd() != null && player.isOnline()) {
            session.onEnd().run();
        }
    }

    public void shutdown() {
        requests.clear();
        for (UUID id : sessions.keySet().toArray(UUID[]::new)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                end(p, false);
            }
        }
    }

    private byte[] errorCard() {
        byte[] card = errorCard;
        if (card == null) {
            ArgbImage empty = new ArgbImage(16, 16);
            ArgbImage composed = MapCardComposer.compose(empty, 0x9a3a3a, settings.get().rendering().map());
            card = render.get().engine().mapEncoder().apply(composed);
            errorCard = card;
        }
        return card;
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) {
            c = c.getCause();
        }
        return c.getMessage();
    }

    // ------------------------------------------------------------------ ending triggers

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            end(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (isPreviewing(event.getPlayer())) {
            event.setCancelled(true);
            end(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isPreviewing(event.getPlayer())) {
            event.setCancelled(true);
            end(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isPreviewing(event.getPlayer())) {
            event.setCancelled(true);
            end(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isPreviewing(event.getPlayer())) {
            event.setCancelled(true);
            end(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (requests.containsKey(player.getUniqueId())) end(player, false);
        Session session = sessions.get(player.getUniqueId());
        if (session != null) Bukkit.getScheduler().runTask(plugin, () -> {
            if (sessions.get(player.getUniqueId()) == session) end(player, true);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Session s = sessions.get(event.getPlayer().getUniqueId());
        if (s != null && event.hasChangedBlock()
                && (!s.origin().getWorld().equals(event.getTo().getWorld())
                || s.origin().distanceSquared(event.getTo()) > Math.pow(settings.get().preview().maxMove(), 2))) {
            end(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player p) {
            end(p, false);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        end(event.getPlayer(), false);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        end(event.getPlayer(), false);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        end(event.getEntity(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Session s = sessions.remove(id);
        if (s != null) {
            s.timeout().cancel();
            if (s.spin() != null) {
                s.spin().cancel();
            }
            for (Entity e : s.entities()) {
                e.remove();
            }
        }
        requests.remove(id);
        pool.release(id);
    }
}
