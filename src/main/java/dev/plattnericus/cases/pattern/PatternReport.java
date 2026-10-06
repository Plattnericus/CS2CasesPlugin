package dev.plattnericus.cases.pattern;

import java.util.Map;

/**
 * Everything known about one skin + seed: variant (Doppler phase), classification, fade
 * percentage and the raw metrics used to decide them.
 */
public record PatternReport(String variantId, String variantName, String classification, int tier,
                            int color, Double fadePercent, boolean manual, Map<String, Double> metrics) {

    public PatternReport {
        metrics = Map.copyOf(metrics);
    }

    public static PatternReport none(String variantId, String variantName) {
        return new PatternReport(variantId, variantName, null, 0, 0, null, false, Map.of());
    }

    public boolean hasClassification() {
        return classification != null;
    }
}
