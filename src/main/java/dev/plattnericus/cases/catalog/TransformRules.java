package dev.plattnericus.cases.catalog;

/**
 * Which seed-driven transformations a finish allows. Offsets are fractions of the pattern
 * texture size; {@code baseScale} is pattern pixels per canvas pixel at scale 1.
 * With {@code clamp} the texture is not tiled (gradients such as Fade).
 */
public record TransformRules(boolean translate,
                             double offsetMinX, double offsetMaxX, double offsetMinY, double offsetMaxY,
                             double rotationMin, double rotationMax,
                             double scaleMin, double scaleMax,
                             boolean mirror, boolean clamp, double baseScale) {

    public static TransformRules defaults() {
        return new TransformRules(true, 0, 1, 0, 1, 0, 360, 0.9, 1.15, true, false, 1.0);
    }
}
