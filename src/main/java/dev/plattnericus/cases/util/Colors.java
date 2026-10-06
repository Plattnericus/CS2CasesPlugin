package dev.plattnericus.cases.util;

/** Small helpers for packed 0xRRGGBB colors. */
public final class Colors {

    private Colors() {
    }

    public static int parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("color is missing");
        }
        String t = text.trim();
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        if (t.length() == 3) {
            t = "" + t.charAt(0) + t.charAt(0) + t.charAt(1) + t.charAt(1) + t.charAt(2) + t.charAt(2);
        }
        if (t.length() != 6) {
            throw new IllegalArgumentException("invalid color '" + text + "'");
        }
        return Integer.parseInt(t, 16);
    }

    public static String hex(int rgb) {
        return String.format("#%06x", rgb & 0xFFFFFF);
    }

    public static int r(int c) {
        return (c >> 16) & 0xFF;
    }

    public static int g(int c) {
        return (c >> 8) & 0xFF;
    }

    public static int b(int c) {
        return c & 0xFF;
    }

    public static int rgb(int r, int g, int b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int clamp(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }

    public static int lerp(int a, int b, double t) {
        return rgb(
                (int) Math.round(r(a) + (r(b) - r(a)) * t),
                (int) Math.round(g(a) + (g(b) - g(a)) * t),
                (int) Math.round(b(a) + (b(b) - b(a)) * t));
    }

    /** HSV with hue in degrees [0,360), saturation and value in [0,1]. */
    public static float[] hsv(int c) {
        float r = r(c) / 255f;
        float g = g(c) / 255f;
        float b = b(c) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) {
            h = 0;
        } else if (max == r) {
            h = 60 * (((g - b) / d) % 6);
        } else if (max == g) {
            h = 60 * (((b - r) / d) + 2);
        } else {
            h = 60 * (((r - g) / d) + 4);
        }
        if (h < 0) {
            h += 360;
        }
        float s = max == 0 ? 0 : d / max;
        return new float[]{h, s, max};
    }

    public static double luminance(int c) {
        return (0.2126 * r(c) + 0.7152 * g(c) + 0.0722 * b(c)) / 255.0;
    }
}
