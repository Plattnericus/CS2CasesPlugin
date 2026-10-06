package dev.plattnericus.cases.pattern;

/**
 * Named HSV range used to measure color shares ("blue", "gold"...). A hue range whose
 * minimum is larger than its maximum wraps around 0 (e.g. red: 340..15).
 */
public record ColorClass(String id, float hueMin, float hueMax, float satMin, float satMax,
                         float valMin, float valMax) {

    public boolean matches(float hue, float sat, float val) {
        if (sat < satMin || sat > satMax || val < valMin || val > valMax) {
            return false;
        }
        if (hueMin <= hueMax) {
            return hue >= hueMin && hue <= hueMax;
        }
        return hue >= hueMin || hue <= hueMax;
    }
}
