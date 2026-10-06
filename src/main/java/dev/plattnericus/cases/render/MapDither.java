package dev.plattnericus.cases.render;

/**
 * Converts an RGB image to map color ids with serpentine Floyd-Steinberg dithering.
 * Nearest-color search uses a weighted ("redmean") distance and a 32³ lookup table.
 */
public final class MapDither {

    private final int[] rgb;
    private final byte[] ids;
    private final short[] lut = new short[32 * 32 * 32];
    private final double strength;

    /**
     * @param paletteRgb colors usable on a map (opaque entries only)
     * @param paletteIds the map byte for each color
     * @param strength   error diffusion factor (1 = classic Floyd-Steinberg, lower = calmer image)
     */
    public MapDither(int[] paletteRgb, byte[] paletteIds, double strength) {
        if (paletteRgb.length == 0 || paletteRgb.length != paletteIds.length) {
            throw new IllegalArgumentException("invalid palette");
        }
        this.rgb = paletteRgb.clone();
        this.ids = paletteIds.clone();
        this.strength = strength;
        for (int r = 0; r < 32; r++) {
            for (int g = 0; g < 32; g++) {
                for (int b = 0; b < 32; b++) {
                    lut[(r << 10) | (g << 5) | b] = (short) nearestExact(r * 8 + 4, g * 8 + 4, b * 8 + 4);
                }
            }
        }
    }

    private int nearestExact(int r, int g, int b) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < rgb.length; i++) {
            int c = rgb[i];
            double d = distance(r, g, b, (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Refines the table guess against its neighbours in palette space. */
    private int nearest(int r, int g, int b) {
        int guess = lut[((r >> 3) << 10) | ((g >> 3) << 5) | (b >> 3)];
        int c = rgb[guess];
        double gd = distance(r, g, b, (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF);
        if (gd < 120) {
            return guess;
        }
        return nearestExact(r, g, b);
    }

    private static double distance(int r1, int g1, int b1, int r2, int g2, int b2) {
        double rm = (r1 + r2) / 2.0;
        double dr = r1 - r2;
        double dg = g1 - g2;
        double db = b1 - b2;
        return (2 + rm / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rm) / 256) * db * db;
    }

    /** @return map color ids, row-major; fully transparent pixels become id 0 (transparent). */
    public byte[] dither(ArgbImage img) {
        int w = img.width();
        int h = img.height();
        float[] er = new float[(w + 2) * 2];
        float[] eg = new float[(w + 2) * 2];
        float[] eb = new float[(w + 2) * 2];
        byte[] out = new byte[w * h];
        for (int y = 0; y < h; y++) {
            int cur = (y & 1) * (w + 2);
            int nxt = ((y + 1) & 1) * (w + 2);
            java.util.Arrays.fill(er, nxt, nxt + w + 2, 0);
            java.util.Arrays.fill(eg, nxt, nxt + w + 2, 0);
            java.util.Arrays.fill(eb, nxt, nxt + w + 2, 0);
            boolean ltr = (y & 1) == 0;
            for (int step = 0; step < w; step++) {
                int x = ltr ? step : w - 1 - step;
                int c = img.get(x, y);
                if ((c >>> 24) < 128) {
                    out[y * w + x] = 0;
                    continue;
                }
                int e = x + 1;
                float r = ((c >> 16) & 0xFF) + er[cur + e];
                float g = ((c >> 8) & 0xFF) + eg[cur + e];
                float b = (c & 0xFF) + eb[cur + e];
                int ri = clamp(r), gi = clamp(g), bi = clamp(b);
                int idx = nearest(ri, gi, bi);
                out[y * w + x] = ids[idx];
                int pc = rgb[idx];
                float dr = (float) ((ri - ((pc >> 16) & 0xFF)) * strength);
                float dg = (float) ((gi - ((pc >> 8) & 0xFF)) * strength);
                float db = (float) ((bi - (pc & 0xFF)) * strength);
                int dir = ltr ? 1 : -1;
                spread(er, eg, eb, cur + e + dir, dr, dg, db, 7f / 16);
                spread(er, eg, eb, nxt + e - dir, dr, dg, db, 3f / 16);
                spread(er, eg, eb, nxt + e, dr, dg, db, 5f / 16);
                spread(er, eg, eb, nxt + e + dir, dr, dg, db, 1f / 16);
            }
        }
        return out;
    }

    /** Converts ids back to RGB, used by the tooling to preview exactly what the client sees. */
    public int[] decode(byte[] mapIds) {
        int[] out = new int[mapIds.length];
        for (int i = 0; i < mapIds.length; i++) {
            for (int p = 0; p < ids.length; p++) {
                if (ids[p] == mapIds[i]) {
                    out[i] = 0xFF000000 | rgb[p];
                    break;
                }
            }
        }
        return out;
    }

    private static void spread(float[] er, float[] eg, float[] eb, int idx, float dr, float dg, float db, float f) {
        er[idx] += dr * f;
        eg[idx] += dg * f;
        eb[idx] += db * f;
    }

    private static int clamp(float v) {
        return v < 0 ? 0 : (v > 255 ? 255 : Math.round(v));
    }
}
