package dev.plattnericus.cases.pattern;

import dev.plattnericus.cases.catalog.FinishVariant;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.catalog.TransformRules;
import dev.plattnericus.cases.util.StableHash;
import dev.plattnericus.cases.util.StableRandom;

import java.util.List;

/**
 * Seed → pattern mapping. Every value is drawn from a {@link StableRandom} seeded only with the
 * skin id and the pattern seed, so "skin + seed" always yields the same pattern, independent of
 * owner, float, creation time, restarts or reloads.
 */
public final class PatternEngine {

    private PatternEngine() {
    }

    public static PatternTransform transform(SkinDefinition skin, int seed) {
        if (!skin.finish().patterned()) {
            TransformRules r = skin.finish().style().transform();
            return new PatternTransform((r.offsetMinX() + r.offsetMaxX()) / 2, (r.offsetMinY() + r.offsetMaxY()) / 2,
                    r.rotationMin(), (r.scaleMin() + r.scaleMax()) / 2, false);
        }
        TransformRules r = skin.finish().style().transform();
        StableRandom rng = new StableRandom(StableHash.of(skin.id() + "#pattern", seed));
        // all draws happen unconditionally so enabling one rule never shifts the others
        double ox = rng.range(r.offsetMinX(), r.offsetMaxX());
        double oy = rng.range(r.offsetMinY(), r.offsetMaxY());
        double rot = rng.range(r.rotationMin(), r.rotationMax());
        double scale = rng.range(r.scaleMin(), r.scaleMax());
        boolean mirror = rng.nextBoolean();
        if (!r.translate()) {
            ox = (r.offsetMinX() + r.offsetMaxX()) / 2;
            oy = (r.offsetMinY() + r.offsetMaxY()) / 2;
        }
        return new PatternTransform(ox, oy, rot, scale, r.mirror() && mirror);
    }

    /** Deterministic weighted pick of the finish variant (Doppler phase etc.), or null. */
    public static FinishVariant variant(SkinDefinition skin, int seed) {
        List<FinishVariant> variants = skin.finish().variants();
        if (variants.isEmpty()) {
            return null;
        }
        double total = 0;
        for (FinishVariant v : variants) {
            total += Math.max(0, v.weight());
        }
        double roll = new StableRandom(StableHash.of(skin.id() + "#variant", seed)).nextDouble() * total;
        for (FinishVariant v : variants) {
            roll -= Math.max(0, v.weight());
            if (roll < 0) {
                return v;
            }
        }
        return variants.getLast();
    }

    /** Wear layout seed: per instance only if the finish allows it, otherwise fixed per skin + pattern. */
    public static long wearLayout(SkinDefinition skin, int seed, long instanceWearSeed) {
        long base = StableHash.of(skin.id() + "#wear", seed);
        return skin.finish().perInstanceWear() ? StableHash.avalanche(base ^ instanceWearSeed) : base;
    }
}
