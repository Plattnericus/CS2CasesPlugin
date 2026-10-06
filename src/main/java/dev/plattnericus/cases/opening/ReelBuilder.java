package dev.plattnericus.cases.opening;

import dev.plattnericus.cases.catalog.CaseDefinition;
import dev.plattnericus.cases.catalog.Catalog;
import dev.plattnericus.cases.catalog.Rarity;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.reward.RewardRoller;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Builds the visual strip. Filler items are independent draws with the case's real odds, so any
 * near miss on the strip is genuine chance, never staged. The strip cannot influence the reward,
 * which was decided before this is called.
 */
public final class ReelBuilder {

    /** Items shown after the winner so the strip does not end at the marker. */
    public static final int TAIL = 5;

    private ReelBuilder() {
    }

    public static List<SkinDefinition> build(CaseDefinition def, Catalog catalog, SkinDefinition winner, int length, long seed) {
        SplittableRandom rng = new SplittableRandom(seed);
        List<SkinDefinition> reel = new ArrayList<>(length);
        int winnerIndex = length - TAIL;
        for (int i = 0; i < length; i++) {
            if (i == winnerIndex) {
                reel.add(winner);
                continue;
            }
            Rarity r = RewardRoller.rollRarity(def, catalog, rng);
            List<SkinDefinition> pool = def.skins(r);
            reel.add(pool.get(rng.nextInt(pool.size())));
        }
        return reel;
    }
}
