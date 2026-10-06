package dev.plattnericus.cases.catalog;

/**
 * Exterior condition derived purely from the float: {@code min <= float < max}.
 */
public record WearTier(String id, String name, String shortName, double min, double max) {

    public boolean contains(double value) {
        return value >= min && value < max;
    }
}
