package dev.plattnericus.cases.inspect;

import dev.plattnericus.cases.catalog.Hold;
import dev.plattnericus.cases.catalog.Region;
import dev.plattnericus.cases.catalog.WeaponCategory;
import dev.plattnericus.cases.catalog.WeaponType;
import dev.plattnericus.cases.render.SpriteFrame;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * First-person view model: where the weapon sits and how the player's own skin arms hold it.
 *
 * <p>Arms are Minecraft's player-head special model, stretched to one of two reserved box
 * signatures. The pack's entity shader recognises those signatures and samples the arm and sleeve
 * faces of the same skin, so the arm always wears the holder's real skin. Mirrored boxes are left
 * arms. Matrices map into the item-local space of the right-hand first-person context; the pack
 * mirrors the whole composition for the left hand.
 *
 * <p>Poses are designed in Minecraft's hand view space (x right, y up, -z forward, blocks) and
 * converted back through the vanilla hand transform of each use state, so the bow draw, crossbow
 * charge and loaded crossbow show deliberate poses instead of vanilla's sideways bow tilt. Weapon
 * sizes are measured on the canvas, so every skin of a weapon keeps its real proportions although
 * its sprite is fitted to the square.
 */
public final class HeldRig {

    /** Head box scale that marks a wide (4 px) arm: front 3.00, side 2.61, end 1.15 texel aspect. */
    public static final Vector3fc WIDE = new Vector3f(.5f, 1.5f, .575f);
    /** Head box scale that marks a slim (3 px) arm: front 4.67, side 3.50, end 1.33 texel aspect. */
    public static final Vector3fc SLIM = new Vector3f(.375f, 1.75f, .5f);

    /**
     * First custom model data string of a held skin: arm width, or the client-only inspect state that
     * hides the first-person model while the item keeps its hotbar icon.
     */
    public static final String WIDE_STATE = "mccases_wide", SLIM_STATE = "mccases_slim", INSPECT_STATE = "mccases_inspect";

    /**
     * Inspect scene items: the first-person scene is drawn only near the camera, the owner's own copy
     * of the body-hand scene only away from it (third person). The pack's item shader reads the tints.
     */
    public static final String NEAR_SCENE = "mccases_near", FAR_SCENE = "mccases_far";
    public static final int NEAR_TINT = 0xFEFCFD, FAR_TINT = 0xFDFEFC;

    /** Vanilla's first-person item anchor for the right hand. */
    private static final Vector3fc HAND = new Vector3f(.56f, -.52f, -.72f);

    public enum Pose { IDLE, BOW_DRAWN, CROSSBOW_CHARGING, CROSSBOW_LOADED }

    public record Arm(boolean left, Matrix4f matrix) { }

    /** One weapon copy with the arms holding it, in item-local space of the right-hand context. */
    public record Hand(Matrix4f weapon, List<Arm> arms) { }

    private record Template(Vector3f grip, float yaw, float pitch, float roll, boolean faceCamera, float scale,
                            Vector3f elbow, float supportDrop, Vector3f supportElbow, float armScale) {
        Template sized(float factor) {
            return new Template(grip, yaw, pitch, roll, faceCamera, scale * factor, elbow, supportDrop, supportElbow, armScale);
        }
    }

