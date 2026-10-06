package dev.plattnericus.cases.catalog;

import java.util.List;

/** Ordered list of exterior tiers covering [0, 1]. */
public record WearScale(List<WearTier> tiers) {

    public WearScale {
        tiers = List.copyOf(tiers);
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("at least one wear tier is required");
        }
    }

    public WearTier of(double floatValue) {
        for (WearTier tier : tiers) {
            if (tier.contains(floatValue)) {
                return tier;
            }
        }
        return floatValue < tiers.getFirst().min() ? tiers.getFirst() : tiers.getLast();
    }

    public WearTier byId(String id) {
        for (WearTier tier : tiers) {
            if (tier.id().equalsIgnoreCase(id) || tier.shortName().equalsIgnoreCase(id)) {
                return tier;
            }
        }
        return null;
    }

    /** True if a skin limited to [min, max] can roll this tier at all. */
    public static boolean reachable(WearTier tier, double min, double max) {
        return tier.max() > min && tier.min() <= max;
    }
}
