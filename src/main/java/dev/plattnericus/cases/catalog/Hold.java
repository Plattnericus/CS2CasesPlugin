package dev.plattnericus.cases.catalog;

/**
 * Where the hands hold a weapon, in normalised canvas coordinates (x right, y down) of its
 * presented artwork. The support hand is absent for one-handed weapons and knives.
 */
public record Hold(double gripX, double gripY, double supportX, double supportY, boolean twoHanded) {

    public static Hold oneHanded(double gripX, double gripY) {
        return new Hold(gripX, gripY, gripX, gripY, false);
    }

    public static Hold twoHanded(double gripX, double gripY, double supportX, double supportY) {
        return new Hold(gripX, gripY, supportX, supportY, true);
    }
}
