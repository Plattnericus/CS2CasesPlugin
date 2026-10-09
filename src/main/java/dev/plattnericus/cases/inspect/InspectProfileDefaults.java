package dev.plattnericus.cases.inspect;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Upgrade only unchanged stock timelines/pools; explicit server choreography stays intact. */
public final class InspectProfileDefaults {
    private InspectProfileDefaults() { }
    public static boolean upgrade(YamlConfiguration current, YamlConfiguration defaults) {
        try (var in = InspectProfileDefaults.class.getResourceAsStream("/migrations/inspect-profiles-v1.yml")) {
            if (in == null) return false;
            var previous = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String section : java.util.List.of("animations", "animation-pools")) {
                var old = previous.getConfigurationSection(section);
                if (old == null) continue;
                for (String id : old.getKeys(false)) {
                    String path = section + "." + id;
                    Object before = value(previous, path), existing = value(current, path), after = value(defaults, path);
                    if (existing != null && existing.equals(before) && after != null && !after.equals(before)) {
                        current.set(path, defaults.get(path)); changed = true;
                    }
                }
            }
            return changed;
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot read inspect profile migration", error); }
    }
    private static Object value(YamlConfiguration y, String path) {
        Object value = y.get(path);
        if (!(value instanceof ConfigurationSection section)) return value;
        Map<String, Object> leaves = new LinkedHashMap<>();
        for (String key : section.getKeys(true)) if (!section.isConfigurationSection(key)) leaves.put(key, section.get(key));
        return leaves;
    }
}