    private static final Template KNIFE = new Template(new Vector3f(.60f, -.46f, -1.05f), 50, 32, -25, true, .75f,
            new Vector3f(-.18f, -.38f, .60f), 0, new Vector3f(), .85f);
    private static final Template REVERSE = new Template(new Vector3f(.50f, -.30f, -.95f), -150, 6, 0, true, .75f,
            new Vector3f(-.16f, -.40f, .60f), 0, new Vector3f(), .85f);
    private static final Template PISTOL = new Template(new Vector3f(.26f, -.38f, -.88f), 35, 10, 25, false, .85f,
            new Vector3f(.15f, -.45f, .55f), 0, new Vector3f(-.40f, -.40f, .40f), .8f);
    private static final Template PISTOL_AIM = new Template(new Vector3f(.10f, -.30f, -.74f), 10, 4, 12, false, .85f,
            new Vector3f(.22f, -.42f, .50f), 0, new Vector3f(-.36f, -.42f, .42f), .8f);
    private static final Template PISTOL_RELOAD = new Template(new Vector3f(.22f, -.44f, -.82f), 55, 30, 60, false, .85f,
            new Vector3f(.18f, -.45f, .55f), 0, new Vector3f(-.30f, -.50f, .35f), .8f);
    private static final Template LONG = new Template(new Vector3f(.44f, -.48f, -1.10f), 30, 14, 40, false, 1.2f,
            new Vector3f(.38f, -.40f, .45f), .05f, new Vector3f(-.32f, -.62f, .30f), .8f);
    private static final Template LONG_AIM = new Template(new Vector3f(.20f, -.36f, -.88f), 9, 4, 16, false, 1.2f,
            new Vector3f(.36f, -.42f, .48f), .05f, new Vector3f(-.30f, -.60f, .32f), .8f);
    private static final Template LONG_RELOAD = new Template(new Vector3f(.40f, -.56f, -1.00f), 48, 26, 70, false, 1.2f,
            new Vector3f(.36f, -.40f, .48f), .05f, new Vector3f(-.26f, -.62f, .34f), .8f);

    /** Built-in hands for the stock artwork in canvas coordinates; weapons.yml {@code hold} overrides them. */
    private static final Map<String, Hold> STOCK = Map.ofEntries(
            Map.entry("glock18", pistol(.276, .564)), Map.entry("usps", pistol(.180, .575)), Map.entry("p2000", pistol(.265, .596)),
            Map.entry("p250", pistol(.265, .585)), Map.entry("fiveseven", pistol(.244, .596)), Map.entry("tec9", pistol(.191, .575)),
            Map.entry("cz75", pistol(.255, .585)), Map.entry("dual_berettas", pistol(.276, .649)), Map.entry("deagle", pistol(.265, .617)),
            Map.entry("r8_revolver", pistol(.244, .628)), Map.entry("zeus", Hold.oneHanded(.276, .607)),
            Map.entry("mac10", Hold.twoHanded(.404, .649, .639, .521)), Map.entry("mp9", Hold.twoHanded(.425, .628, .671, .585)),
            Map.entry("mp7", Hold.twoHanded(.425, .607, .660, .564)), Map.entry("mp5sd", Hold.twoHanded(.244, .553, .681, .457)),
            Map.entry("ump45", Hold.twoHanded(.233, .585, .628, .500)), Map.entry("p90", Hold.twoHanded(.191, .500, .553, .553)),
            Map.entry("ppbizon", Hold.twoHanded(.223, .596, .521, .543)), Map.entry("ak47", Hold.twoHanded(.329, .532, .639, .479)),
            Map.entry("m4a4", Hold.twoHanded(.319, .553, .671, .468)), Map.entry("m4a1s", Hold.twoHanded(.297, .553, .617, .468)),
            Map.entry("galil", Hold.twoHanded(.308, .553, .628, .457)), Map.entry("famas", Hold.twoHanded(.404, .564, .607, .500)),
            Map.entry("aug", Hold.twoHanded(.500, .564, .575, .628)), Map.entry("sg553", Hold.twoHanded(.276, .564, .628, .468)),
            Map.entry("ssg08", Hold.twoHanded(.372, .489, .553, .457)), Map.entry("awp", Hold.twoHanded(.297, .500, .500, .489)),
            Map.entry("scar20", Hold.twoHanded(.308, .553, .607, .457)), Map.entry("g3sg1", Hold.twoHanded(.308, .553, .607, .468)),
            Map.entry("nova", Hold.twoHanded(.308, .500, .585, .479)), Map.entry("xm1014", Hold.twoHanded(.244, .543, .553, .479)),
            Map.entry("sawedoff", Hold.twoHanded(.212, .575, .553, .479)), Map.entry("mag7", Hold.twoHanded(.351, .607, .543, .585)),
            Map.entry("m249", Hold.twoHanded(.223, .564, .553, .500)), Map.entry("negev", Hold.twoHanded(.244, .553, .521, .500)),
            Map.entry("karambit", Hold.oneHanded(.201, .532)), Map.entry("talon", Hold.oneHanded(.265, .521)),
            Map.entry("butterfly", Hold.oneHanded(.201, .479)), Map.entry("flip", Hold.oneHanded(.223, .489)),
            Map.entry("gut", Hold.oneHanded(.244, .489)), Map.entry("falchion", Hold.oneHanded(.223, .500)),
            Map.entry("shadow_daggers", Hold.oneHanded(.095, .500)), Map.entry("navaja", Hold.oneHanded(.201, .511)),
            Map.entry("paracord", Hold.oneHanded(.180, .500)), Map.entry("survival", Hold.oneHanded(.180, .489)),
            Map.entry("skeleton", Hold.oneHanded(.180, .489)));

