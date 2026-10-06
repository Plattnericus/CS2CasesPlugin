package dev.plattnericus.cases.pattern;

import java.util.List;

/** Which regions and color classes to measure for a finish, and how to classify the result. */
public record AnalysisProfile(String id, List<String> regions, List<String> classes, FadeSpec fade,
                              List<ClassificationRule> rules) {

    public AnalysisProfile {
        regions = List.copyOf(regions);
        classes = List.copyOf(classes);
        rules = List.copyOf(rules);
    }
}
