package dev.plattnericus.cases.tools;

/**
 * Periodic value noise. Every function here tiles seamlessly over the given period,
 * which keeps generated pattern textures free of visible seams when the renderer wraps them.
 */
final class TileNoise {

    private final long seed;

    TileNoise(long seed) {
        this.seed = seed;
    }

    private double lattice(int x, int y, int period) {
        int px = Math.floorMod(x, period);
        int py = Math.floorMod(y, period);
        long h = seed;
        h ^= px * 0x9E3779B97F4A7C15L;
        h = Long.rotateLeft(h, 31) * 0xBF58476D1CE4E5B9L;
        h ^= py * 0x94D049BB133111EBL;
        h = (h ^ (h >>> 29)) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFFL) / (double) 0xFFFFFF;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /** Value noise in [0,1]; x/y are in lattice units, period in lattice cells. */
    double value(double x, double y, int period) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        double fx = fade(x - x0);
        double fy = fade(y - y0);
        double a = lattice(x0, y0, period);
        double b = lattice(x0 + 1, y0, period);
        double c = lattice(x0, y0 + 1, period);
        double d = lattice(x0 + 1, y0 + 1, period);
        double top = a + (b - a) * fx;
        double bottom = c + (d - c) * fx;
        return top + (bottom - top) * fy;
    }

    /**
     * Fractal noise over a square texture of {@code size} pixels. {@code cells} is the lattice
     * resolution of the first octave; every octave doubles it, so tiling is preserved.
     */
    double fbm(double px, double py, int size, int cells, int octaves, double gain) {
        double sum = 0;
        double amp = 1;
        double norm = 0;
        int c = cells;
        for (int o = 0; o < octaves; o++) {
            double scale = (double) c / size;
            sum += value(px * scale, py * scale, c) * amp;
            norm += amp;
            amp *= gain;
            c *= 2;
        }
        return sum / norm;
    }

    /** Hash in [0,1) for integer coordinates, used for per-cell random values. */
    double cell(int x, int y, int salt) {
        return lattice(x * 7919 + salt, y * 104729 - salt, Integer.MAX_VALUE);
    }
}
