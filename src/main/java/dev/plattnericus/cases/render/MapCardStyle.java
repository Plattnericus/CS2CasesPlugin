package dev.plattnericus.cases.render;

/**
 * Look of the 128x128 preview card that is drawn onto a map.
 *
 * @param backgroundTop    gradient color at the top
 * @param backgroundBottom gradient color at the bottom
 * @param padding          free pixels around the weapon
 * @param glow             strength of the rarity-colored glow behind the weapon (0..1)
 * @param accentBar        height of the rarity bar at the bottom edge (0 disables)
 * @param contrast         contrast multiplier applied before palette conversion
 * @param saturation       saturation multiplier applied before palette conversion
 */
public record MapCardStyle(int backgroundTop, int backgroundBottom, int padding, double glow, int accentBar,
                           double contrast, double saturation) {

    public static MapCardStyle defaults() {
        return new MapCardStyle(0x2B2F36, 0x121418, 6, 0.22, 3, 1.08, 1.12);
    }
}
