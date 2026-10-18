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
        boolean changed = false;
        for (String baseline : java.util.List.of("v1", "v2", "v3", "v4"))
            changed |= upgrade(current, defaults, "/migrations/inspect-profiles-" + baseline + ".yml", java.util.List.of("animations", "animation-pools"));
        return changed;
    }
    /** Held-pose inspects in inspect.yml: unchanged 1.3.0 and 10.17.2026 stock entries take the current defaults. */
    public static boolean upgradeViews(YamlConfiguration current, YamlConfiguration defaults) {
        // 1.3.0 and 10.17.2026 stock entries; each step only replaces entries that still match that release.
        boolean changed = false;
        java.util.List<String> baselines = java.util.List.of("/migrations/inspect-view-v1.yml", "/migrations/inspect-view-v2.yml");
        for (String baseline : baselines) {
            changed |= upgrade(current, defaults, baseline, java.util.List.of("view-animations", "view-pools"));
        }
        // Stock inspects the defaults no longer ship (the shared knife set before every knife had its own)
        // go away while unchanged and unused; a pool the server wrote that still names one keeps it.
        var animations = current.getConfigurationSection("view-animations");
        if (animations == null) return changed;
        java.util.Set<String> used = new java.util.HashSet<>();
        var pools = current.getConfigurationSection("view-pools");
        if (pools != null) for (String key : pools.getKeys(false)) used.addAll(pools.getStringList(key));
        for (String id : animations.getKeys(false)) {
            String path = "view-animations." + id;
            if (defaults.contains(path) || used.contains(id)) continue;
            for (String baseline : baselines) {
                if (value(current, path).equals(value(baseline(baseline), path))) {
                    current.set(path, null);
                    changed = true;
                    break;
                }
            }
        }
        return changed;
    }
    private static boolean upgrade(YamlConfiguration current, YamlConfiguration defaults, String baseline, java.util.List<String> sections) {
        try (var in = InspectProfileDefaults.class.getResourceAsStream(baseline)) {
            if (in == null) return false;
            var previous = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String section : sections) {
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
    private static YamlConfiguration baseline(String resource) {
        try (var in = InspectProfileDefaults.class.getResourceAsStream(resource)) {
            if (in == null) return new YamlConfiguration();
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
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
