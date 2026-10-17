package dev.plattnericus.cases.render;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Where a weapon canvas lands in its square item texture. Skin sprites turn knives to the vanilla
 * sword diagonal, then crop and fit the silhouette; inspect rigs keep the whole canvas at a fixed inset
 * and turn reverse-grip knives half a revolution. The pack exporter draws both with these frames and
 * the server maps grips through them, so both always agree.
 *
 * @param canvas  source canvas edge in pixels
 * @param turned  whether the canvas was turned by -45 degrees (knife sprites)
 * @param flipped whether the canvas was turned half a revolution (reverse-grip rigs)
 * @param minX    left edge of the cropped silhouette on the (turned) canvas
 * @param minY    top edge of the cropped silhouette on the (turned) canvas
 * @param width   cropped silhouette width on the canvas
 * @param height  cropped silhouette height on the canvas
 * @param fittedWidth  silhouette width in the sprite
 * @param fittedHeight silhouette height in the sprite
 */
public record SpriteFrame(int canvas, boolean turned, boolean flipped, int minX, int minY, int width, int height, int fittedWidth,
                          int fittedHeight) {

    /** Edge of every exported skin sprite and inspect rig texture. */
    public static final int SIZE = 128;
    /** Edge of the canvas inside a rig texture; the rest is a transparent inset on every side. */
    public static final int RIG_CONTENT = 120;
    private static final double TURN = Math.toRadians(-45);

    /** Frame of a rendered skin (alpha comes from the weapon mask). */
    public static SpriteFrame of(ArgbImage canvas, boolean knife) {
        ArgbImage source = knife ? turn(canvas) : canvas;
        int minX = source.width(), minY = source.height(), maxX = -1, maxY = -1;
        for (int y = 0; y < source.height(); y++) {
            for (int x = 0; x < source.width(); x++) {
                if ((source.get(x, y) >>> 24) > 10) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return new SpriteFrame(canvas.width(), knife, false, 0, 0, source.width(), source.height(), SIZE, SIZE);
        }
        int w = maxX - minX + 1, h = maxY - minY + 1;
        double scale = (SIZE - 2.0) / Math.max(w, h);
        return new SpriteFrame(canvas.width(), knife, false, minX, minY, w, h,
                Math.max(1, (int) Math.round(w * scale)), Math.max(1, (int) Math.round(h * scale)));
    }

    /** Frame computed from the weapon mask alone, identical to the rendered skins of that weapon. */
    public static SpriteFrame of(WeaponAssets weapon, boolean knife) {
        int[] pixels = new int[weapon.size() * weapon.size()];
        for (int i = 0; i < pixels.length; i++) {
            float alpha = weapon.mask()[i];
            pixels[i] = alpha <= 0f ? 0 : Math.round(alpha * 255) << 24 | 0xffffff;
        }
        return of(new ArgbImage(weapon.size(), weapon.size(), pixels), knife);
    }

    /** Frame of a skin's inspect rig: the whole canvas at the fixed inset, half turned for reverse grips. */
    public static SpriteFrame rig(int canvas, boolean flipped) {
        return new SpriteFrame(canvas, false, flipped, 0, 0, canvas, canvas, RIG_CONTENT, RIG_CONTENT);
    }

    public static SpriteFrame rig(WeaponAssets weapon, boolean flipped) {
        return rig(weapon.size(), flipped);
    }

    /** The sword-diagonal turn used for knife sprites. */
    public static ArgbImage turn(ArgbImage canvas) {
        BufferedImage src = canvas.toBufferedImage();
        BufferedImage rotated = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = rotated.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setTransform(AffineTransform.getRotateInstance(TURN, src.getWidth() / 2.0, src.getHeight() / 2.0));
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return ArgbImage.from(rotated);
    }

    /** Normalised canvas point (x right, y down) to normalised sprite point (x right, y down). */
    public double[] map(double x, double y) {
        double px = x * canvas, py = y * canvas;
        if (flipped) {
            px = canvas - px;
            py = canvas - py;
        }
        if (turned) {
            double c = canvas / 2.0, dx = px - c, dy = py - c;
            px = c + dx * Math.cos(TURN) - dy * Math.sin(TURN);
            py = c + dx * Math.sin(TURN) + dy * Math.cos(TURN);
        }
        return new double[]{(offsetX() + (px - minX) * fittedWidth / width) / SIZE,
                (offsetY() + (py - minY) * fittedHeight / height) / SIZE};
    }

    /** Left padding of the fitted silhouette in the sprite. */
    public int offsetX() {
        return (SIZE - fittedWidth) / 2;
    }

    /** Top padding of the fitted silhouette in the sprite. */
    public int offsetY() {
        return (SIZE - fittedHeight) / 2;
    }

    /** Sprite edges per canvas edge: how much the fit enlarged the weapon. */
    public double zoom() {
        return fittedWidth / (double) width * canvas / SIZE;
    }
}
