package dev.plattnericus.cases.pattern;

import dev.plattnericus.cases.catalog.FinishVariant;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.render.PaintSample;

import java.util.LinkedHashMap;
import java.util.Map;

/** Combines manual database, automatic metrics, fade percentage and classification rules. */
public final class PatternClassifier {

    public static final String FADE_METRIC = "fade";

    private PatternClassifier() {
    }

    public static PatternReport classify(SkinDefinition skin, int seed, PaintSample paint, PatternConfig config) {
        FinishVariant variant = PatternEngine.variant(skin, seed);
        String variantId = variant == null ? null : variant.id();
        String variantName = variant == null ? null : variant.name();
        AnalysisProfile profile = skin.finish().analysis() == null ? null : config.profiles().get(skin.finish().analysis());
        Map<String, Double> metrics = new LinkedHashMap<>();
        Double fade = null;
        if (profile != null && paint != null) {
            metrics.putAll(PatternMetrics.measure(paint, skin.weapon(), profile, config));
            if (profile.fade() != null) {
                Double share = null;
                for (String candidate : profile.fade().metric().split("\\|")) {
                    share = metrics.get(candidate.trim());
                    if (share != null) {
                        break;
                    }
                }
                if (share != null) {
                    fade = Math.round(profile.fade().percentage(share) * 10.0) / 10.0;
                    metrics.put(FADE_METRIC, fade);
                }
            }
        }
        ManualPattern manual = config.manual(skin.id(), seed);
        if (manual != null) {
            return new PatternReport(variantId, variantName, manual.name(), manual.tier(), manual.color(), fade, true, metrics);
        }
        if (profile != null) {
            for (ClassificationRule rule : profile.rules()) {
                if (rule.matches(metrics)) {
                    return new PatternReport(variantId, variantName, rule.name(), rule.tier(), rule.color(), fade, false, metrics);
                }
            }
        }
        return new PatternReport(variantId, variantName, null, 0, 0, fade, false, metrics);
    }
}
