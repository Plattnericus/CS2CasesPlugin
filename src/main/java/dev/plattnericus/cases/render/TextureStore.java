package dev.plattnericus.cases.render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads PNG layers from the plugin's {@code textures/} folder. Every loaded layer is cached for the
 * lifetime of this store; a reload simply replaces the store. Problems are collected with file
 * name and reason instead of being thrown into the server.
 */
public final class TextureStore {

    public static final int DEFAULT_SIZE = 256;

    private final File root;
    private final Map<String, GrayTexture> grays = new ConcurrentHashMap<>();
    private final Map<String, ArgbImage> colors = new ConcurrentHashMap<>();
    private final Map<String, WeaponAssets> weapons = new ConcurrentHashMap<>();
    private final List<String> warnings = java.util.Collections.synchronizedList(new ArrayList<>());

    public TextureStore(File root) {
        this.root = root;
    }

    public File root() {
        return root;
    }

    public List<String> drainWarnings() {
        synchronized (warnings) {
            List<String> copy = new ArrayList<>(warnings);
            warnings.clear();
            return copy;
        }
    }

    private BufferedImage read(String relative) throws TextureException {
        File file = new File(root, relative);
        if (!file.isFile()) {
            throw new TextureException("missing file " + relative);
        }
        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                throw new TextureException("not a readable PNG: " + relative);
            }
            if (img.getWidth() < 8 || img.getHeight() < 8) {
                throw new TextureException("image too small (" + img.getWidth() + "x" + img.getHeight() + "): " + relative);
            }
            return img;
        } catch (IOException e) {
            throw new TextureException("corrupt PNG " + relative + " (" + e.getMessage() + ")");
        }
    }

    /** Luminance texture; alpha is ignored. */
    public GrayTexture gray(String relative) throws TextureException {
        GrayTexture cached = grays.get(relative);
        if (cached != null) {
            return cached;
        }
        BufferedImage img = read(relative);
        GrayTexture tex = new GrayTexture(img.getWidth(), img.getHeight(), luminance(img));
        grays.put(relative, tex);
        return tex;
    }

    /**
     * Raw luminance. Grayscale PNGs are read from the raster directly: {@code getRGB} would run
     * them through a gamma conversion and brighten every value.
     */
    private static float[] luminance(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        float[] data = new float[w * h];
        java.awt.image.Raster raster = img.getRaster();
        boolean grayModel = img.getColorModel().getColorSpace().getType() == java.awt.color.ColorSpace.TYPE_GRAY;
        if (grayModel && raster.getNumBands() <= 2) {
            float max = (1 << img.getColorModel().getComponentSize(0)) - 1;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    data[y * w + x] = raster.getSample(x, y, 0) / max;
                }
            }
            return data;
        }
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            data[i] = (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF)) / 255f;
        }
        return data;
    }

    public ArgbImage color(String relative) throws TextureException {
        ArgbImage cached = colors.get(relative);
        if (cached != null) {
            return cached;
        }
        ArgbImage img = ArgbImage.from(read(relative));
        colors.put(relative, img);
        return img;
    }

    public boolean exists(String relative) {
        return new File(root, relative).isFile();
    }

    /**
     * Loads a weapon's layers. Only the mask is mandatory; missing optional layers fall back to
     * neutral values and are reported once as warnings.
     */
    public WeaponAssets weapon(String weaponId, String folder) throws TextureException {
        WeaponAssets cached = weapons.get(weaponId);
        if (cached != null) {
            return cached;
        }
        String dir = folder.endsWith("/") ? folder : folder + "/";
        BufferedImage maskImg = read(dir + "mask.png");
        if (maskImg.getWidth() != maskImg.getHeight()) {
            throw new TextureException("mask must be square, is " + maskImg.getWidth() + "x" + maskImg.getHeight() + ": " + dir + "mask.png");
        }
        int size = maskImg.getWidth();
        float[] mask = alphaOrLuminance(maskImg, dir + "mask.png");
        float[] paint = optionalGray(dir + "paint.png", size, mask);
        float[] shadow = optionalGray(dir + "shadow.png", size, filled(size, 1f));
        float[] highlight = optionalGray(dir + "highlight.png", size, filled(size, 0f));
        float[] wear = optionalGray(dir + "wear.png", size, filled(size, 0.3f));
        int[] base;
        if (exists(dir + "base.png")) {
            ArgbImage b = color(dir + "base.png");
            if (b.width() != size || b.height() != size) {
                warnings.add(dir + "base.png has size " + b.width() + "x" + b.height() + ", expected " + size + "x" + size + " - resampled");
                b = b.scaledTo(size, size);
            }
            base = b.pixels();
        } else {
            warnings.add("missing optional layer " + dir + "base.png - using neutral metal");
            base = new int[size * size];
            java.util.Arrays.fill(base, 0xFF5A5F66);
        }
        WeaponAssets assets = new WeaponAssets(weaponId, size, mask, paint, base, shadow, highlight, wear);
        weapons.put(weaponId, assets);
        return assets;
    }

    private float[] alphaOrLuminance(BufferedImage img, String name) throws TextureException {
        int w = img.getWidth();
        float[] out;
        if (img.getColorModel().hasAlpha()) {
            int[] px = img.getRGB(0, 0, w, w, null, 0, w);
            out = new float[w * w];
            for (int i = 0; i < px.length; i++) {
                out[i] = ((px[i] >>> 24) & 0xFF) / 255f;
            }
        } else {
            // masks without alpha channel: white = weapon, black = empty
            out = luminance(img);
        }
        int opaque = 0;
        for (float v : out) {
            if (v > 0.5f) {
                opaque++;
            }
        }
        if (opaque == 0) {
            throw new TextureException("mask has no visible area (fully transparent): " + name);
        }
        if (opaque == out.length) {
            throw new TextureException("mask has no transparent area, the weapon shape cannot be cut out: " + name);
        }
        return out;
    }

    private float[] optionalGray(String relative, int size, float[] fallback) throws TextureException {
        if (!exists(relative)) {
            warnings.add("missing optional layer " + relative + " - using fallback");
            return fallback;
        }
        GrayTexture tex = gray(relative);
        if (tex.width() != size || tex.height() != size) {
            warnings.add(relative + " has size " + tex.width() + "x" + tex.height() + ", expected " + size + "x" + size + " - resampled");
            tex = tex.resized(size, size);
        }
        return tex.data();
    }

    private static float[] filled(int size, float v) {
        float[] f = new float[size * size];
        java.util.Arrays.fill(f, v);
        return f;
    }
}
