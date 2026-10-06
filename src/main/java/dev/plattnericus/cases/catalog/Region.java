package dev.plattnericus.cases.catalog;

/** Analysis area in normalised canvas coordinates (0..1). */
public record Region(String id, double x1, double y1, double x2, double y2) {

    public static final String FULL = "full";

    public static Region full() {
        return new Region(FULL, 0, 0, 1, 1);
    }

    public boolean contains(double nx, double ny) {
        return nx >= x1 && nx < x2 && ny >= y1 && ny < y2;
    }
}
