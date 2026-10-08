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
        int frames = 0; Set<String> all = new HashSet<>();
        for (WeaponType weapon : catalog.weapons()) {
            var pool = models.pool(weapon.id()); require(pool.size() == 3, "three individual variants required: " + weapon.id());
            var base = models.model(weapon.isKnife() ? weapon.id() : weapon.category().name().toLowerCase(Locale.ROOT));
            for (String id : pool) {
                require(all.add(id) && id.startsWith(weapon.id() + "__"), "shared weapon timeline " + id);
                InspectAnimation animation = models.animation(id);
                require(animation.duration() >= 30 && animation.duration() <= 120, "inspect duration " + id);
                for (boolean pack : List.of(false, true)) {
                    var parts = pack ? InspectRig.packParts(weapon, models.packModelScale()) : InspectRig.blockParts(weapon, base.parts());
                    for (boolean left : List.of(false, true)) for (boolean hand : List.of(false, true)) {
                        for (ModelPart part : parts) {
                            var first = InspectTransform.at(animation, part, 0, hand ? models.handModelScale() : models.modelScale(), left, hand);
                            var last = InspectTransform.at(animation, part, animation.duration(), hand ? models.handModelScale() : models.modelScale(), left, hand);
                            require(first.equals(last, .00003f), "joint does not return: " + id + "/" + part.id());
                            for (int tick = 0; tick <= animation.duration(); tick++) {
                                var matrix = InspectTransform.at(animation, part, tick, hand ? models.handModelScale() : models.modelScale(), left, hand);
                                require(matrix.isFinite(), "nonfinite frame " + id);
                                // Conservative full cube for block parts; transparent pack canvas corners
                                // are excluded, so use the actual weapon alpha mask at its original UV.
                                if (!hand && !pack) for (int v = 0; v < 8; v++) {
                                    Vector3f point = matrix.transformPosition(new Vector3f(v & 1, v >> 1 & 1, v >> 2 & 1));
                                    require(Math.abs(point.x) < 1.4 && Math.abs(point.y) < 1.3 && Math.abs(point.z) < 1.1, "unbounded frame " + id);
                                }
                                frames++;
                            }
                        }
                    }
                }
            }
        }
        require(all.size() == 165, "weapon coverage");
        try {
            var a = new InspectAnimation.Group("a", "b", new Vector3f(), List.of());
            var b = new InspectAnimation.Group("b", "a", new Vector3f(), List.of());
            new InspectAnimation("cycle", Map.of("a", a, "b", b), Map.of(), 3); throw new AssertionError("cyclic rig accepted");
        } catch (IllegalArgumentException expected) { }
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
        System.out.println("PASS: all 55 weapon rigs / 165 individual timelines, " + frames + " finite joint frames in right/left eye/hand views; held-pose continuity, cyclic-rig rejection, full dealer price/overflow/unknown price and exact knife probabilities.");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
