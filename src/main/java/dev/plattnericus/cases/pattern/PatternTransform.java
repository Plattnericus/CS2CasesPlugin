package dev.plattnericus.cases.pattern;

/** Seed-derived placement of the pattern texture. Offsets are fractions of the texture size. */
public record PatternTransform(double offsetX, double offsetY, double rotation, double scale, boolean mirror) {

    public static PatternTransform identity() {
        return new PatternTransform(0.5, 0.5, 0, 1, false);
    }
}