    private HeldRig() {
    }

    private static Hold pistol(double x, double y) {
        return Hold.twoHanded(x, y, x + .03, y + .085);
    }

    /** Karambit and Talon are held with the blade below the little finger. */
    public static boolean reverseGrip(WeaponType weapon) {
        return InspectRig.reverseGrip(weapon);
    }

    /** Gloves are worn, not held: they keep the profile inspects and have no held view model. */
    public static boolean held(WeaponType weapon) {
        return weapon.category() != WeaponCategory.GLOVE;
    }

    /** The frame of a weapon's inspect rig, which the held view model and the inspect scenes show. */
    public static SpriteFrame rigFrame(WeaponType weapon, dev.plattnericus.cases.render.WeaponAssets assets) {
        return SpriteFrame.rig(assets, reverseGrip(weapon));
    }

    /**
     * Joint of a movable rig layer in canvas coordinates: the pivot pins of folding blades and the two
     * butterfly pins. The butterfly's safe handle (handle_a) stays in the hand; the blade turns about
     * the safe-handle pin and the bite handle (handle_b) about its own pin on the blade. Slides, bolts
     * and pumps only move, so they report the canvas centre.
     */
    public static double[] hinge(WeaponType weapon, String layer) {
        return switch (weapon.id() + "/" + layer) {
            case "butterfly/blade", "butterfly/handle_a" -> new double[]{.505, .463};
            case "butterfly/handle_b" -> new double[]{.505, .521};
            case "flip/blade", "falchion/blade" -> new double[]{.44, .5};
            case "stiletto/blade" -> new double[]{.465, .5};
            case "navaja/blade" -> new double[]{.415, .505};
            case "ursus/blade" -> new double[]{.41, .5};
            default -> new double[]{.5, .5};
        };
    }

    /**
     * The rig layer a layer is pinned to, or null for layers pinned to the weapon in the hand: the
     * butterfly's bite handle rides on the blade, so it swings around with it.
     */
    public static String parent(WeaponType weapon, String layer) {
        return weapon.id().equals("butterfly") && layer.equals("handle_b") ? "blade" : null;
    }

    /**
     * Where a released weapon turns, in canvas coordinates: ring knives about the ring on the index
     * finger, other knives about their balance where the blade meets the handle (finger twirls and
     * tosses), everything else about the canvas centre.
     */
    public static double[] spinPoint(WeaponType weapon) {
        switch (weapon.id()) {
            case "karambit": return new double[]{.146, .514};
            case "talon": return new double[]{.100, .521};
            default: break;
        }
        Region blade = weapon.category() == WeaponCategory.KNIFE ? weapon.region("blade") : null;
        if (blade == null) return new double[]{.5, .5};
        return new double[]{blade.x1(), (blade.y1() + blade.y2()) / 2};
    }

    /** The configured, built-in or category-default hold of a weapon, in canvas coordinates. */
    public static Hold hold(WeaponType weapon) {
        if (weapon.hold() != null) return weapon.hold();
        Hold stock = STOCK.get(weapon.id());
        if (stock != null) return stock;
        return switch (weapon.category()) {
            case KNIFE -> {
                Region blade = weapon.region("blade");
                yield Hold.oneHanded(blade == null ? .20 : blade.x1() * .45, blade == null ? .49 : (blade.y1() + blade.y2()) / 2);
            }
            case PISTOL -> pistol(.26, .59);
            case EQUIPMENT -> Hold.oneHanded(.28, .61);
            default -> Hold.twoHanded(.31, .55, .62, .47);
        };
    }

