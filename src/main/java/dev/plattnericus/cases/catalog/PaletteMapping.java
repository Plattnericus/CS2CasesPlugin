package dev.plattnericus.cases.catalog;

/** How a grayscale pattern texture is turned into color. */
public enum PaletteMapping {
    /** Smooth gradient through all palette stops. */
    SMOOTH,
    /** Hard bands, one per palette color (camo, digital, graffiti). */
    STEPS,
    /** Texture is used as-is (colored PNG); palette ignored. */
    NONE
}
