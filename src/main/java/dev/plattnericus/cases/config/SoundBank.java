package dev.plattnericus.cases.config;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Named sound cues from sounds.yml. A cue can layer several sounds; every sound is referenced by
 * its namespaced key string, so resource-pack sounds work as well as vanilla ones.
 */
public final class SoundBank {

    public record Layer(String sound, float volume, float pitch, SoundCategory category) {
    }

    private final Map<String, List<Layer>> cues = new HashMap<>();
    private final float masterVolume;

    public SoundBank(File file, Consumer<String> warn) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        this.masterVolume = (float) yaml.getDouble("master-volume", 1.0);
        ConfigurationSection sec = yaml.getConfigurationSection("sounds");
        if (sec == null) {
            warn.accept("sounds.yml: section 'sounds' missing - all sounds disabled");
            return;
        }
        for (String key : sec.getKeys(true)) {
            if (sec.isConfigurationSection(key) && sec.contains(key + ".sound")) {
                cues.put(key, List.of(layer(sec.getConfigurationSection(key), key, warn)));
            } else if (sec.isList(key)) {
                List<Layer> layers = new ArrayList<>();
                for (Map<?, ?> raw : sec.getMapList(key)) {
                    YamlConfiguration tmp = new YamlConfiguration();
                    raw.forEach((k, v) -> tmp.set(String.valueOf(k), v));
                    layers.add(layer(tmp, key, warn));
                }
                cues.put(key, List.copyOf(layers));
            }
        }
    }

    private static Layer layer(ConfigurationSection s, String key, Consumer<String> warn) {
        SoundCategory category;
        try {
            category = SoundCategory.valueOf(s.getString("category", "master").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warn.accept("sounds.yml: '" + key + "' has unknown category '" + s.getString("category") + "'");
            category = SoundCategory.MASTER;
        }
        return new Layer(s.getString("sound", "minecraft:ui.button.click"), (float) s.getDouble("volume", 0.5),
                (float) s.getDouble("pitch", 1.0), category);
    }

    public void play(Player player, String cue) {
        play(player, cue, 1f);
    }

    /** Plays a cue only for this player; {@code pitchFactor} scales every layer's pitch. */
    public void play(Player player, String cue, float pitchFactor) {
        List<Layer> layers = cues.get(cue);
        if (layers == null) {
            return;
        }
        for (Layer l : layers) {
            player.playSound(player, l.sound(), l.category(), l.volume() * masterVolume,
                    Math.max(0.5f, Math.min(2f, l.pitch() * pitchFactor)));
        }
    }

    /** Plays a cue at a location for everyone nearby. */
    public void playAt(Location location, String cue) {
        List<Layer> layers = cues.get(cue);
        if (layers == null || location.getWorld() == null) {
            return;
        }
        for (Layer l : layers) {
            location.getWorld().playSound(location, l.sound(), l.category(), l.volume() * masterVolume, l.pitch());
        }
    }

    public boolean has(String cue) {
        return cues.containsKey(cue);
    }
}
