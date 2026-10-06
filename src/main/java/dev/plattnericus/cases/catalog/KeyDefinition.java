package dev.plattnericus.cases.catalog;

import java.util.List;

public record KeyDefinition(String id, String name, int color, String icon, List<String> lore) {

    public KeyDefinition {
        lore = List.copyOf(lore);
    }
}
