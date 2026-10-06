package dev.plattnericus.cases.catalog;

import java.util.Map;

/**
 * A weapon model: display name, category, icon material for menus, the folder holding its
 * PNG layers and the analysis regions used by pattern classification.
 */
public record WeaponType(String id, String name, WeaponCategory category, String icon, String textureFolder,
                         Map<String, Region> regions, boolean statTrak, String inspectModel) {

    public WeaponType {
        regions = Map.copyOf(regions);
    }

    public boolean isKnife() {
        return category == WeaponCategory.KNIFE;
    }

    public Region region(String regionId) {
        if (Region.FULL.equals(regionId)) {
            return Region.full();
        }
        return regions.get(regionId);
    }
}
