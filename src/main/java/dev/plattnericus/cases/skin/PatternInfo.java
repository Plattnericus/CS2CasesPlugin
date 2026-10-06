package dev.plattnericus.cases.skin;

import dev.plattnericus.cases.pattern.PatternReport;

/**
 * Snapshot of the pattern analysis stored with an instance (variant, classification, fade).
 * Kept with the instance so the audit trail shows exactly what was shown at drop time.
 */
public record PatternInfo(String variantId, String variantName, String classification, int tier,
                          int color, Double fadePercent) {

    public static final PatternInfo NONE = new PatternInfo(null, null, null, 0, 0, null);

    public static PatternInfo of(PatternReport r) {
        if (r == null) {
            return NONE;
        }
        return new PatternInfo(r.variantId(), r.variantName(), r.classification(), r.tier(), r.color(), r.fadePercent());
    }

    public boolean hasClassification() {
        return classification != null && !classification.isEmpty();
    }
}
