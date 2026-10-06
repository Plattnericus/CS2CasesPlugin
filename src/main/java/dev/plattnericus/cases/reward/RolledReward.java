package dev.plattnericus.cases.reward;

import dev.plattnericus.cases.catalog.SkinDefinition;

/** Result of the server-side roll, before pattern analysis. */
public record RolledReward(SkinDefinition skin, boolean statTrak, double floatValue, int pattern, long wearSeed) {
}
