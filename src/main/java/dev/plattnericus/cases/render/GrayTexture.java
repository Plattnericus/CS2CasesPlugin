package dev.plattnericus.cases.render;

/** Single-channel texture with values in [0, 1] and bilinear sampling. */
public final class GrayTexture {

    private final int width;
    private final int height;
    private final float[] data;

    public GrayTexture(int width, int height, float[] data) {
        if (data.length != width * height) {
            throw new IllegalArgumentException("size mismatch");
        }
        this.width = width;
        this.height = height;
        this.data = data;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public float[] data() {
        return data;
    }

    public float at(int x, int y) {
        return data[y * width + x];
    }

    public float sampleWrap(double u, double v) {
        double fx = u - 0.5;
        double fy = v - 0.5;
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        float tx = (float) (fx - x0);
        float ty = (float) (fy - y0);
        int xa = Math.floorMod(x0, width);
        int xb = Math.floorMod(x0 + 1, width);
        int ya = Math.floorMod(y0, height);
        int yb = Math.floorMod(y0 + 1, height);
        return bilerp(xa, xb, ya, yb, tx, ty);
    }

    public float sampleClamp(double u, double v) {
        double fx = Math.max(0, Math.min(width - 1.0, u - 0.5));
        double fy = Math.max(0, Math.min(height - 1.0, v - 0.5));
        int x0 = (int) fx;
        int y0 = (int) fy;
        float tx = (float) (fx - x0);
        float ty = (float) (fy - y0);
        return bilerp(x0, Math.min(width - 1, x0 + 1), y0, Math.min(height - 1, y0 + 1), tx, ty);
    }

    private float bilerp(int xa, int xb, int ya, int yb, float tx, float ty) {
        float a = data[ya * width + xa];
        float b = data[ya * width + xb];
        float c = data[yb * width + xa];
        float d = data[yb * width + xb];
        float top = a + (b - a) * tx;
        float bottom = c + (d - c) * tx;
        return top + (bottom - top) * ty;
    }

    /** Bilinear resample to a new size (used when a layer has the wrong resolution). */
    public GrayTexture resized(int newWidth, int newHeight) {
        float[] out = new float[newWidth * newHeight];
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                out[y * newWidth + x] = sampleClamp((x + 0.5) * width / newWidth, (y + 0.5) * height / newHeight);
            }
        }
        return new GrayTexture(newWidth, newHeight, out);
    }
}
