package dev.plattnericus.cases.render;

/**
 * The pattern colors before wear, lighting and overlays, plus per-pixel paint coverage.
 * Pattern analysis runs on this so wear can never influence a classification.
 */
public record PaintSample(int size, int[] colors, float[] coverage) {
}
