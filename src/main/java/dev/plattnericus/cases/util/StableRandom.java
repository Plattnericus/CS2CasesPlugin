package dev.plattnericus.cases.util;

/**
 * SplitMix64 generator. Implemented here instead of relying on JDK generators so that the
 * sequence for a given seed is fixed forever, independent of the Java version.
 * Used for everything that must be reproducible (pattern placement, variants, wear layout).
 * Never used for reward rolls.
 */
public final class StableRandom {

    private long state;

    public StableRandom(long seed) {
        this.state = seed;
    }

    public long nextLong() {
        long z = (state += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Uniform double in [0, 1). */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public double range(double min, double max) {
        return min + (max - min) * nextDouble();
    }

    public boolean nextBoolean() {
        return (nextLong() & 1L) != 0;
    }
}
