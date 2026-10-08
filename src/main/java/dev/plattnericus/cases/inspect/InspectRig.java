package dev.plattnericus.cases.inspect;

import dev.plattnericus.cases.catalog.WeaponType;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The pack exporter and live scene share the same joints and texture partitions. */
public final class InspectRig {
    private static final Set<String> FOLDERS = Set.of("flip", "navaja", "stiletto", "falchion");
    private InspectRig() { }
    public record Layer(String id, String group, Vector3f position) { }

    public static List<Layer> layers(WeaponType weapon) {
        String id = weapon.id();
        if (id.equals("shadow_daggers") || id.equals("dual_berettas")) return List.of(
                new Layer("body", "pair_a", new Vector3f(-.09f, .07f, -.035f)),
                new Layer("body", "pair_b", new Vector3f(.09f, -.07f, .035f)));
        if (id.equals("butterfly")) return List.of(new Layer("blade", "body", new Vector3f()),
                new Layer("handle_a", "handle_a", new Vector3f()), new Layer("handle_b", "handle_b", new Vector3f()));
        if (FOLDERS.contains(id)) return List.of(new Layer("body", "body", new Vector3f()), new Layer("blade", "blade", new Vector3f()));
        if (!weapon.isKnife()) return List.of(new Layer("body", "body", new Vector3f()), new Layer("mechanism", "mechanism", new Vector3f()));
        return List.of(new Layer("body", "body", new Vector3f()));
    }

    /** Coordinates refer to the original, unrotated square weapon canvas, preserving the hinge. */
    public static String layerAt(WeaponType weapon, double x, double y) {
        if (weapon.id().equals("butterfly")) {
            if (x >= .505) return "blade";
            return y < .49 ? "handle_a" : "handle_b";
        }
        if (FOLDERS.contains(weapon.id())) {
            var blade = weapon.region("blade");
            if (x >= (blade == null ? .48 : blade.x1())) return "blade";
        }
        if (!weapon.isKnife() && !weapon.id().equals("dual_berettas")) {
            boolean moving = switch (weapon.id()) {
                case "r8_revolver" -> x > .28 && x < .52 && y > .36 && y < .54;
                case "zeus" -> x > .65 && y < .52;
                case "nova", "sawedoff" -> x > .55 && x < .79 && y > .46 && y < .59;
                case "p90" -> x > .20 && x < .64 && y > .34 && y < .44;
                case "ppbizon" -> x > .43 && x < .81 && y > .52 && y < .61;
                case "m249", "negev" -> x > .31 && x < .60 && y > .35 && y < .45;
                default -> weapon.category() == dev.plattnericus.cases.catalog.WeaponCategory.PISTOL
                        ? y > .30 && y < .49 && x > .12 : x > .30 && x < .50 && y > .38 && y < .49;
            };
            if (moving) return "mechanism";
        }
        return "body";
    }

    public static List<ModelPart> packParts(WeaponType weapon, float scale) {
        List<ModelPart> parts = new ArrayList<>();
        for (Layer layer : layers(weapon)) parts.add(new ModelPart(layer.group(), ModelPart.Type.ITEM, "$rig:" + layer.id(),
                new Vector3f(layer.position()), new Vector3f(scale), new Vector3f(), layer.group(), false));
        return List.copyOf(parts);
    }

    public static List<ModelPart> blockParts(WeaponType weapon, List<ModelPart> source) {
        List<ModelPart> parts = new ArrayList<>();
        boolean pair = weapon.id().equals("shadow_daggers") || weapon.id().equals("dual_berettas");
        for (ModelPart p : source) {
            String group = FOLDERS.contains(weapon.id()) && (p.id().startsWith("blade") || p.id().equals("tip") || p.id().equals("edge")) ? "blade" : p.group();
            if (!weapon.isKnife() && (p.id().equals("slide") || p.id().equals("handguard") || p.id().equals("magazine"))) group = "mechanism";
            if (pair) for (Layer layer : layers(weapon)) parts.add(new ModelPart(p.id() + "_" + layer.group(), p.type(), p.material(),
                    new Vector3f(p.position()).mul(.7f).add(layer.position()), new Vector3f(p.size()).mul(.7f), p.rotation(), layer.group(), p.glow()));
            else parts.add(new ModelPart(p.id(), p.type(), p.material(), p.position(), p.size(), p.rotation(), group, p.glow()));
        }
        return List.copyOf(parts);
    }
}
