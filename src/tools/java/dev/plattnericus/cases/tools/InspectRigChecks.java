package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.inspect.*;
import dev.plattnericus.cases.catalog.*;
import dev.plattnericus.cases.render.*;
import dev.plattnericus.cases.shop.ShopService;
import org.joml.Vector3f;
import java.io.File;
import java.util.*;

/** Coverage, joint continuity, hostile configuration and actual camera-space geometry checks. */
public final class InspectRigChecks {
    private InspectRigChecks() { }
    public static void run(File root) {
        Catalog catalog = new CatalogLoader(new File(root, "catalog"), new TextureStore(new File(root, "textures"))).load(1).catalog();
        var warnings = new ArrayList<String>();
        InspectModels models = new InspectModels(new File(root, "inspect.yml"), warnings::add);
        profileUpgrade(root);
        require(warnings.isEmpty(), "inspect warnings " + warnings);
        var soundConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new File(root, "sounds.yml"));
        int frames = 0; double requiredForward = 0; String furthest = ""; Set<String> all = new HashSet<>(), motionSignatures = new HashSet<>();
        var held = new InspectAnimation("stationary", Map.of("body", new InspectAnimation.Group("body", null, new Vector3f(), List.of(
                new InspectAnimation.Keyframe(0,null,new Vector3f(),null,InspectAnimation.Ease.LINEAR),
                new InspectAnimation.Keyframe(80,null,null,null,InspectAnimation.Ease.INOUT)))), Map.of(),4);
        require(held.samples().equals(List.of(0,80)), "stationary hold sends unnecessary tween samples");
        require(held.samples() == held.samples(), "per-tick sample list allocation");
        try { held.samples().add(1); throw new AssertionError("mutable animation samples"); }
        catch (UnsupportedOperationException expected) { /* immutable scene schedule */ }
        for (WeaponType weapon : catalog.weapons()) {
            String artworkSkin = catalog.skins().stream().filter(s -> s.weapon().id().equals(weapon.id())).map(SkinDefinition::id).sorted().findFirst().orElseThrow();
            require(catalog.skins().stream().anyMatch(s -> s.weapon().id().equals(weapon.id())), "unobtainable weapon " + weapon.id());
            var pool = models.pool(weapon.id()); require(pool.size() == 4, "four individual variants required: " + weapon.id());
            var base = models.model(weapon.isKnife() ? weapon.id() : weapon.category().name().toLowerCase(Locale.ROOT));
            if (weapon.category() == WeaponCategory.GLOVE) {
                var fallback = InspectRig.blockParts(weapon, base.parts());
                var leftPalm = fallback.stream().filter(p -> p.id().equals("palm_pair_a")).findFirst().orElseThrow();
                var rightPalm = fallback.stream().filter(p -> p.id().equals("palm_pair_b")).findFirst().orElseThrow();
                require(rightPalm.position().x - leftPalm.position().x >= .3f, "fallback gloves overlap " + weapon.id());
                var leftThumb = fallback.stream().filter(p -> p.id().equals("thumb_pair_a")).findFirst().orElseThrow();
                var rightThumb = fallback.stream().filter(p -> p.id().equals("thumb_pair_b")).findFirst().orElseThrow();
                require(leftThumb.position().x > leftPalm.position().x && rightThumb.position().x < rightPalm.position().x,
                        "fallback glove thumbs must face inward " + weapon.id());
            }
            for (String id : pool) {
                require(all.add(id) && id.startsWith(weapon.id() + "__"), "shared weapon timeline " + id);
                InspectAnimation animation = models.animation(id);
                if (InspectRig.reverseGrip(weapon)) {
                    // A real ring spin must leave the ring center anchored to the parent body.
                    Vector3f ring = weapon.id().equals("talon") ? new Vector3f(.27773438f, .017578125f, 0)
                            : new Vector3f(.24609375f, .01171875f, 0);
                    for (int tick = 0; tick <= animation.duration(); tick++) require(
                            animation.groupMatrix("roll", tick).transformPosition(new Vector3f(ring)).distance(
                                    animation.groupMatrix("body", tick).transformPosition(new Vector3f(ring))) < 1e-5,
                            "ring orbits instead of spinning on the finger " + id + "/" + tick);
                    require(animation.groupMatrix("roll", 0).equals(animation.groupMatrix("roll", animation.duration()), 1e-5f),
                            "ring knife does not return to the held pose " + id);
                    if (id.endsWith("reverse_grip") || id.endsWith("reverse_catch")) require(
                            !animation.groupMatrix("roll", 26).equals(animation.groupMatrix("body", 26), 1e-3f), "reverse grip never turns " + id);
                }
                for (String cue : animation.sounds().values()) require(soundConfig.contains("sounds." + cue), "missing sound cue " + id + "/" + cue);
                StringBuilder signature = new StringBuilder().append(animation.duration());
                for (int sample=0;sample<=animation.duration();sample++) signature.append(animation.groupMatrix("body",sample).hashCode());
                require(motionSignatures.add(signature.toString()), "duplicated weapon motion " + id);
                require(animation.duration() >= 30 && animation.duration() <= 120, "inspect duration " + id);
                for (boolean pack : List.of(false, true)) {
                    var parts = pack ? InspectRig.packParts(weapon, models.packModelScale()) : InspectRig.blockParts(weapon, base.parts());
                    for (boolean left : List.of(false, true)) for (boolean hand : List.of(false, true)) {
                        for (ModelPart part : parts) {
                            var first = InspectTransform.at(animation, part, 0, hand ? models.handModelScale() : models.modelScale(), left, hand);
                            var last = InspectTransform.at(animation, part, animation.duration(), hand ? models.handModelScale() : models.modelScale(), left, hand);
                            require(first.equals(last, .00003f), "joint does not return: " + id + "/" + part.id());
                            var vertices = pack ? vertices(root, weapon, part, artworkSkin) : cube();
                            for (int tick = 0; tick <= animation.duration(); tick++) {
                                var matrix = InspectTransform.at(animation, part, tick, hand ? models.handModelScale() : models.modelScale(), left, hand);
                                require(matrix.isFinite(), "nonfinite frame " + id);
                                // Conservative full cube for block parts; transparent pack canvas corners
                                // are excluded, so use the actual weapon alpha mask at its original UV.
                                if (!hand && !pack) for (int v = 0; v < 8; v++) {
                                    Vector3f point = matrix.transformPosition(new Vector3f(v & 1, v >> 1 & 1, v >> 2 & 1));
                                    require(Math.abs(point.x) < 1.4 && Math.abs(point.y) < 1.3 && Math.abs(point.z) < 1.1, "unbounded frame " + id);
                                }
                                if (!hand) for (Vector3f vertex : vertices) {
                                    Vector3f point = matrix.transformPosition(vertex, new Vector3f());
                                    double depth = models.anchor().forward() + point.z;
                                    double anchorRight = InspectRig.paired(weapon) ? 0 : models.anchor().right();
                                    double x = (left ? -anchorRight : anchorRight) - point.x;
                                    double y = models.anchor().up() + point.y;
                                    double halfHeight = Math.tan(Math.toRadians(70)/2) * depth;
                                    double required = Math.max(Math.abs(y)/Math.tan(Math.toRadians(70)/2), Math.abs(x)/(Math.tan(Math.toRadians(70)/2)*4/3)) - point.z;
                                    if (required > requiredForward) { requiredForward = required; furthest = id + "/" + part.id() + " tick=" + tick + " pack=" + pack; }
                                }
                                frames++;
                            }
                        }
                    }
                }
            }
        }
        require(models.anchor().forward() >= requiredForward + .015, "camera requires forward >= " + (requiredForward + .015) + " at " + furthest);
        require(all.size() == catalog.weapons().size() * 4, "weapon coverage");
        var spin = List.of(new InspectAnimation.Keyframe(0, null, new Vector3f(), null, InspectAnimation.Ease.LINEAR),
                new InspectAnimation.Keyframe(12, null, new Vector3f(0,0,360), null, InspectAnimation.Ease.LINEAR));
        var compound = new InspectAnimation("compound", Map.of(
                "body", new InspectAnimation.Group("body", null, new Vector3f(), spin),
                "child", new InspectAnimation.Group("child", "body", new Vector3f(), spin)), Map.of(), 1);
        for (int i = 1; i < compound.samples().size(); i++) require(compound.samples().get(i) - compound.samples().get(i - 1) == 1,
                "compound full turns used a sparse shortest-path interpolation");
        try {
            var a = new InspectAnimation.Group("a", "b", new Vector3f(), List.of());
            var b = new InspectAnimation.Group("b", "a", new Vector3f(), List.of());
            new InspectAnimation("cycle", Map.of("a", a, "b", b), Map.of(), 3); throw new AssertionError("cyclic rig accepted");
        } catch (IllegalArgumentException expected) { }
        try {
            File invalid = java.nio.file.Files.createTempFile("inspect-settings", ".yml").toFile();
            try {
                java.nio.file.Files.writeString(invalid.toPath(), "model-scale: .nan\npack-model-scale: -1\nview-range: .inf\nbrightness: 99\nanchor: {forward: .nan}\n");
                var bad = new InspectModels(invalid, message -> { });
                require(Float.isFinite(bad.modelScale()) && bad.packModelScale()>0 && Float.isFinite(bad.viewRange()) && bad.brightness()==15 && Double.isFinite(bad.anchor().forward()),"invalid scene settings");
            } finally { java.nio.file.Files.deleteIfExists(invalid.toPath()); }
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        var guide = new CaseGuide(new File(root, "case-guide.yml"), warnings::add);
        var offers = new ArrayList<ShopService.Offer>(); offers.add(new ShopService.Offer("key", "case_key", 1));
        for (var def : catalog.cases()) offers.add(new ShopService.Offer("case", def.id(), 3));
        for (var def : catalog.cases()) {
            var m = guide.measure(def, catalog, offers);
            require(m.cost() != null && m.cost() == 4 && Math.abs(m.value() * 4 - m.expectedPoints()) < 1e-9, "full dealer price " + def.id());
            require(m.favoriteChance() <= m.knifeChance()+1e-12 && Math.abs(m.knives().values().stream().mapToDouble(Double::doubleValue).sum() - m.knifeChance()) < 1e-12, "knife probabilities " + def.id());
            var unavailable = guide.measure(def, catalog, List.of()); require(unavailable.cost() == null && unavailable.value() == -1, "missing dealer price " + def.id());
            var huge = guide.measure(def, catalog, List.of(new ShopService.Offer("case", def.id(), Integer.MAX_VALUE), new ShopService.Offer("key", def.keyId(), Integer.MAX_VALUE)));
            require(huge.cost() == 4294967294L && Double.isFinite(huge.value()), "price overflow " + def.id());
        }
        require(warnings.isEmpty(), "guide warnings " + warnings);
        System.out.println("PASS: all " + catalog.weapons().size() + " weapon/glove rigs / " + all.size() + " individual timelines, " + frames + " finite joint frames in right/left eye/hand views, 70° 4:3 camera bounds; held-pose continuity, cyclic-rig rejection, full dealer price/overflow/unknown price and exact knife probabilities.");
    }
    private static List<Vector3f> cube() {
        var out = new ArrayList<Vector3f>(); for (int v=0;v<8;v++) out.add(new Vector3f(v&1,v>>1&1,v>>2&1)); return out;
    }
    private static void profileUpgrade(File root) {
        try (var in = InspectRigChecks.class.getResourceAsStream("/migrations/inspect-profiles-v1.yml")) {
            require(in != null, "missing stock profile migration");
            var current = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            var defaults = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new File(root, "inspect-profiles.yml"));
            String custom = current.getConfigurationSection("animations").getKeys(false).iterator().next();
            current.set("animations." + custom + ".substeps", 2);
            current.set("animation-pools.karambit", java.util.List.of(custom));
            require(InspectProfileDefaults.upgrade(current, defaults), "stock upgrade did not run");
            require(current.getInt("animations." + custom + ".substeps") == 2
                    && current.getStringList("animation-pools.karambit").equals(java.util.List.of(custom)), "custom animation/pool overwritten");
            require(current.getStringList("animation-pools.ak47").size() == 4, "existing stock server did not receive fourth variant");
            var reloaded = new org.bukkit.configuration.file.YamlConfiguration(); reloaded.loadFromString(current.saveToString());
            require(reloaded.getStringList("animation-pools.ak47").equals(defaults.getStringList("animation-pools.ak47")), "migration not serializable");
            require(!InspectProfileDefaults.upgrade(reloaded, defaults), "profile upgrade not idempotent");
            for (String baseline : List.of("v2", "v3")) try (var recent = InspectRigChecks.class.getResourceAsStream("/migrations/inspect-profiles-" + baseline + ".yml")) {
                var stock = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(recent, java.nio.charset.StandardCharsets.UTF_8));
                stock.set("animations.talon__blade_presentation.substeps", 2);
                require(InspectProfileDefaults.upgrade(stock, defaults), "1.2.0 ring timeline upgrade did not run");
                require(stock.getList("animations.karambit__finger_roll.groups.roll.pivot").equals(defaults.getList("animations.karambit__finger_roll.groups.roll.pivot")), "1.2.0 ring pivot not migrated");
                require(stock.getInt("animations.talon__blade_presentation.substeps") == 2, "custom ring timeline overwritten");
                require(!InspectProfileDefaults.upgrade(stock, defaults), "ring migration not idempotent");
            }
            System.out.println("PASS: existing stock inspect profiles upgrade to four variants; custom timelines/pools survive and migration is idempotent.");
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException error) { throw new IllegalStateException(error); }
    }
    private static final Map<String, byte[]> ORIGINAL_LAYERS = originalLayers();
    private static Map<String, byte[]> originalLayers() {
        var result = new HashMap<String,byte[]>();
        try (var input = InspectRigChecks.class.getResourceAsStream("/original-skins.zip");
             var zip = new java.util.zip.ZipInputStream(Objects.requireNonNull(input))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) if (entry.getName().contains("/inspect/")) result.put(entry.getName(),zip.readAllBytes());
            return Map.copyOf(result);
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
    private static List<Vector3f> vertices(File root, WeaponType weapon, ModelPart part, String skin) {
        try {
            var out = new ArrayList<Vector3f>(); String layer = part.material().substring(5);
            var original = dev.plattnericus.cases.render.ArgbImage.from(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(
                    Objects.requireNonNull(ORIGINAL_LAYERS.get("assets/mccases/textures/item/inspect/" + skin + "/" + layer + ".png")))));
            var source = InspectRig.presentation(weapon, original);
            float depth = (weapon.isKnife() ? (layer.startsWith("handle") || layer.equals("body") ? 1.2f : .45f) : 2.6f)/32;
            for (int yy=0;yy<128;yy++) {
                int first=128,last=-1;
                for (int xx=0;xx<128;xx++) {
                    if((source.get(xx, yy)>>>24)>=128) {first=Math.min(first,xx);last=xx;}
                }
                if(last>=first)for(int x:new int[]{first,last+1})for(int y:new int[]{yy,yy+1})for(float z:new float[]{-depth,depth})out.add(new Vector3f(x/128f-.5f,.5f-y/128f,z));
            }
            require(!out.isEmpty(), "empty rig layer " + weapon.id() + "/" + layer); return out;
        } catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
