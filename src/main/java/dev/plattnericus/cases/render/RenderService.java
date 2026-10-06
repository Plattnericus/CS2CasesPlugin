package dev.plattnericus.cases.render;

import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.pattern.PatternClassifier;
import dev.plattnericus.cases.pattern.PatternConfig;
import dev.plattnericus.cases.pattern.PatternReport;
import dev.plattnericus.cases.util.LruCache;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Asynchronous rendering with bounded caches. Nothing here ever runs on the server thread.
 * Cache keys contain everything that affects the pixels; floats are bucketed so near-identical
 * floats share an entry.
 */
public final class RenderService implements AutoCloseable {

    /** Swappable state; replaced atomically on reload. */
    public record Engine(SkinRenderer renderer, PatternConfig patterns, MapCardStyle cardStyle,
                         Function<ArgbImage, byte[]> mapEncoder) {
    }

    private record PreviewKey(String skinId, int seed, long floatBucket, long wearSeed, int accent) {
    }

    private record ReportKey(String skinId, int seed) {
    }

    private record HologramKey(PreviewKey preview, int resolution) {
    }

    private final ExecutorService executor;
    private final LruCache<PreviewKey, byte[]> previews;
    private final LruCache<ReportKey, PatternReport> reports;
    private final LruCache<HologramKey, int[]> holograms;
    private final double floatBucket;
    private volatile Engine engine;

    public RenderService(int threads, int previewEntries, int reportEntries, double floatBucket, Engine engine) {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "MCCases-Render-" + counter.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        };
        this.executor = Executors.newFixedThreadPool(Math.max(1, threads), factory);
        this.previews = new LruCache<>(previewEntries);
        this.reports = new LruCache<>(reportEntries);
        this.holograms = new LruCache<>(Math.max(16, previewEntries / 4));
        this.floatBucket = floatBucket <= 0 ? 0.0025 : floatBucket;
        this.engine = engine;
    }

    public Engine engine() {
        return engine;
    }

    /** Swaps renderer/config after a reload and drops every cached result. */
    public void reconfigure(Engine newEngine) {
        this.engine = newEngine;
        previews.clear();
        reports.clear();
        holograms.clear();
    }

    public CompletableFuture<PatternReport> report(SkinDefinition skin, int seed) {
        ReportKey key = new ReportKey(skin.id(), seed);
        PatternReport cached = reports.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        Engine e = engine;
        return CompletableFuture.supplyAsync(() -> {
            try {
                PaintSample paint = e.renderer().paint(skin, seed);
                PatternReport report = PatternClassifier.classify(skin, seed, paint, e.patterns());
                reports.put(key, report);
                return report;
            } catch (TextureException ex) {
                throw new CompletionException(ex);
            }
        }, executor);
    }

    /** Map color ids (128x128) for the given instance parameters. */
    public CompletableFuture<byte[]> mapPreview(SkinDefinition skin, int seed, double floatValue, long wearSeed) {
        long bucket = Math.round(floatValue / floatBucket);
        long effectiveWear = skin.finish().perInstanceWear() ? wearSeed : 0;
        PreviewKey key = new PreviewKey(skin.id(), seed, bucket, effectiveWear, skin.rarity().color());
        byte[] cached = previews.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        Engine e = engine;
        double renderFloat = bucket * floatBucket;
        return CompletableFuture.supplyAsync(() -> {
            try {
                RenderedSkin rendered = e.renderer().render(skin, seed, renderFloat, effectiveWear);
                ArgbImage card = MapCardComposer.compose(rendered.image(), skin.rarity().color(), e.cardStyle());
                byte[] bytes = e.mapEncoder().apply(card);
                previews.put(key, bytes);
                return bytes;
            } catch (TextureException ex) {
                throw new CompletionException(ex);
            }
        }, executor);
    }

    /**
     * Full-color preview card at {@code resolution}x{@code resolution} (RGB, row-major) for text-display
     * holograms. Unlike maps there is no palette limit, so no dithering is applied.
     */
    public CompletableFuture<int[]> hologram(SkinDefinition skin, int seed, double floatValue, long wearSeed, int resolution) {
        long bucket = Math.round(floatValue / floatBucket);
        long effectiveWear = skin.finish().perInstanceWear() ? wearSeed : 0;
        HologramKey key = new HologramKey(new PreviewKey(skin.id(), seed, bucket, effectiveWear, skin.rarity().color()), resolution);
        int[] cached = holograms.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        Engine e = engine;
        double renderFloat = bucket * floatBucket;
        return CompletableFuture.supplyAsync(() -> {
            try {
                RenderedSkin rendered = e.renderer().render(skin, seed, renderFloat, effectiveWear);
                ArgbImage card = MapCardComposer.compose(rendered.image(), skin.rarity().color(), e.cardStyle())
                        .scaledTo(resolution, resolution);
                int[] rgb = new int[resolution * resolution];
                for (int i = 0; i < rgb.length; i++) {
                    rgb[i] = card.pixels()[i] & 0xFFFFFF;
                }
                holograms.put(key, rgb);
                return rgb;
            } catch (TextureException ex) {
                throw new CompletionException(ex);
            }
        }, executor);
    }

    /** Uncached full-resolution render (exports, debugging). */
    public CompletableFuture<RenderedSkin> render(SkinDefinition skin, int seed, double floatValue, long wearSeed) {
        Engine e = engine;
        return CompletableFuture.supplyAsync(() -> {
            try {
                return e.renderer().render(skin, seed, floatValue, wearSeed);
            } catch (TextureException ex) {
                throw new CompletionException(ex);
            }
        }, executor);
    }

    /** Runs arbitrary CPU work (pattern scans, pack export) on the render pool. */
    public <T> CompletableFuture<T> submit(java.util.function.Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executor);
    }

    public int cachedPreviews() {
        return previews.size();
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
