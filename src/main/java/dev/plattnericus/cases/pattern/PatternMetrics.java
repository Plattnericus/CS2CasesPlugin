package dev.plattnericus.cases.pattern;

import dev.plattnericus.cases.catalog.Region;
import dev.plattnericus.cases.catalog.WeaponType;
import dev.plattnericus.cases.render.PaintSample;
import dev.plattnericus.cases.util.Colors;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Measures color shares of the rendered (pre-wear) paint inside analysis regions.
 * Metric keys are {@code <region>.<class>} with values in [0, 1].
 */
public final class PatternMetrics {

    private PatternMetrics() {
    }

    public static Map<String, Double> measure(PaintSample paint, WeaponType weapon, AnalysisProfile profile,
                                              PatternConfig config) {
        List<ColorClass> classes = profile.classes().stream().map(config::colorClass)
                .filter(java.util.Objects::nonNull).toList();
        List<String> regionIds = profile.regions().isEmpty() ? List.of(Region.FULL) : profile.regions();
        Map<String, Double> out = new LinkedHashMap<>();
        int size = paint.size();
        for (String regionId : regionIds) {
            Region region = weapon.region(regionId);
            if (region == null) {
                continue;
            }
            int[] counts = new int[classes.size()];
            int total = 0;
            for (int y = 0; y < size; y++) {
                double ny = (y + 0.5) / size;
                for (int x = 0; x < size; x++) {
                    int i = y * size + x;
                    if (paint.coverage()[i] < 0.5f || !region.contains((x + 0.5) / size, ny)) {
                        continue;
                    }
                    total++;
                    float[] hsv = Colors.hsv(paint.colors()[i]);
                    for (int c = 0; c < classes.size(); c++) {
                        if (classes.get(c).matches(hsv[0], hsv[1], hsv[2])) {
                            counts[c]++;
                            break;
                        }
                    }
                }
            }
            for (int c = 0; c < classes.size(); c++) {
                out.put(regionId + "." + classes.get(c).id(), total == 0 ? 0.0 : counts[c] / (double) total);
            }
        }
        return out;
    }
}
