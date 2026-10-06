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
