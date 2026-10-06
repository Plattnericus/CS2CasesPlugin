package dev.plattnericus.cases.admin;

import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.pattern.PatternReport;
import dev.plattnericus.cases.render.RenderService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Evaluates every seed of a skin (cached per seed) and ranks them by one metric. */
public final class PatternScanner {

    public record Hit(int seed, double value, PatternReport report) {
    }

    private PatternScanner() {
    }

    public static CompletableFuture<List<Hit>> scan(RenderService render, SkinDefinition skin, int seedMin, int seedMax,
                                                     String metric, int limit) {
        List<CompletableFuture<PatternReport>> futures = new ArrayList<>();
        for (int seed = seedMin; seed <= seedMax; seed++) {
            futures.add(render.report(skin, seed));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(v -> {
            List<Hit> hits = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                PatternReport r = futures.get(i).join();
                Double value = r.metrics().get(metric);
                if (value != null) {
                    hits.add(new Hit(seedMin + i, value, r));
                }
            }
            hits.sort(Comparator.comparingDouble(Hit::value).reversed());
            return hits.size() > limit ? new ArrayList<>(hits.subList(0, limit)) : hits;
        });
    }

    /** Metric names available for a skin (taken from one analysed seed). */
    public static CompletableFuture<List<String>> metrics(RenderService render, SkinDefinition skin) {
        return render.report(skin, 0).thenApply(r -> new ArrayList<>(r.metrics().keySet()));
    }
}
