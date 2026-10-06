package dev.plattnericus.cases.util;

import java.nio.charset.StandardCharsets;

/** FNV-1a based hashing with a final avalanche step; stable across JVMs and restarts. */
public final class StableHash {

    private StableHash() {
    }

    public static long of(String text) {
        long h = 0xCBF29CE484222325L;
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xFF;
            h *= 0x100000001B3L;
        }
        return avalanche(h);
    }

    public static long of(String text, long salt) {
        return avalanche(of(text) ^ (salt * 0x9E3779B97F4A7C15L));
    }

    public static long avalanche(long z) {
        z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
        z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return z ^ (z >>> 33);
    }
}
