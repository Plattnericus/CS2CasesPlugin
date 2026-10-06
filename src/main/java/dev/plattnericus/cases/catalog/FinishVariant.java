package dev.plattnericus.cases.catalog;

/**
 * Seed-selected sub-variant of a finish (Doppler phases, Ruby, Sapphire, Emerald...).
 * {@code texture} may be null to reuse the finish texture.
 */
public record FinishVariant(String id, String name, double weight, Palette palette, String texture, int color) {
}
