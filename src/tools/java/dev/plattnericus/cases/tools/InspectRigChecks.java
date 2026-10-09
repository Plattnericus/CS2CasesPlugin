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
        require(warnings.isEmpty(), "inspect warnings " + warnings);
        var soundConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new File(root, "sounds.yml"));
        int frames = 0; double requiredForward = 0; String furthest = ""; Set<String> all = new HashSet<>(), motionSignatures = new HashSet<>();
        for (WeaponType weapon : catalog.weapons()) {
            require(catalog.skins().stream().anyMatch(s -> s.weapon().id().equals(weapon.id())), "unobtainable weapon " + weapon.id());
            var pool = models.pool(weapon.id()); require(pool.size() == 3, "three individual variants required: " + weapon.id());
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
                            var vertices = pack ? vertices(root, weapon, part) : cube();
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
        require(all.size() == catalog.weapons().size() * 3, "weapon coverage");
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
    private static List<Vector3f> vertices(File root, WeaponType weapon, ModelPart part) {
        try {
            var source = javax.imageio.ImageIO.read(new File(root, "textures/" + weapon.textureFolder() + "/base.png"));
            var out = new ArrayList<Vector3f>(); String layer = part.material().substring(5);
            float depth = (weapon.isKnife() ? (layer.startsWith("handle") || layer.equals("body") ? 1.2f : .45f) : 2.6f)/32;
            for (int yy=0;yy<64;yy++) {
                int first=64,last=-1;
                for (int xx=0;xx<64;xx++) {
                    double x=(xx-1.5)/60,y=(yy-1.5)/60;
                    if(x<0 || x>=1 || y<0 || y>=1)continue;
                    if((source.getRGB(Math.min(source.getWidth()-1,(int)(x*source.getWidth())), Math.min(source.getHeight()-1,(int)(y*source.getHeight())))>>>24)>10
                       && layer.equals(InspectRig.layerAt(weapon,x,y))) {first=Math.min(first,xx);last=xx;}
                }
                if(last>=first)for(int x:new int[]{first,last+1})for(int y:new int[]{yy,yy+1})for(float z:new float[]{-depth,depth})out.add(new Vector3f(x/64f-.5f,.5f-y/64f,z));
            }
            require(!out.isEmpty(), "empty rig layer " + weapon.id() + "/" + layer); return out;
        } catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
