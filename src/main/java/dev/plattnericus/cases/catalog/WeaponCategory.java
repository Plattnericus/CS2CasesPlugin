package dev.plattnericus.cases.catalog;

public enum WeaponCategory {
    PISTOL, SMG, RIFLE, SNIPER, HEAVY, EQUIPMENT, KNIFE, GLOVE;

    /** Knives and gloves are "★" items and live in the rare special tier. */
    public boolean isStarItem() {
        return this == KNIFE || this == GLOVE;
    }
}
