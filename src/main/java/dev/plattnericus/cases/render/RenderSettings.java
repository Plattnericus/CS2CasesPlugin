package dev.plattnericus.cases.render;

/**
 * Global tuning of the wear model.
 *
 * @param wearExponent   float → wear curve exponent (below 1 makes low floats show wear earlier)
 * @param wearMax        paint-loss strength at float 1.0
 * @param edgeWeight     influence of the weapon's edge wear map
 * @param scratchWeight  influence of the tiled scratch texture
 * @param grime          darkening of remaining paint at high wear
 */
public record RenderSettings(double wearExponent, double wearMax, double edgeWeight, double scratchWeight, double grime) {

    public static RenderSettings defaults() {
        return new RenderSettings(0.9, 0.72, 0.62, 0.58, 0.22);
    }

    public double wearAmount(double floatValue) {
        double f = Math.max(0, Math.min(1, floatValue));
        return wearMax * Math.pow(f, wearExponent);
    }
}
