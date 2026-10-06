package dev.plattnericus.cases.catalog;

import dev.plattnericus.cases.util.Colors;

import java.util.Arrays;

/** Ordered color stops mapped onto texture luminance. */
public final class Palette {

    private final int[] colors;
    private final int[] lut = new int[256];
    private final PaletteMapping mapping;

    public Palette(int[] colors, PaletteMapping mapping) {
        if (colors.length == 0) {
            throw new IllegalArgumentException("palette needs at least one color");
        }
        this.colors = colors.clone();
        this.mapping = mapping;
        for (int i = 0; i < 256; i++) {
            lut[i] = compute(i / 255.0);
        }
    }

    private int compute(double t) {
        if (colors.length == 1) {
            return colors[0];
        }
        if (mapping == PaletteMapping.STEPS) {
            int idx = Math.min(colors.length - 1, (int) (t * colors.length));
            return colors[idx];
        }
        double pos = t * (colors.length - 1);
        int i = Math.min(colors.length - 2, (int) Math.floor(pos));
        return Colors.lerp(colors[i], colors[i + 1], pos - i);
    }

    /** Color for a luminance value in [0, 255]. */
    public int map(int luminance) {
        return lut[luminance & 0xFF];
    }

    public int[] colors() {
        return colors.clone();
    }

    /** Representative color, used for icons, inspect blocks and pack sprites. */
    public int primary() {
        return colors[colors.length / 2];
    }

    public int color(int index) {
        return colors[Math.floorMod(index, colors.length)];
    }

    public int size() {
        return colors.length;
    }

    public PaletteMapping mapping() {
        return mapping;
    }

    public Palette withMapping(PaletteMapping newMapping) {
        return newMapping == mapping ? this : new Palette(colors, newMapping);
    }

    @Override
    public String toString() {
        return Arrays.stream(colors).mapToObj(Colors::hex).toList() + "/" + mapping;
    }
}
