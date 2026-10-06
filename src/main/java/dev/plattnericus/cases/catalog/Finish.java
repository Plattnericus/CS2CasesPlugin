package dev.plattnericus.cases.catalog;

import java.util.List;

/**
 * Visual definition of a paint job. Shared finishes (knife finishes) live in finishes.yml;
 * weapon skins usually define theirs inline.
 */
public record Finish(String id, String name, Style style, Palette palette, List<FinishVariant> variants,
                     String analysis, boolean wearable, double floatMin, double floatMax,
                     boolean perInstanceWear, String overlay, boolean patterned) {

    public Finish {
        variants = List.copyOf(variants);
    }

    public boolean hasVariants() {
        return !variants.isEmpty();
    }

    public FinishVariant variant(String variantId) {
        if (variantId == null) {
            return null;
        }
        for (FinishVariant v : variants) {
            if (v.id().equals(variantId)) {
                return v;
            }
        }
        return null;
    }

    /** Vanilla knives carry no paint and therefore no name suffix. */
    public boolean isVanilla() {
        return name == null || name.isEmpty();
    }
}
