package dev.plattnericus.cases.reward;

import dev.plattnericus.cases.catalog.CaseDefinition;
import dev.plattnericus.cases.catalog.Catalog;
import dev.plattnericus.cases.catalog.Rarity;
import dev.plattnericus.cases.catalog.SkinDefinition;

import java.security.SecureRandom;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Server-side reward generation in fixed order: rarity, skin, StatTrak, float, pattern seed and
 * wear layout seed. Each value is an independent draw from a cryptographic generator; nothing
 * depends on time, player input or previous openings (no pity system).
 */
public final class RewardRoller {

    private final SecureRandom random = new SecureRandom();

    public RolledReward roll(CaseDefinition def, Catalog catalog) {
        Rarity rarity = rollRarity(def, catalog, random);
        List<SkinDefinition> pool = def.skins(rarity);
        SkinDefinition skin = pool.get(random.nextInt(pool.size()));
        boolean statTrak = skin.statTrakEligible() && random.nextDouble() < def.statTrakChance();
        double floatValue = skin.minFloat() + random.nextDouble() * (skin.maxFloat() - skin.minFloat());
        int seedMin = catalog.patterns().seedMin();
        int seedMax = catalog.patterns().seedMax();
        int pattern = seedMin + random.nextInt(seedMax - seedMin + 1);
        long wearSeed = random.nextLong();
        return new RolledReward(skin, statTrak, floatValue, pattern, wearSeed);
    }

    /**
     * Weighted rarity pick among the rarities this case actually contains; the configured weights
     * are renormalised over those tiers.
     */
    public static Rarity rollRarity(CaseDefinition def, Catalog catalog, RandomGenerator rng) {
        double total = 0;
        for (Rarity r : catalog.raritiesOrdered()) {
            if (!def.skins(r).isEmpty()) {
                total += Math.max(0, r.weight());
            }
        }
        double roll = rng.nextDouble() * total;
        Rarity last = null;
        for (Rarity r : catalog.raritiesOrdered()) {
            if (def.skins(r).isEmpty()) {
                continue;
            }
            last = r;
            roll -= Math.max(0, r.weight());
            if (roll < 0) {
                return r;
            }
        }
        return last;
    }

    /** Exact drop chance of a rarity in this case (for previews and the admin info command). */
    public static double chance(CaseDefinition def, Catalog catalog, Rarity rarity) {
        double total = 0;
        for (Rarity r : catalog.raritiesOrdered()) {
            if (!def.skins(r).isEmpty()) {
                total += Math.max(0, r.weight());
            }
        }
        return def.skins(rarity).isEmpty() || total <= 0 ? 0 : Math.max(0, rarity.weight()) / total;
    }

    public SecureRandom random() {
        return random;
    }
}
