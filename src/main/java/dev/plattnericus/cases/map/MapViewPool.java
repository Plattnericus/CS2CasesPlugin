package dev.plattnericus.cases.map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Reusable map ids. Every created map is stored in the world's data forever, so ids are recycled
 * across viewers and restarts instead of creating a new map per preview.
 */
public final class MapViewPool {

    private final File file;
    private final Logger logger;
    private final List<Integer> knownIds = new ArrayList<>();
    private final Deque<MapView> free = new ArrayDeque<>();
    private final Map<UUID, MapView> assigned = new HashMap<>();
    private final Map<Integer, PreviewMapRenderer> renderers = new HashMap<>();

    private final java.awt.Color[] palette;

    public MapViewPool(File dataFolder, Logger logger, java.awt.Color[] palette) {
        this.file = new File(dataFolder, "maps.yml");
        this.logger = logger;
        this.palette = palette;
    }

    public void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (int id : yaml.getIntegerList("map-ids")) {
            MapView view = Bukkit.getMap(id);
            if (view != null) {
                configure(view);
                knownIds.add(id);
                free.add(view);
            }
        }
    }

    public MapView acquire(UUID viewer) {
        MapView view = assigned.get(viewer);
        if (view != null) {
            return view;
        }
        view = free.poll();
        if (view == null) {
            World world = Bukkit.getWorlds().getFirst();
            view = Bukkit.createMap(world);
            configure(view);
            knownIds.add(view.getId());
            save();
        }
        assigned.put(viewer, view);
        return view;
    }

    public PreviewMapRenderer renderer(MapView view) {
        return renderers.get(view.getId());
    }

    public void release(UUID viewer) {
        MapView view = assigned.remove(viewer);
        if (view != null) {
            free.add(view);
        }
    }

    private void configure(MapView view) {
        for (MapRenderer r : new ArrayList<>(view.getRenderers())) {
            view.removeRenderer(r);
        }
        view.setScale(MapView.Scale.CLOSEST);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setLocked(true);
        PreviewMapRenderer renderer = new PreviewMapRenderer(palette);
        view.addRenderer(renderer);
        renderers.put(view.getId(), renderer);
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("Map ids reused for skin previews. Do not edit."));
        yaml.set("map-ids", knownIds);
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.warning("Could not save maps.yml: " + e.getMessage());
        }
    }
}
