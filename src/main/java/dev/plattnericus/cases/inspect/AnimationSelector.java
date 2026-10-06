package dev.plattnericus.cases.inspect;

import java.util.List;
import java.util.random.RandomGenerator;

/** Random variants without an immediate repeat, including safe single-variant custom pools. */
public final class AnimationSelector {
    private AnimationSelector() { }

    public static String choose(List<String> pool, String previous, RandomGenerator random) {
        if (pool.isEmpty()) throw new IllegalArgumentException("empty animation pool");
        List<String> choices = pool.stream().distinct().filter(id -> !id.equals(previous)).toList();
        if (choices.isEmpty()) choices = pool;
        return choices.get(random.nextInt(choices.size()));
    }
}
