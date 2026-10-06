package dev.plattnericus.cases.pattern;

import java.util.List;
import java.util.Map;

/** Everything from patterns.yml: color classes, analysis profiles and the manual seed database. */
public record PatternConfig(List<ColorClass> colorClasses, Map<String, AnalysisProfile> profiles,
                            Map<String, Map<Integer, ManualPattern>> manual, int seedMin, int seedMax) {

    public PatternConfig {
        colorClasses = List.copyOf(colorClasses);
        profiles = Map.copyOf(profiles);
        manual = Map.copyOf(manual);
    }

    public ManualPattern manual(String skinId, int seed) {
        Map<Integer, ManualPattern> bySeed = manual.get(skinId);
        return bySeed == null ? null : bySeed.get(seed);
    }

    public ColorClass colorClass(String id) {
        for (ColorClass c : colorClasses) {
            if (c.id().equals(id)) {
                return c;
            }
        }
        return null;
    }
}
