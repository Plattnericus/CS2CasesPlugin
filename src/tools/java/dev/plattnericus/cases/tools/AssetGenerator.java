package dev.plattnericus.cases.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Writes the default texture set: per-weapon layers (mask, paint, base, shadow, highlight, wear),
 * the pattern library and the shared scratch texture. Run via {@code gradlew generateAssets}.
 */
public final class AssetGenerator {

    private AssetGenerator() {
    }

    public static void main(String[] args) throws IOException {
        File root = new File(args.length > 0 ? args[0] : "textures");
        File weapons = new File(root, "weapons");
        File patterns = new File(root, "patterns");
        File wear = new File(root, "wear");
        mkdirs(weapons, patterns, wear);

        List<WeaponSketch> sketches = WeaponLibrary.all();
        int sheetCols = 8;
        int sheetRows = (sketches.size() + sheetCols - 1) / sheetCols;
        BufferedImage sheet = new BufferedImage(sheetCols * 128, sheetRows * 128, BufferedImage.TYPE_INT_ARGB);
        int index = 0;
        for (WeaponSketch sketch : sketches) {
            WeaponSketch.Baked baked = sketch.bake();
            File dir = new File(weapons, sketch.id);
            mkdirs(dir);
            writeGray(new File(dir, "mask.png"), baked.mask, WeaponSketch.SIZE, true);
            writeGray(new File(dir, "paint.png"), maskedPaint(baked), WeaponSketch.SIZE, false);
            writeArgb(new File(dir, "base.png"), withAlpha(baked.base, baked.mask), WeaponSketch.SIZE);
            writeGray(new File(dir, "shadow.png"), baked.shadow, WeaponSketch.SIZE, false);
            writeGray(new File(dir, "highlight.png"), baked.highlight, WeaponSketch.SIZE, false);
            writeGray(new File(dir, "wear.png"), baked.wear, WeaponSketch.SIZE, false);
            drawPreview(sheet, baked, (index % sheetCols) * 128, (index / sheetCols) * 128);
            index++;
        }
        File debug = new File(root.getParentFile().getParentFile().getParentFile().getParentFile().getParentFile(), "build/asset-debug");
        mkdirs(debug);
        ImageIO.write(sheet, "png", new File(debug, "weapons.png"));

        PatternTextureGenerator gen = new PatternTextureGenerator();
        Map<String, float[]> textures = gen.generateAll();
        BufferedImage psheet = new BufferedImage(8 * 128, ((textures.size() + 7) / 8) * 128, BufferedImage.TYPE_INT_RGB);
        int p = 0;
        for (Map.Entry<String, float[]> e : textures.entrySet()) {
            writeGray(new File(patterns, e.getKey() + ".png"), e.getValue(), PatternTextureGenerator.N, false);
            float[] f = e.getValue();
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    int v = (int) (f[(y * 4) * PatternTextureGenerator.N + x * 4] * 255);
                    psheet.setRGB((p % 8) * 128 + x, (p / 8) * 128 + y, (v << 16) | (v << 8) | v);
                }
            }
            p++;
        }
        ImageIO.write(psheet, "png", new File(debug, "patterns.png"));

        writeGray(new File(wear, "scratches.png"), scratches(), 256, false);
        System.out.println("Generated " + sketches.size() + " weapons and " + textures.size() + " patterns into " + root);
    }

    private static float[] maskedPaint(WeaponSketch.Baked b) {
        float[] out = new float[b.paint.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = b.paint[i] * (b.mask[i] > 0 ? 1 : 0);
        }
        return out;
    }

    private static int[] withAlpha(int[] rgb, float[] alpha) {
        int[] out = new int[rgb.length];
        for (int i = 0; i < rgb.length; i++) {
            int a = Math.round(alpha[i] * 255);
            out[i] = (a << 24) | (rgb[i] & 0xFFFFFF);
        }
        return out;
    }

    /** Tileable scratch/chip susceptibility map; bright = wears first. */
    private static float[] scratches() {
        int n = 256;
        TileNoise noise = new TileNoise(77);
        float[] f = new float[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double chips = noise.fbm(x, y, n, 8, 4, 0.6);
                f[y * n + x] = (float) (Math.pow(chips, 1.6) * 0.75);
            }
        }
        SplittableRandom r = new SplittableRandom(99);
        for (int s = 0; s < 260; s++) {
            double x = r.nextDouble() * n;
            double y = r.nextDouble() * n;
            double a = r.nextDouble() * Math.PI;
            double len = 6 + r.nextDouble() * 40;
            float strength = (float) (0.55 + r.nextDouble() * 0.45);
            for (double t = 0; t < len; t += 0.5) {
                int px = Math.floorMod((int) Math.round(x + Math.cos(a) * t), n);
                int py = Math.floorMod((int) Math.round(y + Math.sin(a) * t), n);
                int i = py * n + px;
                f[i] = Math.max(f[i], strength * (float) (1 - Math.abs(t / len - 0.5)));
            }
        }
        return f;
    }

    private static void drawPreview(BufferedImage sheet, WeaponSketch.Baked b, int ox, int oy) {
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int i = (y * 2) * WeaponSketch.SIZE + x * 2;
                float a = b.mask[i];
                if (a <= 0) {
                    sheet.setRGB(ox + x, oy + y, 0xFF1A1C20);
                    continue;
                }
                int base = b.base[i];
                double shade = b.shadow[i];
                double hl = b.highlight[i];
                int r = (int) Math.min(255, ((base >> 16) & 0xFF) * shade + hl * 255);
                int g = (int) Math.min(255, ((base >> 8) & 0xFF) * shade + hl * 255);
                int bl = (int) Math.min(255, (base & 0xFF) * shade + hl * 255);
                if (b.paint[i] > 0.5) {
                    r = (int) Math.min(255, r * 0.6 + 90 * shade);
                    g = (int) Math.min(255, g * 0.6 + 60 * shade);
                }
                sheet.setRGB(ox + x, oy + y, 0xFF000000 | (r << 16) | (g << 8) | bl);
            }
        }
    }

    private static void writeGray(File file, float[] values, int size, boolean asAlpha) throws IOException {
        BufferedImage img;
        if (asAlpha) {
            img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            for (int i = 0; i < values.length; i++) {
                int a = Math.round(clamp(values[i]) * 255);
                img.setRGB(i % size, i / size, (a << 24) | 0xFFFFFF);
            }
        } else {
            img = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY);
            for (int i = 0; i < values.length; i++) {
                int v = Math.round(clamp(values[i]) * 255);
                img.getRaster().setSample(i % size, i / size, 0, v);
            }
        }
        ImageIO.write(img, "png", file);
    }

    private static void writeArgb(File file, int[] argb, int size) throws IOException {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, size, size, argb, 0, size);
        ImageIO.write(img, "png", file);
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static void mkdirs(File... dirs) throws IOException {
        for (File d : dirs) {
            if (!d.isDirectory() && !d.mkdirs()) {
                throw new IOException("Cannot create " + d);
            }
        }
    }
}
