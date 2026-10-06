package dev.plattnericus.cases.render;

/** Fits a rendered skin onto a 128x128 card with background, rarity glow and accent bar. */
public final class MapCardComposer {

    public static final int MAP_SIZE = 128;

    private MapCardComposer() {
    }

    public static ArgbImage compose(ArgbImage skin, int accentRgb, MapCardStyle style) {
        // 1. tight bounding box of the weapon
        int minX = skin.width(), minY = skin.height(), maxX = -1, maxY = -1;
        for (int y = 0; y < skin.height(); y++) {
            for (int x = 0; x < skin.width(); x++) {
                if ((skin.get(x, y) >>> 24) > 8) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        ArgbImage card = background(accentRgb, style);
        if (maxX < 0) {
            return card;
        }
        int bw = maxX - minX + 1;
        int bh = maxY - minY + 1;
        int bar = Math.max(0, style.accentBar());
        int availW = MAP_SIZE - style.padding() * 2;
        int availH = MAP_SIZE - style.padding() * 2 - bar;
        double scale = Math.min((double) availW / bw, (double) availH / bh);
        int tw = Math.max(1, (int) Math.round(bw * scale));
        int th = Math.max(1, (int) Math.round(bh * scale));
        ArgbImage cropped = crop(skin, minX, minY, bw, bh);
        ArgbImage fitted = cropped.scaledTo(tw, th);
        int ox = (MAP_SIZE - tw) / 2;
        int oy = (MAP_SIZE - bar - th) / 2;

        // 2. composite weapon over background
        for (int y = 0; y < th; y++) {
            for (int x = 0; x < tw; x++) {
                int c = fitted.get(x, y);
                int a = c >>> 24;
                if (a == 0) {
                    continue;
                }
                int cx = ox + x;
                int cy = oy + y;
                int bg = card.get(cx, cy);
                card.set(cx, cy, 0xFF000000 | mix(bg, c, a / 255.0));
            }
        }
        adjust(card, style.contrast(), style.saturation());
        return card;
    }

    private static ArgbImage background(int accent, MapCardStyle style) {
        ArgbImage img = new ArgbImage(MAP_SIZE, MAP_SIZE);
        int bar = Math.max(0, style.accentBar());
        double cx = MAP_SIZE / 2.0;
        double cy = (MAP_SIZE - bar) / 2.0;
        double maxR = MAP_SIZE * 0.62;
        for (int y = 0; y < MAP_SIZE; y++) {
            int row = mix(style.backgroundTop(), style.backgroundBottom(), y / (double) (MAP_SIZE - 1));
            for (int x = 0; x < MAP_SIZE; x++) {
                int c = row;
                double d = Math.hypot(x - cx, (y - cy) * 1.4) / maxR;
                double glow = style.glow() * Math.max(0, 1 - d) * Math.max(0, 1 - d);
                if (glow > 0) {
                    c = mix(c, accent, glow);
                }
                double vignette = Math.min(1, Math.hypot(x - cx, y - cy) / (MAP_SIZE * 0.75));
                c = mix(c, 0x000000, vignette * vignette * 0.35);
                if (y >= MAP_SIZE - bar) {
                    c = accent;
                }
                img.set(x, y, 0xFF000000 | c);
            }
        }
        return img;
    }

    private static ArgbImage crop(ArgbImage src, int x0, int y0, int w, int h) {
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            System.arraycopy(src.pixels(), (y0 + y) * src.width() + x0, px, y * w, w);
        }
        return new ArgbImage(w, h, px);
    }

    private static void adjust(ArgbImage img, double contrast, double saturation) {
        int[] px = img.pixels();
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            double r = (c >> 16) & 0xFF;
            double g = (c >> 8) & 0xFF;
            double b = c & 0xFF;
            double l = 0.299 * r + 0.587 * g + 0.114 * b;
            r = l + (r - l) * saturation;
            g = l + (g - l) * saturation;
            b = l + (b - l) * saturation;
            r = (r - 128) * contrast + 128;
            g = (g - 128) * contrast + 128;
            b = (b - 128) * contrast + 128;
            px[i] = (c & 0xFF000000) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
        }
    }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int clamp(double v) {
        return v < 0 ? 0 : (v > 255 ? 255 : (int) Math.round(v));
    }
}
