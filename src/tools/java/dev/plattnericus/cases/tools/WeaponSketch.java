package dev.plattnericus.cases.tools;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * A weapon silhouette described as ordered material shapes in a 256x256 design space.
 * {@link #bake()} rasterises it at 4x supersampling and derives every default layer
 * (mask, paint area, base material, shadow, highlight, edge wear).
 */
final class WeaponSketch {

    enum Mat {
        PAINT(new Color(0x6E7378)),
        METAL(new Color(0x4F555C)),
        DARK(new Color(0x232528)),
        WOOD(new Color(0x6B4A2F)),
        LIGHT(new Color(0xA9AEB4)),
        CUT(null);

        final Color base;

        Mat(Color base) {
            this.base = base;
        }
    }

    record Part(Shape shape, Mat mat) {
    }

    static final int SIZE = 256;
    static final int SS = 4;
    static final int HI = SIZE * SS;

    final String id;
    final List<Part> parts = new ArrayList<>();
    private double rotation;

    WeaponSketch(String id) {
        this.id = id;
    }

    WeaponSketch add(Mat mat, Shape shape) {
        parts.add(new Part(shape, mat));
        return this;
    }

    WeaponSketch paint(Shape s) {
        return add(Mat.PAINT, s);
    }

    WeaponSketch metal(Shape s) {
        return add(Mat.METAL, s);
    }

    WeaponSketch dark(Shape s) {
        return add(Mat.DARK, s);
    }

    WeaponSketch wood(Shape s) {
        return add(Mat.WOOD, s);
    }

    WeaponSketch light(Shape s) {
        return add(Mat.LIGHT, s);
    }

    WeaponSketch cut(Shape s) {
        return add(Mat.CUT, s);
    }

    /** Rotates the finished sketch around the canvas centre (degrees, clockwise on screen). */
    WeaponSketch rotate(double degrees) {
        this.rotation = degrees;
        return this;
    }

    // ------------------------------------------------------------ shape helpers

    static Shape rect(double x, double y, double w, double h) {
        return new Rectangle2D.Double(x, y, w, h);
    }

    static Shape rrect(double x, double y, double w, double h, double r) {
        return new RoundRectangle2D.Double(x, y, w, h, r, r);
    }

    static Shape circle(double cx, double cy, double r) {
        return new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
    }

    static Shape ellipse(double cx, double cy, double rx, double ry) {
        return new Ellipse2D.Double(cx - rx, cy - ry, rx * 2, ry * 2);
    }

    static Shape poly(double... xy) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            p.lineTo(xy[i], xy[i + 1]);
        }
        p.closePath();
        return p;
    }

    /** Smooth closed outline through the given points (quadratic midpoint spline). */
    static Shape curve(double... xy) {
        int n = xy.length / 2;
        Path2D.Double p = new Path2D.Double();
        double mx = (xy[0] + xy[2]) / 2;
        double my = (xy[1] + xy[3]) / 2;
        p.moveTo(mx, my);
        for (int i = 1; i <= n; i++) {
            int a = i % n;
            int b = (i + 1) % n;
            double nx = (xy[a * 2] + xy[b * 2]) / 2;
            double ny = (xy[a * 2 + 1] + xy[b * 2 + 1]) / 2;
            p.quadTo(xy[a * 2], xy[a * 2 + 1], nx, ny);
        }
        p.closePath();
        return p;
    }

    static Shape rot(Shape s, double degrees, double cx, double cy) {
        return AffineTransform.getRotateInstance(Math.toRadians(degrees), cx, cy).createTransformedShape(s);
    }

    /** Ring: circle with a circular hole, as two parts. */
    WeaponSketch ring(Mat mat, double cx, double cy, double outer, double inner) {
        add(mat, circle(cx, cy, outer));
        return cut(circle(cx, cy, inner));
    }

    // ------------------------------------------------------------ baking

    static final class Baked {
        final float[] mask = new float[SIZE * SIZE];
        final float[] paint = new float[SIZE * SIZE];
        final int[] base = new int[SIZE * SIZE];
        final float[] shadow = new float[SIZE * SIZE];
        final float[] highlight = new float[SIZE * SIZE];
        final float[] wear = new float[SIZE * SIZE];
    }

    Baked bake() {
        // 1. material id buffer at 4x without anti-aliasing (exact coverage after downsampling)
        BufferedImage ids = new BufferedImage(HI, HI, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = ids.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, HI, HI);
        AffineTransform tx = new AffineTransform();
        tx.scale(SS, SS);
        if (rotation != 0) {
            tx.rotate(Math.toRadians(rotation), SIZE / 2.0, SIZE / 2.0);
        }
        g.setTransform(tx);
        for (Part part : parts) {
            int code = part.mat == Mat.CUT ? 0 : part.mat.ordinal() + 1;
            g.setColor(new Color(code, 0, 0));
            g.fill(part.shape);
        }
        g.dispose();

        int[] hi = new int[HI * HI];
        for (int y = 0; y < HI; y++) {
            for (int x = 0; x < HI; x++) {
                hi[y * HI + x] = (ids.getRGB(x, y) >> 16) & 0xFF;
            }
        }

        // 2. distance to silhouette edge (chamfer) in hi-res pixels
        float[] dist = chamfer(hi);
        // 3. seams: material boundaries inside the silhouette
        float[] seam = new float[HI * HI];
        for (int y = 1; y < HI - 1; y++) {
            for (int x = 1; x < HI - 1; x++) {
                int c = hi[y * HI + x];
                if (c == 0) {
                    continue;
                }
                int r = hi[y * HI + x + 1];
                int d = hi[(y + 1) * HI + x];
                if ((r != c && r != 0) || (d != c && d != 0)) {
                    seam[y * HI + x] = 1;
                }
            }
        }

        Baked out = new Baked();
        Mat[] mats = Mat.values();
        float[] heightField = new float[SIZE * SIZE];
        float[] seamLow = new float[SIZE * SIZE];
        float[] distLow = new float[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int covered = 0;
                int painted = 0;
                double r = 0, gg = 0, b = 0;
                float seamSum = 0;
                float distSum = 0;
                for (int sy = 0; sy < SS; sy++) {
                    for (int sx = 0; sx < SS; sx++) {
                        int idx = (y * SS + sy) * HI + (x * SS + sx);
                        int code = hi[idx];
                        seamSum += seam[idx];
                        distSum += dist[idx];
                        if (code == 0) {
                            continue;
                        }
                        Mat m = mats[code - 1];
                        covered++;
                        if (m == Mat.PAINT) {
                            painted++;
                        }
                        r += m.base.getRed();
                        gg += m.base.getGreen();
                        b += m.base.getBlue();
                    }
                }
                int i = y * SIZE + x;
                int samples = SS * SS;
                out.mask[i] = covered / (float) samples;
                out.paint[i] = covered == 0 ? 0 : painted / (float) covered;
                if (covered > 0) {
                    out.base[i] = 0xFF000000 | ((int) (r / covered) << 16) | ((int) (gg / covered) << 8) | (int) (b / covered);
                }
                seamLow[i] = Math.min(1, seamSum / SS);
                distLow[i] = distSum / samples / SS;
                heightField[i] = (float) smooth(Math.min(1, distLow[i] / 5.0));
            }
        }

        // 4. lighting from upper-left: diffuse shading + tight specular
        double lx = -0.45, ly = -0.65, lz = 0.62;
        double ln = Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= ln;
        ly /= ln;
        lz /= ln;
        double hx = lx, hy = ly, hz = lz + 1;
        double hn = Math.sqrt(hx * hx + hy * hy + hz * hz);
        hx /= hn;
        hy /= hn;
        hz /= hn;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int i = y * SIZE + x;
                if (out.mask[i] <= 0) {
                    out.shadow[i] = 1;
                    continue;
                }
                double dx = sample(heightField, x + 1, y) - sample(heightField, x - 1, y);
                double dy = sample(heightField, x, y + 1) - sample(heightField, x, y - 1);
                double nx = -dx * 2.2, ny = -dy * 2.2, nz = 1;
                double nn = Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= nn;
                ny /= nn;
                nz /= nn;
                double diffuse = Math.max(0, nx * lx + ny * ly + nz * lz);
                double spec = Math.pow(Math.max(0, nx * hx + ny * hy + nz * hz), 28);
                double rim = (1 - heightField[i]) * Math.max(0, -ny) * 0.6;
                double shade = 0.42 + 0.58 * diffuse - seamLow[i] * 0.45;
                // ambient occlusion towards the lower silhouette edge
                shade -= (1 - heightField[i]) * Math.max(0, ny) * 0.25;
                out.shadow[i] = (float) PatternTextureGenerator.clamp(shade);
                out.highlight[i] = (float) PatternTextureGenerator.clamp(spec * 0.9 + rim * 0.5);
                double edgeWear = 1 - Math.min(1, distLow[i] / 9.0);
                out.wear[i] = (float) PatternTextureGenerator.clamp(edgeWear * 0.85 + seamLow[i] * 0.4);
            }
        }
        return out;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static float sample(float[] f, int x, int y) {
        x = Math.max(0, Math.min(SIZE - 1, x));
        y = Math.max(0, Math.min(SIZE - 1, y));
        return f[y * SIZE + x];
    }

    private static float[] chamfer(int[] hi) {
        float inf = 1e6f;
        float[] d = new float[HI * HI];
        for (int i = 0; i < d.length; i++) {
            d[i] = hi[i] == 0 ? 0 : inf;
        }
        float a = 1f, b = 1.4142f;
        for (int y = 0; y < HI; y++) {
            for (int x = 0; x < HI; x++) {
                int i = y * HI + x;
                if (d[i] == 0) {
                    continue;
                }
                float v = d[i];
                if (x > 0) v = Math.min(v, d[i - 1] + a);
                if (y > 0) v = Math.min(v, d[i - HI] + a);
                if (x > 0 && y > 0) v = Math.min(v, d[i - HI - 1] + b);
                if (x < HI - 1 && y > 0) v = Math.min(v, d[i - HI + 1] + b);
                if (x == 0 || y == 0 || x == HI - 1 || y == HI - 1) v = Math.min(v, 1);
                d[i] = v;
            }
        }
        for (int y = HI - 1; y >= 0; y--) {
            for (int x = HI - 1; x >= 0; x--) {
                int i = y * HI + x;
                if (d[i] == 0) {
                    continue;
                }
                float v = d[i];
                if (x < HI - 1) v = Math.min(v, d[i + 1] + a);
                if (y < HI - 1) v = Math.min(v, d[i + HI] + a);
                if (x < HI - 1 && y < HI - 1) v = Math.min(v, d[i + HI + 1] + b);
                if (x > 0 && y < HI - 1) v = Math.min(v, d[i + HI - 1] + b);
                d[i] = v;
            }
        }
        return d;
    }
}
