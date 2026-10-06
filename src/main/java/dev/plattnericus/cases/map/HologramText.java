package dev.plattnericus.cases.map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

/**
 * Turns an RGB image into a text component of colored block glyphs, one glyph per pixel.
 * Neighbouring pixels of (nearly) the same color are merged into one run to keep the packet small.
 */
final class HologramText {

    private static final String PIXEL = "█";

    private HologramText() {
    }

    static Component render(int[] rgb, int size) {
        TextComponent.Builder out = Component.text();
        for (int y = 0; y < size; y++) {
            if (y > 0) {
                out.append(Component.newline());
            }
            int x = 0;
            while (x < size) {
                int color = quantize(rgb[y * size + x]);
                int run = 1;
                while (x + run < size && quantize(rgb[y * size + x + run]) == color) {
                    run++;
                }
                out.append(Component.text(PIXEL.repeat(run), TextColor.color(color)));
                x += run;
            }
        }
        return out.build();
    }

    /** 5 bits per channel: no visible loss at this size, but far more pixels merge into one run. */
    private static int quantize(int c) {
        int r = ((c >> 16) & 0xF8) | 0x04;
        int g = ((c >> 8) & 0xF8) | 0x04;
        int b = (c & 0xF8) | 0x04;
        return (r << 16) | (g << 8) | b;
    }
}
