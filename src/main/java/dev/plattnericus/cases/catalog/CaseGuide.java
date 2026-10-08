package dev.plattnericus.cases.catalog;

import dev.plattnericus.cases.reward.RewardRoller;
import dev.plattnericus.cases.shop.ShopService;
import dev.plattnericus.cases.items.CaseItems;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/** Transparent server reward points, independent of real-money skin prices. */
public final class CaseGuide {
    public record Metrics(Long cost, double expectedPoints, double value, double knifeChance, double favoriteChance,
                          double communityScore, Map<String, Double> knives) { }
    private final Map<String, Double> rarityPoints = new HashMap<>(), finishMultipliers = new HashMap<>(), scores = new HashMap<>();
    private final double favoriteThreshold;
    private final String snapshot;
    public CaseGuide(File file, Consumer<String> warn) {
        var y = YamlConfiguration.loadConfiguration(file);
        try (var in = CaseGuide.class.getResourceAsStream("/defaults/case-guide.yml")) {
            if (in != null) { y.setDefaults(YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, StandardCharsets.UTF_8))); y.options().copyDefaults(true); }
        } catch (java.io.IOException e) { warn.accept("case-guide.yml: " + e.getMessage()); }
        read(y, "rarity-points", rarityPoints, 1000000, warn);
        read(y, "finish-multipliers", finishMultipliers, 100, warn);
        read(y, "knife-scores", scores, 100, warn);
        double threshold = y.getDouble("favorite-threshold", 85);
        favoriteThreshold = Double.isFinite(threshold) ? Math.clamp(threshold, 0, 100) : 85;
        snapshot = y.getString("snapshot", "2026-10-08");
    }
    private static void read(YamlConfiguration y, String key, Map<String, Double> out, double max, Consumer<String> warn) {
        var s = y.getConfigurationSection(key); if (s == null) return;
        for (String id : s.getKeys(false)) {
            double n = s.getDouble(id);
            if (!Double.isFinite(n) || n < 0 || n > max) warn.accept("case-guide.yml: invalid " + key + "." + id + "; ignoring");
            else out.put(id, n);
        }
    }
    public String snapshot() { return snapshot; }
    public boolean favorite(String knife) { return scores.getOrDefault(knife, 0d) >= favoriteThreshold; }
    public double points(SkinDefinition skin) {
        double base = rarityPoints.getOrDefault(skin.rarity().id(), 0d);
        double knife = skin.isKnife() ? .5 + scores.getOrDefault(skin.weapon().id(), 50d) / 100 : 1;
        return base * knife * finishMultipliers.getOrDefault(skin.finish().id(), 1d);
    }
    public Metrics measure(CaseDefinition def, Catalog catalog, List<ShopService.Offer> offers) {
        Long casePrice = null, keyPrice = null;
        for (var o : offers) {
            if (o.type().equals(CaseItems.TYPE_CASE) && o.id().equals(def.id())) casePrice = (long) o.price();
            if (o.type().equals(CaseItems.TYPE_KEY) && o.id().equals(def.keyId())) keyPrice = (long) o.price();
        }
        Long cost = casePrice == null || keyPrice == null ? null : casePrice + keyPrice;
        double expected = 0, knifeChance = 0, favorites = 0, community = 0;
        Map<String, Double> knives = new TreeMap<>();
        for (Rarity rarity : catalog.raritiesOrdered()) {
            List<SkinDefinition> pool = def.skins(rarity); if (pool.isEmpty()) continue;
            double each = RewardRoller.chance(def, catalog, rarity) / pool.size();
            for (SkinDefinition skin : pool) {
                expected += each * points(skin);
                if (skin.isKnife()) {
                    knifeChance += each; community += each * scores.getOrDefault(skin.weapon().id(), 50d);
                    knives.merge(skin.weapon().id(), each, Double::sum);
                    if (favorite(skin.weapon().id())) favorites += each;
                }
            }
        }
        return new Metrics(cost, expected, cost == null || cost <= 0 ? -1 : expected / cost, knifeChance, favorites,
                knifeChance == 0 ? 0 : community / knifeChance, Map.copyOf(knives));
    }
}
