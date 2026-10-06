package dev.plattnericus.cases.catalog;

/** Reusable look: pattern texture + default mapping and transformation rules. */
public record Style(String id, String texture, PaletteMapping mapping, TransformRules transform,
                    double metallic, double wearStrength) {
}