    /** Inspect family of a weapon, used to choose its held-pose inspect pool; gloves have none. */
    public static String family(WeaponType weapon) {
        if (!held(weapon)) return "glove";
        if (reverseGrip(weapon)) return "reverse";
        if (dual(weapon)) return "daggers";
        return switch (weapon.category()) {
            case KNIFE -> weapon.id().equals("butterfly") ? "butterfly" : "knife";
            case PISTOL -> weapon.id().equals("deagle") || weapon.id().equals("r8_revolver") ? "revolver" : "pistol";
            case EQUIPMENT -> "pistol";
            case SMG -> "smg";
            case SNIPER -> "sniper";
            case HEAVY -> "heavy";
            default -> "rifle";
        };
    }

    /**
     * The idle first-person hold in view space, before any use transform: where the grip sits, how
     * the weapon is turned and scaled, and where the elbows and the support hand rest.
     */
    public record Stance(Vector3f grip, Matrix4f rotation, float scale, Vector3f elbow, boolean twoHanded,
                         Vector3f support, Vector3f supportElbow, float armScale) {
    }

    /**
     * The hold seen by other players at the body hand in Minecraft's crossbow hold: the weapon is
     * presented across the chest with its skin facing forward, the barrel or blade towards the second
     * hand; reverse grips hang the blade below the fist.
     */
    public static Stance bodyStance(WeaponType weapon, SpriteFrame frame) {
        Stance view = stance(weapon, frame);
        boolean knife = weapon.category() == WeaponCategory.KNIFE;
        Matrix4f rotation = reverseGrip(weapon) ? weaponRotation(90, 60, 0)
                : knife ? weaponRotation(90, 35, 0) : weaponRotation(95, 8, 0);
        return new Stance(view.grip(), rotation, view.scale(), view.elbow(), view.twoHanded(), view.support(),
                view.supportElbow(), view.armScale());
    }

    public static Stance stance(WeaponType weapon, SpriteFrame frame) {
        Template t = template(weapon, Pose.IDLE);
        Hold hold = hold(weapon);
        float roll = t.roll() + (t.faceCamera() ? cameraFacingRoll(t.yaw(), t.pitch(), t.grip()) : 0);
        Matrix4f rotation = weaponRotation(t.yaw(), t.pitch(), roll);
        boolean twoHanded = hold.twoHanded() && weapon.category() != WeaponCategory.KNIFE;
        Vector3f support = twoHanded ? inGripFrame(weapon, frame, hold.supportX(), hold.supportY()).sub(0, t.supportDrop(), 0) : new Vector3f();
        return new Stance(new Vector3f(t.grip()), rotation, t.scale(), new Vector3f(t.elbow()), twoHanded, support,
                new Vector3f(t.supportElbow()), t.armScale());
    }

    /** Push daggers come as a pair, one in each hand. */
    public static boolean dual(WeaponType weapon) {
        return weapon.id().equals("shadow_daggers");
    }

    /**
     * First-person composition in the right-hand context. {@code frame} places the weapon canvas in
     * its fitted sprite; the second copy of dual weapons sits mirrored in the left hand.
     */
    public static List<Hand> firstPerson(WeaponType weapon, SpriteFrame frame, Pose pose, boolean slim) {
        Template template = template(weapon, pose);
        Hold hold = hold(weapon);
        Matrix4f toLocal = frame(pose).invert();
        List<Hand> hands = new ArrayList<>();
        hands.add(hand(weapon, frame, template, hold, slim, toLocal, false));
        if (dual(weapon)) hands.add(hand(weapon, frame, template, hold, slim, toLocal, true));
        return List.copyOf(hands);
    }

