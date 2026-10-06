package dev.plattnericus.cases.pattern;

/**
 * Converts a measured share into a fade percentage: the metric is clamped to [from, to] and
 * mapped linearly to [min, max]; {@code invert} measures the share of the non-fade color.
 */
public record FadeSpec(String metric, double from, double to, double min, double max, boolean invert) {

    public double percentage(double share) {
        double s = invert ? 1 - share : share;
        double t = (s - from) / (to - from);
        t = Math.max(0, Math.min(1, t));
        return min + (max - min) * t;
    }
}
