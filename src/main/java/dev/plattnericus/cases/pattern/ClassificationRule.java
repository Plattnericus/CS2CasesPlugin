package dev.plattnericus.cases.pattern;

import java.util.List;
import java.util.Map;

/** Named pattern category that applies when all conditions hold. Lower tier = rarer. */
public record ClassificationRule(String name, int tier, int color, List<Condition> conditions) {

    public ClassificationRule {
        conditions = List.copyOf(conditions);
    }

    public boolean matches(Map<String, Double> metrics) {
        for (Condition c : conditions) {
            if (!c.test(metrics)) {
                return false;
            }
        }
        return true;
    }
}