    /**
     * Third-person hold: the player model's hand layer turns the item -90 degrees about X and 180
     * about Y; this rotation brings the blade or muzzle forward with the grip in the palm.
     */
    public static Matrix4f thirdPerson(WeaponType weapon, SpriteFrame frame) {
        float size = switch (weapon.category()) {
            case KNIFE -> .75f;
            case PISTOL, EQUIPMENT -> .55f;
            case SMG -> .8f;
            default -> 1f;
        };
        // A Y turn of 90 degrees points the muzzle straight ahead; a little more angles it inwards.
        boolean knife = weapon.category() == WeaponCategory.KNIFE;
        return new Matrix4f().translation(.5f, .5f, .5f)
                .rotateXYZ((float) Math.toRadians(90), (float) Math.toRadians(knife ? 104 : 98), 0)
                .scale(size).mul(gripFrame(weapon, frame));
    }

    /**
     * Matrix from the sprite's item space into the grip frame: the grip at the origin, blade or
     * barrel along +X with the artwork's top up, one canvas width per unit.
     */
    public static Matrix4f gripFrame(WeaponType weapon, SpriteFrame frame) {
        Hold hold = hold(weapon);
        double[] grip = frame.map(hold.gripX(), hold.gripY());
        Matrix4f m = new Matrix4f();
        // A reverse grip holds the blade below the fist; flipped rig textures are already turned so.
        if (reverseGrip(weapon) && !frame.flipped()) m.rotateZ((float) Math.PI);
        if (frame.turned()) m.rotateZ((float) Math.toRadians(-45));
        return m.scale((float) (1 / frame.zoom())).translate((float) -grip[0], (float) (grip[1] - 1), -.5f);
    }

    /** A canvas point in the weapon's grip frame (see {@link #gripFrame}). */
    public static Vector3f inGripFrame(WeaponType weapon, SpriteFrame frame, double x, double y) {
        double[] point = frame.map(x, y);
        return gripFrame(weapon, frame).transformPosition(new Vector3f((float) point[0], (float) (1 - point[1]), .5f));
    }

    private static Hand hand(WeaponType weapon, SpriteFrame frame, Template t, Hold hold, boolean slim, Matrix4f toLocal, boolean mirror) {
        Vector3f grip = new Vector3f(t.grip());
        float roll = t.roll();
        if (t.faceCamera()) roll += cameraFacingRoll(t.yaw(), t.pitch(), grip);
        Matrix4f placement = new Matrix4f().translation(grip).mul(weaponRotation(t.yaw(), t.pitch(), roll)).scale(t.scale());
        Matrix4f weaponView = new Matrix4f(placement).mul(gripFrame(weapon, frame));
        List<Arm> arms = new ArrayList<>();
        arms.add(new Arm(false, arm(grip, new Vector3f(grip).add(t.elbow()), false, slim, t.armScale())));
        if (hold.twoHanded() && weapon.category() != WeaponCategory.KNIFE) {
            Vector3f support = inGripFrame(weapon, frame, hold.supportX(), hold.supportY()).sub(0, t.supportDrop(), 0);
            Vector3f supportView = placement.transformPosition(support);
            arms.add(new Arm(true, arm(supportView, new Vector3f(supportView).add(t.supportElbow()), true, slim, t.armScale())));
        }
        Matrix4f mirrorView = mirror ? new Matrix4f().scaling(-1, 1, 1) : new Matrix4f();
        Matrix4f weaponLocal = new Matrix4f(toLocal).mul(mirrorView).mul(weaponView);
        List<Arm> localArms = arms.stream().map(a -> new Arm(a.left() != mirror,
                new Matrix4f(toLocal).mul(mirrorView).mul(a.matrix()))).toList();
        return new Hand(weaponLocal, localArms);
    }

    /**
     * An arm box from the hand end (at {@code hand}) towards {@code elbow}. The returned matrix maps the
     * player-head model, whose neck end becomes the hand, into the frame of both points.
     */
    public static Matrix4f arm(Vector3fc hand, Vector3fc elbow, boolean left, boolean slim, float scale) {
        Vector3fc signature = slim ? SLIM : WIDE;
        return armFrame(hand, elbow, left).scale((left ? -1 : 1) * signature.x() * scale, signature.y() * scale, signature.z() * scale);
    }

    /** Position and orientation of an arm: +Y runs from the elbow to the hand, +X faces outwards. */
    public static Matrix4f armFrame(Vector3fc hand, Vector3fc elbow, boolean left) {
        return armFrame(hand, elbow, new Vector3f(left ? -1 : 1, 0, 0));
    }

