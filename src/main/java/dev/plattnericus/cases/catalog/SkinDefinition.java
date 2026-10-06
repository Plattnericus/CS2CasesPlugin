package dev.plattnericus.cases.catalog;

/**
 * What a skin is (weapon + finish + rarity + float bounds). Concrete owned copies are
 * {@link dev.plattnericus.cases.skin.SkinInstance}s.
 */
public record SkinDefinition(String id, WeaponType weapon, Finish finish, Rarity rarity,
                             double minFloat, double maxFloat, boolean statTrakEligible) {

    /** "AK-47 | Redline", "★ Karambit | Doppler", "★ Karambit" for vanilla. */
    public String displayName() {
        String prefix = weapon.category().isStarItem() ? "★ " : "";
        if (finish.isVanilla()) {
            return prefix + weapon.name();
        }
        return prefix + weapon.name() + " | " + finish.name();
    }

    public boolean isKnife() {
        return weapon.isKnife();
    }

    public boolean hasWear() {
        return finish.wearable();
    }
}
