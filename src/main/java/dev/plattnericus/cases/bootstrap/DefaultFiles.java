package dev.plattnericus.cases.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Copies the bundled default files (configs, catalog, textures) into the data folder.
 * Existing files are never overwritten, so admin edits survive updates while newly added
 * defaults still appear.
 */
public final class DefaultFiles {

    private static final String ROOT = "defaults/";

    private DefaultFiles() {
    }

    /** @return number of files written */
    public static int extractMissing(JavaPlugin plugin) throws IOException {
        List<String> entries = index(plugin);
        int written = 0;
        for (String entry : entries) {
            File target = new File(plugin.getDataFolder(), entry);
            if (target.exists()) {
                if (entry.equals("config.yml") || entry.equals("market.yml") || entry.equals("sounds.yml") || entry.startsWith("messages_")) mergeMissing(plugin, entry, target);
                continue;
            }
            File parent = target.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("cannot create " + parent);
            }
            try (InputStream in = plugin.getResource(ROOT + entry)) {
                if (in == null) {
                    throw new IOException("bundled default missing: " + entry);
                }
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            written++;
        }
        return written;
    }

    /** Merge leaves only; a malformed user file is never silently replaced. */
    private static void mergeMissing(JavaPlugin plugin, String entry, File target) throws IOException {
        try (InputStream in = open(plugin, entry)) {
            if (in == null) return;
            var defaults = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            var existing = new org.bukkit.configuration.file.YamlConfiguration();
            existing.options().parseComments(true);
            try { existing.load(target); }
            catch (org.bukkit.configuration.InvalidConfigurationException error) { throw new IOException("Invalid YAML in " + target + "; preserving file", error); }
            boolean changed = false;
            for (String key : defaults.getKeys(true)) if (!defaults.isConfigurationSection(key) && !existing.contains(key, true)) {
                existing.set(key, defaults.get(key)); changed = true;
            }
            if (changed) {
                var temp = Files.createTempFile(target.toPath().getParent(), target.getName(), ".tmp");
                try {
                    Files.writeString(temp, existing.saveToString(), StandardCharsets.UTF_8);
                    try { Files.move(temp, target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING); }
                } finally { Files.deleteIfExists(temp); }
            }
        }
    }

    /** Opens a bundled default for reading (used to fill in missing config keys). */
    public static InputStream open(JavaPlugin plugin, String entry) {
        return plugin.getResource(ROOT + entry);
    }

    /** Whether a bundled default exists (the stream is closed again). */
    public static boolean exists(JavaPlugin plugin, String entry) {
        try (InputStream in = plugin.getResource(ROOT + entry)) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static List<String> index(JavaPlugin plugin) throws IOException {
        List<String> out = new ArrayList<>();
        try (InputStream in = plugin.getResource(ROOT + "index.txt")) {
            if (in == null) {
                throw new IOException("defaults/index.txt missing from the plugin jar");
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.equals("index.txt") && !line.contains("..")) {
                        out.add(line);
                    }
                }
            }
        }
        return out;
    }
}
