package dev.plattnericus.cases.catalog;

/**
 * A drop tier. {@code weight} is the relative case odds; {@code rareSpecial} marks the gold tier,
 * which gets the dedicated reveal and is shown as a mystery item in case previews.
 */
public record Rarity(String id, String name, int color, double weight, int order, String pane,
                     boolean rareSpecial, String revealSound) {
}
