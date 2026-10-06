package dev.plattnericus.cases.config;

import dev.plattnericus.cases.render.MapCardStyle;
import dev.plattnericus.cases.render.RenderSettings;
import dev.plattnericus.cases.util.Colors;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Typed, immutable view of config.yml. Invalid values fall back to defaults with a warning. */
public record PluginSettings(
        boolean debug,
        Storage storage,
        Rendering rendering,
        Opening opening,
        Items items,
        ReservedSlot reservedSlot,
        Knives knives,
        Inspect inspect,
        Preview preview,
        ResourcePack resourcePack,
        StatTrak statTrak,
        Display display,
        SkinInventory skinInventory) {

    /** "world" shows the skin inventory as a floating wall of item displays, "gui" as inventory menu. */
    public record SkinInventory(String display, int columns, int rows, double distance, double spacing,
                                double itemScale, int timeoutSeconds) {

        public boolean world() {
            return display.equals("world");
        }
    }

    public record Display(int floatDecimals, int inspectDecimals, String dateFormat, boolean logOpenings) {
    }

    public record Storage(String type, String sqliteFile, String host, int port, String database,
                          String user, String password, String tablePrefix) {
    }

    public record Rendering(int threads, int previewCache, int reportCache, double floatBucket,
                            RenderSettings wear, MapCardStyle map, double ditherStrength) {
    }

    public record Opening(int durationTicks, String easing, int reelLength, int revealHoldTicks, int goldPauseTicks,
                          boolean knifeAutoPreview, Material markerTop, Material markerBottom, Material filler,
                          boolean blockCommands, boolean broadcastRare, String display, WorldReel world,
                          boolean speechBubble, int speechBubbleTicks) {

        /** "world" shows the reel to everyone with item displays, "gui" uses the inventory roulette. */
        public boolean worldDisplay() {
            return display.equals("world");
        }
    }

    /** Public in-world reel built from display entities. */
    public record WorldReel(double distance, double height, int visibleItems, double spacing, double itemScale,
                            int holdTicks, double viewRange) {
    }

    public record Items(Material caseMaterial, String caseModel, Material keyMaterial, String keyModel) {
    }

    public record ReservedSlot(boolean enabled, int slot, Material material, String model) {
    }

    public record Knives(Set<Material> swordMaterials, boolean glint, boolean stripOnDrop, boolean bowSkins,
                         boolean crossbowSkins) {
    }

    public record Inspect(int cooldownTicks, boolean sneakRightClick, boolean swapHandKey, boolean hideHeldItem, boolean actionBar,
                          boolean revealPreview) {
    }

    public record Preview(int durationSeconds, double maxMove, int showcaseSeed, double showcaseFloat, String mode,
                          int hologramResolution, double hologramSize, double hologramDistance,
                          double itemSize, double itemDistance) {

        /** "item" shows the resource-pack sprite as a floating, turning item display. */
        public boolean item() {
            return mode.equals("item");
        }


        /** "hologram" shows a text-display image in front of the player instead of a map in the hands. */
        public boolean hologram() {
            return mode.equals("hologram");
        }
    }

    /**
     * {@code enabled}: items use the pack's models. It is forced on while the plugin distributes the
     * pack itself, because every player is then asked to load it.
     */
    public record ResourcePack(boolean enabled, String namespace, Distribution distribution) {
    }

    public record Distribution(boolean enabled, String bindAddress, int port, String publicUrl, boolean required,
                               String prompt) {
    }

    public record StatTrak(boolean countMobs) {
    }

    public static PluginSettings load(FileConfiguration c, Consumer<String> warn) {
        Storage storage = new Storage(
                c.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT),
                c.getString("storage.sqlite-file", "data.db"),
                c.getString("storage.mysql.host", "localhost"),
                c.getInt("storage.mysql.port", 3306),
                c.getString("storage.mysql.database", "mccases"),
                c.getString("storage.mysql.user", "root"),
                c.getString("storage.mysql.password", ""),
                c.getString("storage.table-prefix", "pc_"));
        if (!storage.tablePrefix().matches("[a-zA-Z0-9_]{0,16}")) {
            warn.accept("config.yml: storage.table-prefix may only contain letters, digits and _ - using pc_");
            storage = new Storage(storage.type(), storage.sqliteFile(), storage.host(), storage.port(), storage.database(),
                    storage.user(), storage.password(), "pc_");
        }

        RenderSettings wear = new RenderSettings(
                c.getDouble("rendering.wear.exponent", 0.9),
                c.getDouble("rendering.wear.max", 0.72),
                c.getDouble("rendering.wear.edge-weight", 0.62),
                c.getDouble("rendering.wear.scratch-weight", 0.58),
                c.getDouble("rendering.wear.grime", 0.22));
        MapCardStyle d = MapCardStyle.defaults();
        MapCardStyle map = new MapCardStyle(
                color(c, "rendering.map.background-top", d.backgroundTop(), warn),
                color(c, "rendering.map.background-bottom", d.backgroundBottom(), warn),
                clamp(c.getInt("rendering.map.padding", d.padding()), 0, 40),
                c.getDouble("rendering.map.glow", d.glow()),
                clamp(c.getInt("rendering.map.accent-bar", d.accentBar()), 0, 12),
                c.getDouble("rendering.map.contrast", d.contrast()),
                c.getDouble("rendering.map.saturation", d.saturation()));
        Rendering rendering = new Rendering(
                clamp(c.getInt("rendering.threads", 2), 1, 8),
                clamp(c.getInt("rendering.preview-cache", 384), 16, 8192),
                clamp(c.getInt("rendering.report-cache", 4096), 64, 65536),
                c.getDouble("rendering.float-bucket", 0.0025),
                wear, map,
                c.getDouble("rendering.map.dither-strength", 0.85));

        Opening opening = new Opening(
                clamp(c.getInt("opening.duration-ticks", 120), 40, 400),
                c.getString("opening.easing", "cubic").toLowerCase(Locale.ROOT),
                clamp(c.getInt("opening.reel-length", 64), 30, 200),
                clamp(c.getInt("opening.reveal-hold-ticks", 50), 0, 200),
                clamp(c.getInt("opening.gold-pause-ticks", 16), 0, 100),
                c.getBoolean("opening.knife-auto-preview", true),
                material(c, "opening.marker-top", Material.HOPPER, warn),
                material(c, "opening.marker-bottom", Material.HOPPER, warn),
                material(c, "opening.filler", Material.BLACK_STAINED_GLASS_PANE, warn),
                c.getBoolean("opening.block-commands", true),
                c.getBoolean("opening.broadcast-rare", true),
                c.getString("opening.display", "world").toLowerCase(Locale.ROOT),
                new WorldReel(
                        c.getDouble("opening.world.distance", 3.0),
                        c.getDouble("opening.world.height", 0.35),
                        Math.max(3, Math.min(15, c.getInt("opening.world.visible-items", 7) | 1)),
                        c.getDouble("opening.world.spacing", 0.62),
                        c.getDouble("opening.world.item-scale", 0.5),
                        clamp(c.getInt("opening.world.hold-ticks", 80), 20, 400),
                        c.getDouble("opening.world.view-range", 1.0)),
                c.getBoolean("opening.speech-bubble.enabled", true),
                clamp(c.getInt("opening.speech-bubble.duration-ticks", 60), 10, 400));
        if (!opening.display().equals("gui") && !opening.display().equals("world")) {
            warn.accept("config.yml: opening.display must be gui or world - using gui");
        }

        Items items = new Items(
                material(c, "items.case.material", Material.PAPER, warn), c.getString("items.case.model", "minecraft:chest"),
                material(c, "items.key.material", Material.PAPER, warn), c.getString("items.key.model", "minecraft:trial_key"));

        int slot = c.getInt("skin-inventory-item.slot", 17);
        if (slot < 0 || slot > 35) {
            warn.accept("config.yml: skin-inventory-item.slot must be 0..35 - using 17");
            slot = 17;
        }
        ReservedSlot reserved = new ReservedSlot(c.getBoolean("skin-inventory-item.enabled", false), slot,
                material(c, "skin-inventory-item.material", Material.NETHER_STAR, warn),
                c.getString("skin-inventory-item.model", ""));

        Set<Material> swords = EnumSet.noneOf(Material.class);
        for (String name : c.getStringList("knives.sword-materials")) {
            Material m = Material.matchMaterial(name);
            if (m == null) {
                warn.accept("config.yml: knives.sword-materials contains unknown material '" + name + "'");
            } else {
                swords.add(m);
            }
        }
        if (swords.isEmpty()) {
            for (Material m : Material.values()) {
                if (!m.isLegacy() && m.name().endsWith("_SWORD")) {
                    swords.add(m);
                }
            }
        }
        Knives knives = new Knives(swords, c.getBoolean("knives.glint", true), c.getBoolean("knives.strip-on-drop", true),
                c.getBoolean("knives.bow-skins", true), c.getBoolean("knives.crossbow-skins", true));

        Inspect inspect = new Inspect(
                clamp(c.getInt("inspect.cooldown-ticks", 40), 0, 400),
                c.getBoolean("inspect.sneak-right-click", true),
                c.getBoolean("inspect.swap-hand-key", true),
                c.getBoolean("inspect.hide-held-item", true),
                c.getBoolean("inspect.actionbar", true),
                c.getBoolean("inspect.reveal-preview", true));

        Preview preview = new Preview(
                clamp(c.getInt("preview.duration-seconds", 30), 3, 300),
                c.getDouble("preview.max-move", 1.5),
                c.getInt("preview.showcase-seed", 0),
                c.getDouble("preview.showcase-float", 0.05),
                c.getString("preview.mode", "item").toLowerCase(Locale.ROOT),
                clamp(c.getInt("preview.hologram.resolution", 64), 16, 96),
                c.getDouble("preview.hologram.size", 1.1),
                c.getDouble("preview.hologram.distance", 1.6),
                c.getDouble("preview.item.size", 0.9),
                c.getDouble("preview.item.distance", 1.3));

        Distribution distribution = new Distribution(
                c.getBoolean("resource-pack.distribution.enabled", false),
                c.getString("resource-pack.distribution.bind-address", "0.0.0.0"),
                clamp(c.getInt("resource-pack.distribution.port", 8165), 1, 65535),
                c.getString("resource-pack.distribution.public-url", "http://127.0.0.1:8165"),
                c.getBoolean("resource-pack.distribution.required", false),
                c.getString("resource-pack.distribution.prompt", ""));
        String namespace = c.getString("resource-pack.namespace", "mccases");
        if (!namespace.matches("[a-z0-9_.-]+")) {
            warn.accept("config.yml: resource-pack.namespace is not a valid namespace - using mccases");
            namespace = "mccases";
        }
        ResourcePack pack = new ResourcePack(c.getBoolean("resource-pack.enabled", false) || distribution.enabled(),
                namespace, distribution);
        StatTrak statTrak = new StatTrak(c.getBoolean("stattrak.count-mobs", false));
        Display display = new Display(
                clamp(c.getInt("display.float-decimals", 6), 2, 16),
                clamp(c.getInt("display.inspect-decimals", 12), 2, 16),
                c.getString("display.date-format", "dd.MM.yyyy HH:mm"),
                c.getBoolean("display.log-openings", true));
        SkinInventory skinInventory = new SkinInventory(
                c.getString("skin-inventory.display", "world").toLowerCase(Locale.ROOT),
                clamp(c.getInt("skin-inventory.columns", 6), 2, 9),
                clamp(c.getInt("skin-inventory.rows", 3), 1, 5),
                c.getDouble("skin-inventory.distance", 2.3),
                c.getDouble("skin-inventory.spacing", 0.55),
                c.getDouble("skin-inventory.item-scale", 0.36),
                clamp(c.getInt("skin-inventory.timeout-seconds", 120), 10, 1800));
        return new PluginSettings(c.getBoolean("debug", false), storage, rendering, opening, items, reserved,
                knives, inspect, preview, pack, statTrak, display, skinInventory);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int color(ConfigurationSection c, String path, int def, Consumer<String> warn) {
        String v = c.getString(path);
        if (v == null) {
            return def;
        }
        try {
            return Colors.parse(v);
        } catch (IllegalArgumentException e) {
            warn.accept("config.yml: " + path + ": " + e.getMessage());
            return def;
        }
    }

    static Material material(ConfigurationSection c, String path, Material def, Consumer<String> warn) {
        String v = c.getString(path);
        if (v == null) {
            return def;
        }
        Material m = Material.matchMaterial(v);
        if (m == null || !m.isItem()) {
            warn.accept("config.yml: " + path + ": '" + v + "' is not an item - using " + def.name());
            return def;
        }
        return m;
    }

    /** Names of every top-level key, used to report unknown keys after updates. */
    public static List<String> keys(FileConfiguration c) {
        return new ArrayList<>(c.getKeys(false));
    }
}