    /** As above with an explicit outward reference, which lets the forearm twist with the wrist. */
    public static Matrix4f armFrame(Vector3fc hand, Vector3fc elbow, Vector3fc outward) {
        Vector3f y = new Vector3f(hand).sub(elbow).normalize();
        Vector3f reference = new Vector3f(outward);
        Vector3f x = new Vector3f(reference).sub(new Vector3f(y).mul(reference.dot(y)));
        if (x.lengthSquared() < 1e-6f) x.set(0, 0, 1).sub(new Vector3f(y).mul(y.z));
        x.normalize();
        Vector3f z = new Vector3f(x).cross(y);
        return new Matrix4f().translation(new Vector3f(hand)).mul(new Matrix4f().set3x3(new Matrix3f(x, y, z)));
    }

    private static Template template(WeaponType weapon, Pose pose) {
        if (weapon.category() == WeaponCategory.KNIFE) {
            return reverseGrip(weapon) ? REVERSE : KNIFE;
        }
        boolean shortArm = weapon.category() == WeaponCategory.PISTOL || weapon.category() == WeaponCategory.EQUIPMENT;
        Template base = switch (pose) {
            case BOW_DRAWN -> shortArm ? PISTOL_AIM : LONG_AIM;
            case CROSSBOW_CHARGING -> shortArm ? PISTOL_RELOAD : LONG_RELOAD;
            default -> shortArm ? PISTOL : LONG;
        };
        return switch (weapon.category()) {
            case SMG -> base.sized(.88f);
            case SNIPER -> base.sized(1.05f);
            default -> base;
        };
    }

    /** Barrel or blade along view -z for yaw/pitch/roll 0, artwork up stays up. */
    private static Matrix4f weaponRotation(float yaw, float pitch, float roll) {
        return new Matrix4f().rotateY((float) Math.toRadians(yaw)).rotateX((float) Math.toRadians(pitch))
                .rotateY((float) (Math.PI / 2)).rotateX((float) Math.toRadians(roll));
    }

    /**
     * Knife flats turn towards the camera instead of showing only their spine, with the spine up and the
     * edge down like a real hammer grip: of the two rolls that show a flat, the one with the artwork's top up.
     */
    private static float cameraFacingRoll(float yaw, float pitch, Vector3fc grip) {
        Vector3f camera = new Vector3f(grip).negate().normalize();
        float best = 0;
        double score = -2;
        for (int roll = -180; roll < 180; roll += 2) {
            Matrix4f rotation = weaponRotation(yaw, pitch, roll);
            if (rotation.transformDirection(new Vector3f(0, 1, 0)).y < 0) continue;
            double s = Math.abs(rotation.transformDirection(new Vector3f(0, 0, 1)).dot(camera));
            if (s > score) { score = s; best = roll; }
        }
        return best;
    }

    /**
     * View-space frame of the item-local space for a use state: vanilla's hand anchor, its use
     * transform halfway through the draw (so the remaining stretch stays within 10%) and the item centre.
     */
    public static Matrix4f frame(Pose pose) {
        Matrix4f frame = new Matrix4f().translation(HAND);
        switch (pose) {
            case BOW_DRAWN -> frame.translate(-.2785682f, .18344387f, .15731531f).rotateX(rad(-13.935f)).rotateY(rad(35.3f))
                    .rotateZ(rad(-9.785f)).translate(0, 0, .02f).scale(1, 1, 1.1f).rotateY(rad(-45f));
            case CROSSBOW_CHARGING -> frame.translate(-.4785682f, -.094387f, .05731531f).rotateX(rad(-11.935f)).rotateY(rad(65.3f))
                    .rotateZ(rad(-9.785f)).translate(0, 0, .02f).scale(1, 1, 1.1f).rotateY(rad(-45f));
            case CROSSBOW_LOADED -> frame.translate(-.641864f, 0, 0).rotateY(rad(10f));
            case IDLE -> { }
        }
        return frame.translate(-.5f, -.5f, -.5f);
    }

    private static float rad(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}
