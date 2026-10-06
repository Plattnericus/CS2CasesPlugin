package dev.plattnericus.cases.render;

import dev.plattnericus.cases.catalog.Finish;
import dev.plattnericus.cases.catalog.FinishVariant;
import dev.plattnericus.cases.catalog.Palette;
import dev.plattnericus.cases.catalog.PaletteMapping;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.catalog.TransformRules;
import dev.plattnericus.cases.pattern.PatternEngine;
import dev.plattnericus.cases.pattern.PatternTransform;
import dev.plattnericus.cases.util.StableRandom;

/**
 * Layer compositor. Order per pixel:
 * <ol>
 *   <li>pattern texture sampled through the seed transform (inside the paint mask only)</li>
 *   <li>palette mapping (or raw color for colored textures)</li>
 *   <li>optional fixed overlay (logos, details)</li>
 *   <li>wear: paint chips where edge-wear + scratch susceptibility exceed the float threshold,
 *       revealing the base layer underneath</li>
 *   <li>shadow (multiply) and highlight (screen)</li>
 *   <li>alpha from the weapon mask</li>
 * </ol>
 * Pure function of its inputs; safe to call from any thread.
 */
public final class SkinRenderer {

    /** Canvas size the transform parameters are defined for. */
    private static final double REFERENCE_SIZE = 256.0;

    private final TextureStore textures;
    private final RenderSettings settings;

    public SkinRenderer(TextureStore textures, RenderSettings settings) {
        this.textures = textures;
        this.settings = settings;
    }

    public TextureStore textures() {
        return textures;
    }

