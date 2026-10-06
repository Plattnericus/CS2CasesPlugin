package dev.plattnericus.cases.render;

/**
 * All layers of one weapon at a common square resolution. Arrays are shared read-only between
 * render threads and must never be mutated.
 */
public record WeaponAssets(String weaponId, int size, float[] mask, float[] paint, int[] base,
                           float[] shadow, float[] highlight, float[] wear) {
}
