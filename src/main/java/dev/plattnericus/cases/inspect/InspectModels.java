package dev.plattnericus.cases.inspect;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Everything from inspect.yml: anchor, block palette, knife models and animations. */
public final class InspectModels {

    public record Anchor(double forward, double right, double up) {
    }

    public record PaletteBlock(Material material, int rgb) {
    }

    private final Anchor anchor;
    private final Anchor revealAnchor;
    private final Anchor handAnchor;
    private final float handModelScale;
    private final int maxParts;
    private final float viewRange;
    private final int brightness;
    private final double followThreshold;
    private final boolean packModel;
    private final float packModelScale;
    private final float modelScale;
    private final Map<String, Material> aliases = new HashMap<>();
    private final List<PaletteBlock> palette = new ArrayList<>();
    private final Map<String, KnifeModel> models = new LinkedHashMap<>();
    private final Map<String, InspectAnimation> animations = new LinkedHashMap<>();
    private final Map<String, List<String>> animationPools = new LinkedHashMap<>();

    public InspectModels(File file, Consumer<String> warn) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        try (var bundled = InspectModels.class.getResourceAsStream("/defaults/inspect.yml")) {
            if (bundled != null) {
                y.setDefaults(YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(bundled, java.nio.charset.StandardCharsets.UTF_8)));
                y.options().copyDefaults(true);
            }
        } catch (java.io.IOException e) {
            warn.accept("inspect.yml: cannot read bundled defaults: " + e.getMessage());
        }
        // New profiles live separately so upgrades preserve existing inspect.yml customisation.
        YamlConfiguration profiles = YamlConfiguration.loadConfiguration(new File(file.getParentFile(), "inspect-profiles.yml"));
        try (var bundled = InspectModels.class.getResourceAsStream("/defaults/inspect-profiles.yml")) {
            if (bundled != null) {
                var defaults = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(bundled, java.nio.charset.StandardCharsets.UTF_8));
                InspectProfileDefaults.upgrade(profiles, defaults);
                profiles.setDefaults(defaults);
                profiles.options().copyDefaults(true);
            }
        } catch (java.io.IOException e) { warn.accept("inspect-profiles.yml: " + e.getMessage()); }
        if (profiles.getBoolean("enabled", true)) for (String section : List.of("animations", "animation-pools")) {
            ConfigurationSection entries = profiles.getConfigurationSection(section);
            if (entries != null) for (String key : entries.getKeys(false)) y.set(section + "." + key, entries.get(key));
        }
        Anchor configuredAnchor = anchor(y.getConfigurationSection("anchor"), new Anchor(1.25, 0.57, -0.07));
        // Only migrate the stock camera. Explicit server anchor edits keep their exact values.
        anchor = profiles.getBoolean("enabled", true) && configuredAnchor.equals(new Anchor(1.25, .57, -.07))
                ? anchor(profiles.getConfigurationSection("anchor"), new Anchor(1.75, .43, -.07)) : configuredAnchor;
        revealAnchor = anchor(y.getConfigurationSection("reveal-anchor"), new Anchor(1.35, 0, -0.05));
        handAnchor = anchor(y.getConfigurationSection("hand-anchor"), new Anchor(0.26, 0.34, -0.8));
        handModelScale = number(y, "hand-model-scale", 1.3f, .1f, 4, warn);
        maxParts = Math.max(1, Math.min(32, y.getInt("max-parts", 16)));
        viewRange = number(y, "view-range", .5f, .1f, 16, warn);
        brightness = Math.clamp(y.getInt("brightness", 15), -1, 15);
        followThreshold = number(y, "follow-threshold", .015f, 0, 1, warn);
        packModel = y.getBoolean("pack-model", true);
        packModelScale = number(y, "pack-model-scale", .75f, .1f, 4, warn);
        modelScale = number(y, "model-scale", 1.24f, .1f, 4, warn);

        ConfigurationSection al = y.getConfigurationSection("aliases");
        if (al != null) {
            for (String k : al.getKeys(false)) {
                Material m = Material.matchMaterial(al.getString(k, ""));
                if (m == null) {
                    warn.accept("inspect.yml: alias '" + k + "' has unknown material");
                } else {
                    aliases.put(k, m);
                }
            }
        }
        ConfigurationSection bp = y.getConfigurationSection("block-palette");
        if (bp != null) {
            for (String k : bp.getKeys(false)) {
                Material m = Material.matchMaterial(k);
                if (m == null || (org.bukkit.Bukkit.getServer() != null && !m.isBlock())) {
                    warn.accept("inspect.yml: block-palette entry '" + k + "' is not a block");
                    continue;
                }
                try {
                    palette.add(new PaletteBlock(m, dev.plattnericus.cases.util.Colors.parse(bp.getString(k))));
                } catch (IllegalArgumentException e) {
                    warn.accept("inspect.yml: block-palette '" + k + "': " + e.getMessage());
                }
            }
        }
        if (palette.isEmpty()) {
            palette.add(new PaletteBlock(Material.IRON_BLOCK, 0xDCDCDC));
        }

        ConfigurationSection an = y.getConfigurationSection("animations");
        if (an != null) {
            for (String id : an.getKeys(false)) {
                InspectAnimation a = animation(id, an.getConfigurationSection(id), warn);
                if (a != null) {
                    animations.put(id, a);
                }
            }
        }
        ConfigurationSection ms = y.getConfigurationSection("models");
        Map<String, String> copies = new LinkedHashMap<>();
        if (ms != null) {
            for (String id : ms.getKeys(false)) {
                ConfigurationSection m = ms.getConfigurationSection(id);
                if (m == null) {
                    continue;
                }
                if (m.isString("copy")) {
                    copies.put(id, m.getString("copy"));
                    continue;
                }
                KnifeModel model = model(id, m, warn);
                if (model != null) {
                    models.put(id, model);
                }
            }
            for (Map.Entry<String, String> c : copies.entrySet()) {
                KnifeModel base = models.get(c.getValue());
                if (base == null) {
                    warn.accept("inspect.yml: model '" + c.getKey() + "' copies unknown model '" + c.getValue() + "'");
                    continue;
                }
                String anim = ms.getString(c.getKey() + ".animation", base.animation());
                models.put(c.getKey(), new KnifeModel(c.getKey(), anim, base.parts()));
            }
        }
        for (KnifeModel m : models.values()) {
            if (!animations.containsKey(m.animation())) {
                warn.accept("inspect.yml: model '" + m.id() + "' uses unknown animation '" + m.animation() + "'");
            }
        }
        ConfigurationSection pools = y.getConfigurationSection("animation-pools");
        if (pools != null) for (String key : pools.getKeys(false)) {
            List<String> valid = new ArrayList<>();
            for (String id : pools.getStringList(key)) {
                if (animations.containsKey(id)) valid.add(id);
                else warn.accept("inspect.yml: animation-pools." + key + " uses unknown animation '" + id + "'");
            }
            if (!valid.isEmpty()) animationPools.put(key, List.copyOf(valid));
        }
    }

    private static Anchor anchor(ConfigurationSection s, Anchor def) {
        if (s == null) {
            return def;
        }
        double forward = s.getDouble("forward", def.forward()), right = s.getDouble("right", def.right()), up = s.getDouble("up", def.up());
        if (!Double.isFinite(forward) || !Double.isFinite(right) || !Double.isFinite(up) || Math.abs(forward)>8 || Math.abs(right)>8 || Math.abs(up)>8) return def;
        return new Anchor(forward, right, up);
    }

    private static float number(YamlConfiguration y, String key, float fallback, float min, float max, Consumer<String> warn) {
        double value = y.getDouble(key, fallback);
        if (!Double.isFinite(value) || value < min || value > max) { warn.accept("inspect.yml: invalid " + key + "; using " + fallback); return fallback; }
        return (float)value;
    }

    private KnifeModel model(String id, ConfigurationSection m, Consumer<String> warn) {
        ConfigurationSection ps = m.getConfigurationSection("parts");
        if (ps == null) {
            warn.accept("inspect.yml: model '" + id + "' has no parts");
            return null;
        }
        List<ModelPart> parts = new ArrayList<>();
        for (String pid : ps.getKeys(false)) {
            ConfigurationSection p = ps.getConfigurationSection(pid);
            if (p == null) {
                continue;
            }
            if (parts.size() >= maxParts) {
                warn.accept("inspect.yml: model '" + id + "' has more than max-parts (" + maxParts + ") parts; extra parts ignored");
                break;
            }
            ModelPart.Type type;
            try {
                type = ModelPart.Type.valueOf(p.getString("type", "block").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                warn.accept("inspect.yml: model '" + id + "' part '" + pid + "' has unknown type");
                continue;
            }
            Vector3f position = vec(p, "position", 0), size = vec(p, "size", .1f), rotation = vec(p, "rotation", 0);
            if (!position.isFinite() || !size.isFinite() || !rotation.isFinite() || size.x<=0 || size.y<=0 || size.z<=0
                    || position.length()>8 || Math.max(size.x,Math.max(size.y,size.z))>4) {
                warn.accept("inspect.yml: invalid geometry in " + id + "." + pid + "; skipping part"); continue;
            }
            parts.add(new ModelPart(pid, type, p.getString("material", "$primary"), position, size, rotation, p.getString("group", "body"), p.getBoolean("glow", false)));
        }
        if (parts.isEmpty()) { warn.accept("inspect.yml: model " + id + " has no valid parts"); return null; }
        return new KnifeModel(id, m.getString("animation", "default"), parts);
    }

    private InspectAnimation animation(String id, ConfigurationSection a, Consumer<String> warn) {
        if (a == null) {
            return null;
        }
        Map<Integer, String> sounds = new HashMap<>();
        ConfigurationSection ss = a.getConfigurationSection("sounds");
        if (ss != null) {
            for (String k : ss.getKeys(false)) {
                try {
                    sounds.put(Integer.parseInt(k), ss.getString(k));
                } catch (NumberFormatException e) {
                    warn.accept("inspect.yml: animation '" + id + "' sound key '" + k + "' is not a tick");
                }
            }
        }
        Map<String, InspectAnimation.Group> groups = new HashMap<>();
        ConfigurationSection gs = a.getConfigurationSection("groups");
        if (gs == null) {
            warn.accept("inspect.yml: animation '" + id + "' has no groups");
            return null;
        }
        for (String gid : gs.getKeys(false)) {
            ConfigurationSection g = gs.getConfigurationSection(gid);
            if (g == null) {
                continue;
            }
            List<InspectAnimation.Keyframe> frames = new ArrayList<>();
            for (Map<?, ?> raw : g.getMapList("keyframes")) {
                YamlConfiguration k = new YamlConfiguration();
                raw.forEach((key, value) -> k.set(String.valueOf(key), value));
                InspectAnimation.Ease ease;
                try {
                    ease = InspectAnimation.Ease.valueOf(k.getString("ease", "inout").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    ease = InspectAnimation.Ease.INOUT;
                }
                Vector3f scale = null;
                if (k.isList("scale")) {
                    scale = vec(k, "scale", 1);
                } else if (k.contains("scale")) {
                    float s = (float) k.getDouble("scale", 1);
                    scale = new Vector3f(s, s, s);
                }
                frames.add(new InspectAnimation.Keyframe(k.getInt("ticks", 0), k.contains("move") ? vec(k, "move", 0) : null,
                        k.contains("rotate") ? vec(k, "rotate", 0) : null, scale, ease));
            }
            groups.put(gid, new InspectAnimation.Group(gid, g.getString("parent"), vec(g, "pivot", 0), frames));
        }
        try { return new InspectAnimation(id, groups, sounds, a.getInt("substeps", 3)); }
        catch (IllegalArgumentException invalid) { warn.accept("inspect animation '" + id + "': " + invalid.getMessage()); return null; }
    }

    private static Vector3f vec(ConfigurationSection s, String key, float def) {
        List<Double> v = s.getDoubleList(key);
        if (v.size() != 3) {
            return new Vector3f(def, def, def);
        }
        return new Vector3f(v.get(0).floatValue(), v.get(1).floatValue(), v.get(2).floatValue());
    }

    public KnifeModel model(String id) {
        KnifeModel m = models.get(id);
        return m != null ? m : models.get("default");
    }

    public boolean hasModel(String id) { return models.containsKey(id); }
    public List<String> pool(String weapon) { return animationPools.getOrDefault(weapon, List.of()); }

    public InspectAnimation animation(String id) {
        InspectAnimation a = animations.get(id);
        return a != null ? a : animations.get("default");
    }

    public InspectAnimation chooseAnimation(dev.plattnericus.cases.catalog.SkinDefinition skin, KnifeModel model, String previous) {
        List<String> pool = animationPools.get(skin.weapon().id());
        if (pool == null) pool = animationPools.get(model.id());
        if (pool == null) pool = animationPools.get(model.animation());
        if (pool == null) pool = animationPools.get(skin.weapon().category().name().toLowerCase(Locale.ROOT));
        if (pool == null) return animation(model.animation());
        return animations.get(AnimationSelector.choose(pool, previous, java.util.concurrent.ThreadLocalRandom.current()));
    }

    public Material alias(String name) {
        return aliases.get(name);
    }

    /** Closest palette block for a color (weighted RGB distance). */
    public Material nearestBlock(int rgb) {
        PaletteBlock best = palette.getFirst();
        double bestD = Double.MAX_VALUE;
        for (PaletteBlock b : palette) {
            double rm = (((rgb >> 16) & 0xFF) + ((b.rgb() >> 16) & 0xFF)) / 2.0;
            double dr = ((rgb >> 16) & 0xFF) - ((b.rgb() >> 16) & 0xFF);
            double dg = ((rgb >> 8) & 0xFF) - ((b.rgb() >> 8) & 0xFF);
            double db = (rgb & 0xFF) - (b.rgb() & 0xFF);
            double d = (2 + rm / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rm) / 256) * db * db;
            if (d < bestD) {
                bestD = d;
                best = b;
            }
        }
        return best.material();
    }

    public Anchor anchor() {
        return anchor;
    }

    public Anchor revealAnchor() {
        return revealAnchor;
    }

    public float viewRange() {
        return viewRange;
    }

    public int brightness() {
        return brightness;
    }

    public double followThreshold() {
        return followThreshold;
    }

    public boolean packModel() {
        return packModel;
    }

    public float packModelScale() {
        return packModelScale;
    }

    /** Uniform scale of inspect models (the reveal showcase always uses 1). */
    public float modelScale() {
        return modelScale;
    }

    public Anchor handAnchor() { return handAnchor; }

    public float handModelScale() { return handModelScale; }

    public int modelCount() {
        return models.size();
    }
}