    public RenderedSkin render(SkinDefinition skin, int seed, double floatValue, long instanceWearSeed) throws TextureException {
        Finish finish = skin.finish();
        WeaponAssets assets = textures.weapon(skin.weapon().id(), skin.weapon().textureFolder());
        int size = assets.size();
        int n = size * size;

        FinishVariant variant = PatternEngine.variant(skin, seed);
        Palette palette = variant != null && variant.palette() != null ? variant.palette() : finish.palette();
        String texturePath = variant != null && variant.texture() != null ? variant.texture() : finish.style().texture();
        PaletteMapping mapping = palette.mapping();

        GrayTexture gray = null;
        ArgbImage colorTex = null;
        if (mapping == PaletteMapping.NONE) {
            colorTex = textures.color("patterns/" + texturePath);
        } else {
            gray = textures.gray("patterns/" + texturePath);
        }
        int texW = gray != null ? gray.width() : colorTex.width();
        int texH = gray != null ? gray.height() : colorTex.height();

        TransformRules rules = finish.style().transform();
        PatternTransform t = PatternEngine.transform(skin, seed);
        double rad = Math.toRadians(t.rotation());
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double k = rules.baseScale() / t.scale() * (REFERENCE_SIZE / size);
        double centre = size / 2.0;
        double offU = t.offsetX() * texW;
        double offV = t.offsetY() * texH;

        int[] paintColors = new int[n];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                if (assets.paint()[i] <= 0f || assets.mask()[i] <= 0f) {
                    continue;
                }
                double dx = x + 0.5 - centre;
                double dy = y + 0.5 - centre;
                if (t.mirror()) {
                    dx = -dx;
                }
                double u = (dx * cos - dy * sin) * k + offU;
                double v = (dx * sin + dy * cos) * k + offV;
                int color;
                if (gray != null) {
                    float lum = rules.clamp() ? gray.sampleClamp(u, v) : gray.sampleWrap(u, v);
                    color = palette.map(Math.round(lum * 255));
                } else {
                    int tx = rules.clamp() ? clampInt((int) u, texW) : Math.floorMod((int) Math.floor(u), texW);
                    int ty = rules.clamp() ? clampInt((int) v, texH) : Math.floorMod((int) Math.floor(v), texH);
                    color = colorTex.get(tx, ty) & 0xFFFFFF;
                }
                paintColors[i] = color;
            }
        }
        PaintSample sample = new PaintSample(size, paintColors, assets.paint());

        ArgbImage overlay = null;
        if (finish.overlay() != null) {
            overlay = textures.color("overlays/" + finish.overlay());
            if (overlay.width() != size || overlay.height() != size) {
                overlay = overlay.scaledTo(size, size);
            }
        }

        // wear
        double wearAmount = finish.wearable() ? settings.wearAmount(floatValue) * finish.style().wearStrength() : 0;
        GrayTexture scratches = wearAmount > 0 && textures.exists("wear/scratches.png") ? textures.gray("wear/scratches.png") : null;
        StableRandom wearRng = new StableRandom(PatternEngine.wearLayout(skin, seed, instanceWearSeed));
        double wox = wearRng.nextDouble() * (scratches == null ? 1 : scratches.width());
        double woy = wearRng.nextDouble() * (scratches == null ? 1 : scratches.height());
        boolean wflip = wearRng.nextBoolean();
        double threshold = 1 - wearAmount;
        double metallic = finish.style().metallic();

        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            float alpha = assets.mask()[i];
            if (alpha <= 0f) {
                continue;
            }
            int x = i % size;
            int y = i / size;
            int base = assets.base()[i] & 0xFFFFFF;
            float paintCov = assets.paint()[i];
            double r = (base >> 16) & 0xFF;
            double g = (base >> 8) & 0xFF;
            double b = base & 0xFF;
            double exposed = 0;
            if (paintCov > 0f) {
                int pc = paintColors[i];
                if (overlay != null) {
                    int oc = overlay.pixels()[i];
                    double oa = ((oc >>> 24) & 0xFF) / 255.0;
                    if (oa > 0) {
                        pc = blend(pc, oc & 0xFFFFFF, oa);
                    }
                }
                double pr = (pc >> 16) & 0xFF;
                double pg = (pc >> 8) & 0xFF;
                double pb = pc & 0xFF;
                double chip = 0;
                if (wearAmount > 0) {
                    double scratch = 0;
                    if (scratches != null) {
                        double sx = (wflip ? size - x : x) * (REFERENCE_SIZE / size) + wox;
                        scratch = scratches.sampleWrap(sx, y * (REFERENCE_SIZE / size) + woy);
                    }
                    double damage = assets.wear()[i] * settings.edgeWeight() + scratch * settings.scratchWeight();
                    chip = smoothstep(threshold - 0.05, threshold + 0.03, damage);
                    double grime = 1 - settings.grime() * wearAmount * scratch;
                    pr *= grime;
                    pg *= grime;
                    pb *= grime;
                }
                // exposed metal is slightly brighter than the base layer
                double mr = Math.min(255, r * 1.18 + 12);
                double mg = Math.min(255, g * 1.18 + 12);
                double mb = Math.min(255, b * 1.18 + 12);
                double cov = paintCov * (1 - chip);
                r = r + (pr - r) * cov;
                g = g + (pg - g) * cov;
                b = b + (pb - b) * cov;
                if (chip > 0) {
                    double e = paintCov * chip;
                    r = r + (mr - r) * e;
                    g = g + (mg - g) * e;
                    b = b + (mb - b) * e;
                    exposed = e;
                }
            }
            double shade = assets.shadow()[i];
            double hl = assets.highlight()[i] * (0.35 + 0.65 * Math.max(metallic, exposed));
            r = r * shade;
            g = g * shade;
            b = b * shade;
            r = r + (255 - r) * hl;
            g = g + (255 - g) * hl;
            b = b + (255 - b) * hl;
            int a = Math.round(alpha * 255);
            out[i] = (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
        }
        return new RenderedSkin(new ArgbImage(size, size, out), sample);
    }

    /** Paint-only pass for pattern analysis; skips wear and lighting. */
    public PaintSample paint(SkinDefinition skin, int seed) throws TextureException {
        return render(skin, seed, 0, 0).paint();
    }

    private static int blend(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int clampInt(int v, int max) {
        return v < 0 ? 0 : Math.min(max - 1, v);
    }

    private static int clamp(double v) {
        return v < 0 ? 0 : (v > 255 ? 255 : (int) Math.round(v));
    }

    private static double smoothstep(double a, double b, double v) {
        double t = Math.max(0, Math.min(1, (v - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
