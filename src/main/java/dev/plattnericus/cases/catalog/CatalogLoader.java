package dev.plattnericus.cases.catalog;

import dev.plattnericus.cases.pattern.AnalysisProfile;
import dev.plattnericus.cases.pattern.ClassificationRule;
import dev.plattnericus.cases.pattern.ColorClass;
import dev.plattnericus.cases.pattern.Condition;
import dev.plattnericus.cases.pattern.FadeSpec;
import dev.plattnericus.cases.pattern.ManualPattern;
import dev.plattnericus.cases.pattern.PatternConfig;
import dev.plattnericus.cases.render.TextureException;
import dev.plattnericus.cases.render.TextureStore;
import dev.plattnericus.cases.util.Colors;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds a {@link Catalog} from the {@code catalog/} folder. Invalid entries are skipped and
 * reported (file, entry, reason); the rest of the catalog still loads.
 */
public final class CatalogLoader {

    public record Result(Catalog catalog, List<String> problems, List<String> notes) {
    }

    private final File root;
    private final TextureStore textures;
    private final List<String> problems = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();

    public CatalogLoader(File catalogFolder, TextureStore textures) {
        this.root = catalogFolder;
        this.textures = textures;
    }

    public Result load(long version) {
        Map<String, Rarity> rarities = new LinkedHashMap<>();
        WearScale wear = loadRarities(rarities);
        Map<String, WeaponType> weapons = loadWeapons();
        Map<String, Style> styles = loadStyles();
        Map<String, Finish> finishes = new LinkedHashMap<>();
        Map<String, List<String>> finishSets = new LinkedHashMap<>();
        loadFinishes(styles, finishes, finishSets);
        Map<String, KeyDefinition> keys = loadKeys();
        PatternConfig patterns = loadPatterns();

        Map<String, SkinDefinition> skins = new LinkedHashMap<>();
        for (File file : yamlFiles("skins")) {
            YamlConfiguration yaml = read(file);
            if (yaml != null) {
                loadSkins(file.getName(), yaml.getConfigurationSection("skins"), weapons, styles, finishes, rarities, skins);
            }
        }
        Map<String, CaseDefinition> cases = new LinkedHashMap<>();
        Rarity gold = rarities.values().stream().filter(Rarity::rareSpecial).findFirst().orElse(null);
        for (File file : yamlFiles("cases")) {
            YamlConfiguration yaml = read(file);
            if (yaml == null) {
                continue;
            }
            String caseId = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
            Set<String> fileSkins = loadSkins(file.getName(), yaml.getConfigurationSection("skins"), weapons, styles, finishes, rarities, skins);
            CaseDefinition def = loadCase(file.getName(), caseId, yaml, fileSkins, skins, weapons, finishes, finishSets, keys, rarities, gold);
            if (def != null) {
                cases.put(caseId, def);
            }
        }
        for (CaseDefinition c : cases.values()) {
            if (keys.get(c.keyId()) == null) {
                problems.add("cases/" + c.id() + ".yml: key '" + c.keyId() + "' does not exist in keys.yml");
            }
        }
        problems.addAll(textures.drainWarnings().stream().map(w -> "textures: " + w).toList());
        Catalog catalog = new Catalog(version, rarities, wear, weapons, finishes, skins, cases, keys, patterns);
        return new Result(catalog, List.copyOf(problems), List.copyOf(notes));
    }

    // ------------------------------------------------------------------ files

