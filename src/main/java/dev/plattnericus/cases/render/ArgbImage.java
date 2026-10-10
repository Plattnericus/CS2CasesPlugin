package dev.plattnericus.cases.render;

import java.awt.image.BufferedImage;

/** Plain ARGB pixel buffer. */
public final class ArgbImage {

    private final int width;
    private final int height;
    private final int[] pixels;

    public ArgbImage(int width, int height) {
        this(width, height, new int[width * height]);
    }

    public ArgbImage(int width, int height, int[] pixels) {
        if (pixels.length != width * height) {
            throw new IllegalArgumentException("size mismatch");
        }
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int[] pixels() {
        return pixels;
    }

    public int get(int x, int y) {
        return pixels[y * width + x];
    }

    public void set(int x, int y, int argb) {
        pixels[y * width + x] = argb;
    }

    public BufferedImage toBufferedImage() {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, width, height, pixels, 0, width);
        return img;
    }

    public static ArgbImage from(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        return new ArgbImage(w, h, px);
    }

    /**
     * Area-averaging downscale with premultiplied alpha, so transparent edges do not bleed dark
     * fringes into the result.
     */
    public ArgbImage scaledTo(int newWidth, int newHeight) {
        if (newWidth < 1 || newHeight < 1) throw new IllegalArgumentException("size must be positive");
        int[] out = new int[newWidth * newHeight];
        double sx = (double) width / newWidth;
        double sy = (double) height / newHeight;
        for (int y = 0; y < newHeight; y++) {
            double top = y * sy, bottom = (y + 1) * sy;
            int y0 = (int) Math.floor(top), y1 = (int) Math.ceil(bottom);
            for (int x = 0; x < newWidth; x++) {
                double left = x * sx, right = (x + 1) * sx;
                int x0 = (int) Math.floor(left), x1 = (int) Math.ceil(right);
                double a = 0, r = 0, g = 0, b = 0;
                double coverage = 0;
                for (int yy = y0; yy < Math.min(height, y1); yy++) {
                    for (int xx = x0; xx < Math.min(width, x1); xx++) {
                        int c = pixels[yy * width + xx];
                        double weight = (Math.min(right, xx + 1) - Math.max(left, xx))
                                * (Math.min(bottom, yy + 1) - Math.max(top, yy));
                        double ca = ((c >>> 24) & 0xFF) / 255.0 * weight;
                        a += ca;
                        r += ((c >> 16) & 0xFF) * ca;
                        g += ((c >> 8) & 0xFF) * ca;
                        b += (c & 0xFF) * ca;
                        coverage += weight;
                    }
                }
                if (coverage <= 0 || a <= 0) {
                    continue;
                }
                int oa = (int) Math.round(a / coverage * 255);
                out[y * newWidth + x] = (oa << 24) | ((int) Math.round(r / a) << 16) | ((int) Math.round(g / a) << 8) | (int) Math.round(b / a);
            }
        }
        return new ArgbImage(newWidth, newHeight, out);
    }
}