    private List<File> yamlFiles(String folder) {
        File dir = new File(root, folder);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return List.of();
        }
        List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort(Comparator.comparing(File::getName));
        return list;
    }

    private YamlConfiguration read(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            return yaml;
        } catch (IOException e) {
            problems.add(rel(file) + ": cannot read (" + e.getMessage() + ")");
        } catch (InvalidConfigurationException e) {
            problems.add(rel(file) + ": invalid YAML (" + firstLine(e.getMessage()) + ")");
        }
        return null;
    }

    private YamlConfiguration read(String name) {
        File file = new File(root, name);
        if (!file.isFile()) {
            problems.add(name + ": file missing");
            return new YamlConfiguration();
        }
        YamlConfiguration yaml = read(file);
        return yaml == null ? new YamlConfiguration() : yaml;
    }

    private String rel(File file) {
        String parent = file.getParentFile().equals(root) ? "" : file.getParentFile().getName() + "/";
        return parent + file.getName();
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "unknown error";
        }
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    // ------------------------------------------------------------------ rarities & wear

    private WearScale loadRarities(Map<String, Rarity> out) {
        YamlConfiguration yaml = read("rarities.yml");
        ConfigurationSection sec = yaml.getConfigurationSection("rarities");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection r = sec.getConfigurationSection(id);
                if (r == null) {
                    continue;
                }
                try {
                    out.put(id, new Rarity(id, r.getString("name", id), Colors.parse(r.getString("color", "#ffffff")),
                            r.getDouble("weight", 0), r.getInt("order", out.size()),
                            r.getString("pane", "GRAY_STAINED_GLASS_PANE"), r.getBoolean("rare-special", false),
                            r.getString("reveal-sound", "reveal." + id)));
                } catch (IllegalArgumentException e) {
                    problems.add("rarities.yml: rarity '" + id + "': " + e.getMessage());
                }
            }
        }
        if (out.isEmpty()) {
            problems.add("rarities.yml: no rarities defined - using a single fallback tier");
            out.put("common", new Rarity("common", "Common", 0xB0C3D9, 1, 0, "GRAY_STAINED_GLASS_PANE", false, "reveal.common"));
        }
        if (out.values().stream().filter(Rarity::rareSpecial).count() > 1) {
            problems.add("rarities.yml: more than one rarity has rare-special: true; the lowest order one is used for knives");
        }
        List<WearTier> tiers = new ArrayList<>();
        ConfigurationSection w = yaml.getConfigurationSection("wear");
        if (w != null) {
            for (String id : w.getKeys(false)) {
                ConfigurationSection t = w.getConfigurationSection(id);
                if (t == null) {
                    continue;
                }
                double min = t.getDouble("min");
                double max = t.getDouble("max");
                if (max <= min) {
                    problems.add("rarities.yml: wear tier '" + id + "' has max <= min");
                    continue;
                }
                tiers.add(new WearTier(id, t.getString("name", id), t.getString("short", id.toUpperCase(Locale.ROOT)), min, max));
            }
        }
        tiers.sort(Comparator.comparingDouble(WearTier::min));
        if (tiers.isEmpty()) {
            problems.add("rarities.yml: no wear tiers - using CS2 defaults");
            tiers = List.of(
                    new WearTier("factory_new", "Factory New", "FN", 0.00, 0.07),
                    new WearTier("minimal_wear", "Minimal Wear", "MW", 0.07, 0.15),
                    new WearTier("field_tested", "Field-Tested", "FT", 0.15, 0.38),
                    new WearTier("well_worn", "Well-Worn", "WW", 0.38, 0.45),
                    new WearTier("battle_scarred", "Battle-Scarred", "BS", 0.45, 1.0000001));
        } else {
            for (int i = 1; i < tiers.size(); i++) {
                if (Math.abs(tiers.get(i).min() - tiers.get(i - 1).max()) > 1e-9) {
                    problems.add("rarities.yml: wear tiers '" + tiers.get(i - 1).id() + "' and '" + tiers.get(i).id() + "' leave a gap or overlap");
                }
            }
        }
        return new WearScale(tiers);
    }

    // ------------------------------------------------------------------ weapons

    private Map<String, WeaponType> loadWeapons() {
        Map<String, WeaponType> out = new LinkedHashMap<>();
        ConfigurationSection sec = read("weapons.yml").getConfigurationSection("weapons");
        if (sec == null) {
            problems.add("weapons.yml: section 'weapons' missing");
            return out;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection w = sec.getConfigurationSection(id);
            if (w == null) {
                continue;
            }
            WeaponCategory category;
            try {
                category = WeaponCategory.valueOf(w.getString("category", "rifle").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                problems.add("weapons.yml: '" + id + "' has unknown category '" + w.getString("category") + "'");
                continue;
            }
            Map<String, Region> regions = new LinkedHashMap<>();
            ConfigurationSection rs = w.getConfigurationSection("regions");
            if (rs != null) {
                for (String rid : rs.getKeys(false)) {
                    List<Double> v = rs.getDoubleList(rid);
                    if (v.size() != 4) {
                        problems.add("weapons.yml: '" + id + "' region '" + rid + "' needs [x1, y1, x2, y2]");
                        continue;
                    }
                    regions.put(rid, new Region(rid, v.get(0), v.get(1), v.get(2), v.get(3)));
                }
            }
            String folder = w.getString("textures", "weapons/" + id);
            try {
                textures.weapon(id, folder);
            } catch (TextureException e) {
                problems.add("weapon '" + id + "': " + e.getMessage() + " - weapon disabled");
                continue;
            }
            boolean statTrak = w.getBoolean("stattrak", true);
            out.put(id, new WeaponType(id, w.getString("name", id), category, w.getString("icon", "IRON_SWORD"),
                    folder, regions, statTrak, w.getString("inspect-model", category == WeaponCategory.KNIFE ? id : null)));
        }
        return out;
    }

    // ------------------------------------------------------------------ styles & finishes

    private Map<String, Style> loadStyles() {
        Map<String, Style> out = new LinkedHashMap<>();
        ConfigurationSection sec = read("styles.yml").getConfigurationSection("styles");
        if (sec == null) {
            problems.add("styles.yml: section 'styles' missing");
            return out;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            String texture = s.getString("texture", id + ".png");
            PaletteMapping mapping = mapping(s.getString("mapping", "smooth"), "styles.yml: '" + id + "'");
            String texPath = "patterns/" + texture;
            if (!textures.exists(texPath)) {
                problems.add("styles.yml: style '" + id + "' references missing texture " + texPath + " - skins using it show a fallback preview");
            } else {
                try {
                    if (mapping == PaletteMapping.NONE) {
                        textures.color(texPath);
                    } else {
                        textures.gray(texPath);
                    }
                } catch (TextureException e) {
                    problems.add("styles.yml: style '" + id + "': " + e.getMessage());
                }
            }
            out.put(id, new Style(id, texture, mapping, transform(s.getConfigurationSection("transform"), "styles.yml: '" + id + "'"),
                    s.getDouble("metallic", 0.2), s.getDouble("wear-strength", 1.0)));
        }
        return out;
    }

    private TransformRules transform(ConfigurationSection t, String where) {
        TransformRules d = TransformRules.defaults();
        if (t == null) {
            return d;
        }
        double[] ox = range(t, "offset-x", d.offsetMinX(), d.offsetMaxX(), where);
        double[] oy = range(t, "offset-y", d.offsetMinY(), d.offsetMaxY(), where);
        double[] rot = range(t, "rotation", d.rotationMin(), d.rotationMax(), where);
        double[] scale = range(t, "scale", d.scaleMin(), d.scaleMax(), where);
        if (scale[0] <= 0 || scale[1] <= 0) {
            problems.add(where + ": scale must be positive");
            scale = new double[]{1, 1};
        }
        return new TransformRules(t.getBoolean("translate", true), ox[0], ox[1], oy[0], oy[1], rot[0], rot[1],
                scale[0], scale[1], t.getBoolean("mirror", d.mirror()), t.getBoolean("clamp", false),
                t.getDouble("base-scale", 1.0));
    }

    private double[] range(ConfigurationSection s, String key, double defMin, double defMax, String where) {
        if (!s.contains(key)) {
            return new double[]{defMin, defMax};
        }
        List<Double> v = s.getDoubleList(key);
        if (v.size() != 2) {
            problems.add(where + ": '" + key + "' must be [min, max]");
            return new double[]{defMin, defMax};
        }
        return new double[]{Math.min(v.get(0), v.get(1)), Math.max(v.get(0), v.get(1))};
    }

    private PaletteMapping mapping(String text, String where) {
        try {
            return PaletteMapping.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            problems.add(where + ": unknown mapping '" + text + "' (smooth, steps, none)");
            return PaletteMapping.SMOOTH;
        }
    }

    private Palette palette(List<String> values, PaletteMapping mapping, String where) {
        List<Integer> parsed = new ArrayList<>();
        for (String v : values) {
            try {
                parsed.add(Colors.parse(v));
            } catch (IllegalArgumentException e) {
                problems.add(where + ": " + e.getMessage());
            }
        }
        if (parsed.isEmpty()) {
            parsed.add(0x808080);
        }
        return new Palette(parsed.stream().mapToInt(Integer::intValue).toArray(), mapping);
    }

    private void loadFinishes(Map<String, Style> styles, Map<String, Finish> out, Map<String, List<String>> sets) {
        YamlConfiguration yaml = read("finishes.yml");
        ConfigurationSection sec = yaml.getConfigurationSection("finishes");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection f = sec.getConfigurationSection(id);
                if (f != null) {
                    Finish finish = finish(id, f, styles, "finishes.yml: '" + id + "'", null);
                    if (finish != null) {
                        out.put(id, finish);
                    }
                }
            }
        }
        ConfigurationSection setSec = yaml.getConfigurationSection("finish-sets");
        if (setSec != null) {
            for (String setId : setSec.getKeys(false)) {
                List<String> ids = new ArrayList<>();
                for (String fid : setSec.getStringList(setId)) {
                    if (out.containsKey(fid)) {
                        ids.add(fid);
                    } else {
                        problems.add("finishes.yml: finish-set '" + setId + "' references unknown finish '" + fid + "'");
                    }
                }
                sets.put(setId, ids);
            }
        }
    }

    /**
     * Parses a finish block. {@code inheritFloat} supplies a default float range when the
     * section does not specify one.
     */
    private Finish finish(String id, ConfigurationSection f, Map<String, Style> styles, String where, double[] inheritFloat) {
        Style style = styles.get(f.getString("style", "solid"));
        if (style == null) {
            problems.add(where + ": unknown style '" + f.getString("style") + "'");
            return null;
        }
        PaletteMapping mapping = f.contains("mapping") ? mapping(f.getString("mapping"), where) : style.mapping();
        Palette palette = palette(f.getStringList("palette"), mapping, where);
        double[] fl = inheritFloat != null ? inheritFloat : new double[]{0, 1};
        if (f.contains("float")) {
            fl = range(f, "float", 0, 1, where);
        }
        if (fl[0] < 0 || fl[1] > 1) {
            problems.add(where + ": float range must stay within [0, 1]");
            fl = new double[]{Math.max(0, fl[0]), Math.min(1, fl[1])};
        }
        List<FinishVariant> variants = new ArrayList<>();
        ConfigurationSection vs = f.getConfigurationSection("variants");
        if (vs != null) {
            for (String vid : vs.getKeys(false)) {
                ConfigurationSection v = vs.getConfigurationSection(vid);
                if (v == null) {
                    continue;
                }
                Palette vp = v.contains("palette") ? palette(v.getStringList("palette"), mapping, where + " variant '" + vid + "'") : palette;
                int color;
                try {
                    color = v.contains("color") ? Colors.parse(v.getString("color")) : vp.primary();
                } catch (IllegalArgumentException e) {
                    problems.add(where + " variant '" + vid + "': " + e.getMessage());
                    color = vp.primary();
                }
                String vt = v.getString("texture");
                if (vt != null && !textures.exists("patterns/" + vt)) {
                    problems.add(where + " variant '" + vid + "': missing texture patterns/" + vt);
                }
                variants.add(new FinishVariant(vid, v.getString("name", vid), v.getDouble("weight", 1), vp, vt, color));
            }
        }
        String overlay = f.getString("overlay");
        if (overlay != null && !textures.exists("overlays/" + overlay)) {
            problems.add(where + ": missing overlay overlays/" + overlay + " - overlay ignored");
            overlay = null;
        }
        String analysis = f.getString("analysis");
        return new Finish(id, f.getString("name", ""), style, palette, variants, analysis,
                f.getBoolean("wearable", true), fl[0], fl[1], f.getBoolean("per-instance-wear", false),
                overlay, f.getBoolean("patterned", true));
    }

    // ------------------------------------------------------------------ keys

    private Map<String, KeyDefinition> loadKeys() {
        Map<String, KeyDefinition> out = new LinkedHashMap<>();
        ConfigurationSection sec = read("keys.yml").getConfigurationSection("keys");
        if (sec == null) {
            problems.add("keys.yml: section 'keys' missing");
            return out;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection k = sec.getConfigurationSection(id);
            if (k == null) {
                continue;
            }
            int color;
            try {
                color = Colors.parse(k.getString("color", "#e4ae39"));
            } catch (IllegalArgumentException e) {
                problems.add("keys.yml: '" + id + "': " + e.getMessage());
                color = 0xE4AE39;
            }
            out.put(id, new KeyDefinition(id, k.getString("name", id), color, k.getString("icon", "TRIAL_KEY"), k.getStringList("lore")));
        }
        return out;
    }

    // ------------------------------------------------------------------ skins & cases

    private Set<String> loadSkins(String file, ConfigurationSection sec, Map<String, WeaponType> weapons,
                                  Map<String, Style> styles, Map<String, Finish> finishes,
                                  Map<String, Rarity> rarities, Map<String, SkinDefinition> out) {
        Set<String> ids = new java.util.LinkedHashSet<>();
        if (sec == null) {
            return ids;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            String where = file + ": skin '" + id + "'";
            if (out.containsKey(id)) {
                problems.add(where + ": duplicate skin id, first definition kept");
                ids.add(id);
                continue;
            }
            WeaponType weapon = weapons.get(s.getString("weapon", ""));
            if (weapon == null) {
                problems.add(where + ": unknown or disabled weapon '" + s.getString("weapon") + "' - skin disabled");
                continue;
            }
            Rarity rarity = rarities.get(s.getString("rarity", ""));
            if (rarity == null) {
                problems.add(where + ": unknown rarity '" + s.getString("rarity") + "' - skin disabled");
                continue;
            }
            Finish finish;
            if (s.isString("finish")) {
                finish = finishes.get(s.getString("finish"));
                if (finish == null) {
                    problems.add(where + ": unknown finish '" + s.getString("finish") + "' - skin disabled");
                    continue;
                }
            } else {
                finish = finish(id, s, styles, where, null);
                if (finish == null) {
                    continue;
                }
            }
            double[] fl = s.contains("float") ? range(s, "float", finish.floatMin(), finish.floatMax(), where)
                    : new double[]{finish.floatMin(), finish.floatMax()};
            boolean statTrak = weapon.statTrak() && s.getBoolean("stattrak", true);
            out.put(id, new SkinDefinition(id, weapon, finish, rarity, fl[0], fl[1], statTrak));
            ids.add(id);
        }
        return ids;
    }

    private CaseDefinition loadCase(String file, String caseId, YamlConfiguration yaml, Set<String> fileSkins,
                                    Map<String, SkinDefinition> skins, Map<String, WeaponType> weapons,
                                    Map<String, Finish> finishes, Map<String, List<String>> finishSets,
                                    Map<String, KeyDefinition> keys, Map<String, Rarity> rarities, Rarity gold) {
        ConfigurationSection meta = yaml.getConfigurationSection("case");
        if (meta == null) {
            problems.add("cases/" + file + ": section 'case' missing - case skipped");
            return null;
        }
        List<String> contentIds = yaml.contains("contents") ? yaml.getStringList("contents") : new ArrayList<>(fileSkins);
        Map<String, List<SkinDefinition>> pool = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (String id : contentIds) {
            SkinDefinition def = skins.get(id);
            if (def == null) {
                problems.add("cases/" + file + ": content '" + id + "' is not a loaded skin");
                continue;
            }
            if (seen.add(id)) {
                pool.computeIfAbsent(def.rarity().id(), k -> new ArrayList<>()).add(def);
            }
        }
        ConfigurationSection rare = yaml.getConfigurationSection("rare-special");
        if (rare != null) {
            if (gold == null) {
                problems.add("cases/" + file + ": rare-special defined but no rarity has rare-special: true");
            } else {
                List<String> finishIds = new ArrayList<>();
                if (rare.isList("finishes")) {
                    finishIds.addAll(rare.getStringList("finishes"));
                } else if (rare.isString("finishes")) {
                    List<String> set = finishSets.get(rare.getString("finishes"));
                    if (set == null) {
                        problems.add("cases/" + file + ": unknown finish-set '" + rare.getString("finishes") + "'");
                    } else {
                        finishIds.addAll(set);
                    }
                }
                for (String knifeId : rare.getStringList("knives")) {
                    WeaponType knife = weapons.get(knifeId);
                    if (knife == null || !knife.isKnife()) {
                        problems.add("cases/" + file + ": '" + knifeId + "' is not a loaded knife");
                        continue;
                    }
                    for (String fid : finishIds) {
                        Finish finish = finishes.get(fid);
                        if (finish == null) {
                            problems.add("cases/" + file + ": unknown knife finish '" + fid + "'");
                            continue;
                        }
                        String skinId = knifeId + "_" + fid;
                        SkinDefinition def = skins.computeIfAbsent(skinId, k ->
                                new SkinDefinition(k, knife, finish, gold, finish.floatMin(), finish.floatMax(), knife.statTrak()));
                        if (seen.add(skinId)) {
                            pool.computeIfAbsent(gold.id(), k -> new ArrayList<>()).add(def);
                        }
                    }
                }
                for (String itemId : rare.getStringList("items")) {
                    SkinDefinition def = skins.get(itemId);
                    if (def == null) {
                        problems.add("cases/" + file + ": rare-special item '" + itemId + "' is not a loaded skin");
                        continue;
                    }
                    if (!def.rarity().rareSpecial()) {
                        notes.add("cases/" + file + ": '" + itemId + "' is listed as rare special but has rarity " + def.rarity().id());
                    }
                    if (seen.add(itemId)) {
                        pool.computeIfAbsent(def.rarity().id(), k -> new ArrayList<>()).add(def);
                    }
                }
            }
        }
        if (pool.isEmpty()) {
            problems.add("cases/" + file + ": case has no valid contents - skipped");
            return null;
        }
        int color;
        try {
            color = Colors.parse(meta.getString("color", "#e4ae39"));
        } catch (IllegalArgumentException e) {
            problems.add("cases/" + file + ": " + e.getMessage());
            color = 0xE4AE39;
        }
        double stChance = meta.getDouble("stattrak-chance", 0.10);
        if (stChance < 0 || stChance > 1) {
            problems.add("cases/" + file + ": stattrak-chance must be between 0 and 1");
            stChance = Math.max(0, Math.min(1, stChance));
        }
        Map<String, List<SkinDefinition>> frozen = new LinkedHashMap<>();
        rarities.values().stream().sorted(Comparator.comparingInt(Rarity::order)).forEach(r -> {
            List<SkinDefinition> l = pool.get(r.id());
            if (l != null) {
                frozen.put(r.id(), List.copyOf(l));
            }
        });
        return new CaseDefinition(caseId, meta.getString("name", caseId), meta.getString("key", "case_key"), color,
                meta.getString("icon", "minecraft:chest"), meta.getStringList("description"), frozen, stChance,
                meta.getBoolean("enabled", true));
    }

    // ------------------------------------------------------------------ patterns

    private PatternConfig loadPatterns() {
        YamlConfiguration yaml = read("patterns.yml");
        List<ColorClass> classes = new ArrayList<>();
        ConfigurationSection cc = yaml.getConfigurationSection("color-classes");
        if (cc != null) {
            for (String id : cc.getKeys(false)) {
                ConfigurationSection c = cc.getConfigurationSection(id);
                if (c == null) {
                    continue;
                }
                String where = "patterns.yml: color-class '" + id + "'";
                List<Double> hue = c.getDoubleList("hue");
                if (hue.size() != 2) {
                    problems.add(where + ": hue must be [min, max]");
                    continue;
                }
                double[] sat = range(c, "saturation", 0, 1, where);
                double[] val = range(c, "value", 0, 1, where);
                classes.add(new ColorClass(id, hue.get(0).floatValue(), hue.get(1).floatValue(),
                        (float) sat[0], (float) sat[1], (float) val[0], (float) val[1]));
            }
        }
        Map<String, AnalysisProfile> profiles = new LinkedHashMap<>();
        ConfigurationSection ps = yaml.getConfigurationSection("profiles");
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                ConfigurationSection p = ps.getConfigurationSection(id);
                if (p == null) {
                    continue;
                }
                String where = "patterns.yml: profile '" + id + "'";
                FadeSpec fade = null;
                ConfigurationSection fs = p.getConfigurationSection("fade");
                if (fs != null) {
                    double from = fs.getDouble("from", 0);
                    double to = fs.getDouble("to", 1);
                    if (to == from) {
                        problems.add(where + ": fade from and to must differ");
                    } else {
                        fade = new FadeSpec(fs.getString("metric", "full.fade"), from, to,
                                fs.getDouble("min", 80), fs.getDouble("max", 100), fs.getBoolean("invert", false));
                    }
                }
                List<ClassificationRule> rules = new ArrayList<>();
                for (Map<?, ?> raw : p.getMapList("rules")) {
                    try {
                        rules.add(rule(raw));
                    } catch (IllegalArgumentException e) {
                        problems.add(where + ": rule " + raw.get("name") + ": " + e.getMessage());
                    }
                }
                for (String cls : p.getStringList("classes")) {
                    if (classes.stream().noneMatch(c -> c.id().equals(cls))) {
                        problems.add(where + ": unknown color class '" + cls + "'");
                    }
                }
                profiles.put(id, new AnalysisProfile(id, p.getStringList("regions"), p.getStringList("classes"), fade, rules));
            }
        }
        Map<String, Map<Integer, ManualPattern>> manual = new LinkedHashMap<>();
        ConfigurationSection ms = yaml.getConfigurationSection("manual");
        if (ms != null) {
            for (String skinId : ms.getKeys(false)) {
                ConfigurationSection bySeed = ms.getConfigurationSection(skinId);
                if (bySeed == null) {
                    continue;
                }
                Map<Integer, ManualPattern> seeds = new HashMap<>();
                for (String seedKey : bySeed.getKeys(false)) {
                    ConfigurationSection e = bySeed.getConfigurationSection(seedKey);
                    try {
                        int seed = Integer.parseInt(seedKey.trim());
                        String name = e != null ? e.getString("name", "Special") : bySeed.getString(seedKey, "Special");
                        int tier = e != null ? e.getInt("tier", 1) : 1;
                        int color = e != null && e.contains("color") ? Colors.parse(e.getString("color")) : 0x4B69FF;
                        seeds.put(seed, new ManualPattern(name, tier, color));
                    } catch (IllegalArgumentException ex) {
                        problems.add("patterns.yml: manual '" + skinId + "." + seedKey + "': " + ex.getMessage());
                    }
                }
                manual.put(skinId, Map.copyOf(seeds));
            }
        }
        List<Integer> seedRange = yaml.getIntegerList("seed-range");
        int seedMin = seedRange.size() == 2 ? seedRange.get(0) : 0;
        int seedMax = seedRange.size() == 2 ? seedRange.get(1) : 999;
        if (seedMax < seedMin) {
            problems.add("patterns.yml: seed-range max < min - using 0..999");
            seedMin = 0;
            seedMax = 999;
        }
        return new PatternConfig(classes, profiles, manual, seedMin, seedMax);
    }

    private ClassificationRule rule(Map<?, ?> raw) {
        Object name = raw.get("name");
        if (name == null) {
            throw new IllegalArgumentException("rule without name");
        }
        int tier = raw.get("tier") instanceof Number n ? n.intValue() : 1;
        int color = raw.get("color") != null ? Colors.parse(String.valueOf(raw.get("color"))) : 0xFFFFFF;
        List<Condition> conditions = new ArrayList<>();
        Object when = raw.get("when");
        if (when instanceof List<?> list) {
            for (Object o : list) {
                conditions.add(Condition.parse(String.valueOf(o)));
            }
        } else if (when != null) {
            conditions.add(Condition.parse(String.valueOf(when)));
        }
        if (conditions.isEmpty()) {
            throw new IllegalArgumentException("rule needs at least one 'when' condition");
        }
        return new ClassificationRule(String.valueOf(name), tier, color, conditions);
    }
}
