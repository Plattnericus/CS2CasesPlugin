package dev.plattnericus.cases.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.plattnericus.cases.catalog.CatalogLoader;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.catalog.WeaponCategory;
import dev.plattnericus.cases.catalog.WeaponType;
import dev.plattnericus.cases.inspect.HeldRig;
import dev.plattnericus.cases.inspect.InspectAnimation;
import dev.plattnericus.cases.inspect.InspectModels;
import dev.plattnericus.cases.inspect.InspectRig;
import dev.plattnericus.cases.inspect.ModelPose;
import dev.plattnericus.cases.inspect.ViewInspect;
import dev.plattnericus.cases.pack.PackExporter;
import dev.plattnericus.cases.render.RenderSettings;
import dev.plattnericus.cases.render.SkinRenderer;
import dev.plattnericus.cases.render.SpriteFrame;
import dev.plattnericus.cases.render.TextureStore;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Checks the held view model, held-pose inspects and the exported pack without a client: hard
 * sprite and rig edges, item definitions and shaders, grips meeting the hands, arm signatures, framing in
 * the 70 degree first-person view and inspects that start and end in the hold.
 */
public final class HeldChecks {
    private static final float TAN = (float) Math.tan(Math.toRadians(35));

    private HeldChecks() {
    }

    public static void main(String[] args) throws Exception {
        File defaults = new File(args[0]);
        TextureStore textures = new TextureStore(new File(defaults, "textures"));
        var catalog = new CatalogLoader(new File(defaults, "catalog"), textures).load(1).catalog();
        var renderer = new SkinRenderer(textures, RenderSettings.defaults());
        int poses = 0, weapons = 0;
        for (WeaponType weapon : catalog.weapons()) {
            if (catalog.skins().stream().noneMatch(s -> s.weapon() == weapon)) continue;
            if (!HeldRig.held(weapon)) continue;
            SpriteFrame frame = HeldRig.rigFrame(weapon, textures.weapon(weapon.id(), weapon.textureFolder()));
            for (HeldRig.Pose pose : HeldRig.Pose.values()) {
                if (weapon.isKnife() && pose != HeldRig.Pose.IDLE) continue;
                for (boolean slim : List.of(false, true)) {
                    List<HeldRig.Hand> hands = HeldRig.firstPerson(weapon, frame, pose, slim);
                    require(hands.size() == (HeldRig.dual(weapon) ? 2 : 1), "hand copies " + weapon.id());
                    Matrix4f view = HeldRig.frame(pose);
                    for (HeldRig.Hand hand : hands) {
                        Matrix4f weaponView = new Matrix4f(view).mul(hand.weapon());
                        // The grip, measured on the canvas, must sit in the first arm's hand.
                        Vector3f gripPoint = new Matrix4f(view).mul(hand.weapon()).mul(HeldRig.gripFrame(weapon, frame).invert(new Matrix4f()))
                                .transformPosition(new Vector3f());
                        require(hand.arms().size() == (weapon.category() == WeaponCategory.KNIFE || !HeldRig.hold(weapon).twoHanded() ? 1 : 2),
                                "arm count " + weapon.id());
                        Vector3f handEnd = new Matrix4f(view).mul(hand.arms().getFirst().matrix()).transformPosition(new Vector3f());
                        require(handEnd.distance(gripPoint) < 1e-3, "grip detached from hand " + weapon.id() + " " + pose);
                        for (HeldRig.Arm arm : hand.arms()) {
                            Matrix4f m = new Matrix4f(view).mul(arm.matrix());
                            Vector3f hand0 = m.transformPosition(new Vector3f());
                            Vector3f shoulder = m.transformPosition(new Vector3f(0, -.5f, 0));
                            require(shoulder.y < hand0.y + .02f, "arm points upwards " + weapon.id() + " " + pose);
                            require(onScreen(hand0, .98f), "hand off screen " + weapon.id() + " " + pose + " " + hand0);
                            // Only the reserved box signature turns the head into an arm.
                            Vector3f sx = m.transformDirection(new Vector3f(1, 0, 0)), sy = m.transformDirection(new Vector3f(0, 1, 0));
                            Vector3f sz = m.transformDirection(new Vector3f(0, 0, 1));
                            var sig = slim ? HeldRig.SLIM : HeldRig.WIDE;
                            float front = sy.length() / sx.length(), side = sy.length() / sz.length();
                            float tolerance = pose == HeldRig.Pose.IDLE || pose == HeldRig.Pose.CROSSBOW_LOADED ? .01f : .12f;
                            require(Math.abs(front / (sig.y() / sig.x()) - 1) < tolerance && Math.abs(side / (sig.y() / sig.z()) - 1) < tolerance,
                                    "arm signature distorted " + weapon.id() + " " + pose);
                            require(sx.cross(sy, new Vector3f()).dot(sz) * (arm.left() ? -1 : 1) > 0, "arm handedness " + weapon.id());
                        }
                        Vector3f muzzle = weaponView.transformPosition(new Vector3f(.5f, .5f, .5f));
                        require(onScreen(muzzle, 1.15f), "weapon centre leaves the view " + weapon.id() + " " + pose);
                        poses++;
                    }
                }
            }
            weapons++;
        }
        int animations = checkViewAnimations(defaults, catalog, textures);
        int skins = checkPack(new File(args[1]), catalog, renderer);
        System.out.println("PASS: held view model for " + weapons + " weapons / " + poses + " hand poses (grip in hand, arm signatures,"
                + " handedness, 70 degree framing), " + animations + " held-pose inspects start and end in the hold and stay clear of the"
                + " model for other players (furthest ahead of the fist looking ahead " + String.format(java.util.Locale.ROOT, "%.2f", maxBodyPush) + " blocks), " + skins
                + " skins with hard-edged sprites and rigs, skin-arm view models and inspect scene layers.");
    }

    private static boolean onScreen(Vector3f view, float margin) {
        float depth = -view.z;
        if (depth <= .05f) return false;
        float x = view.x / (depth * TAN * 16f / 9f), y = view.y / (depth * TAN);
        return Math.abs(x) <= margin && Math.abs(y) <= margin;
    }

    private static int checkViewAnimations(File defaults, dev.plattnericus.cases.catalog.Catalog catalog, TextureStore textures) throws Exception {
        List<String> warnings = new ArrayList<>();
        InspectModels models = new InspectModels(new File(defaults, "inspect.yml"), warnings::add);
        require(warnings.isEmpty(), "inspect.yml warnings " + warnings);
        require(!models.viewAnimations().isEmpty(), "no held-pose inspects");
        for (WeaponType weapon : catalog.weapons()) {
            if (catalog.skins().stream().noneMatch(s -> s.weapon() == weapon) || !HeldRig.held(weapon)) continue;
            require(models.viewPools().containsKey(HeldRig.family(weapon)), "no held-pose pool for " + weapon.id());
            var assets = textures.weapon(weapon.id(), weapon.textureFolder());
            SpriteFrame frame = HeldRig.rigFrame(weapon, assets);
            HeldRig.Stance stance = HeldRig.stance(weapon, frame), bodyStance = HeldRig.bodyStance(weapon, frame);
            List<Vector3f> hull = ModelPose.hull(assets, frame);
            require(hull.size() > 20, "weapon hull " + weapon.id());
            for (String id : models.viewPools().get(HeldRig.family(weapon))) {
                InspectAnimation animation = models.viewAnimation(id);
                require(animation.duration() >= 30 && animation.duration() <= 120, "inspect length " + id);
                for (var variation : VARIATIONS) {
                    var rest = ViewInspect.sample(weapon, frame, stance, emptyAnimation(), 0, variation);
                    var bodyRest = ViewInspect.sampleBody(weapon, frame, bodyStance, emptyAnimation(), 0, variation);
                    for (int t : List.of(0, animation.duration())) {
                        var sample = ViewInspect.sample(weapon, frame, stance, animation, t, variation);
                        require(sample.hand().distance(rest.hand()) < 1e-3 && sample.weapon().equals(rest.weapon(), 1e-3f),
                                "inspect does not start and end in the hold " + id + " for " + weapon.id() + " " + variation);
                        for (var layer : sample.layers().entrySet()) require(layer.getValue().equals(rest.weapon(), 1e-3f),
                                "rig layer " + layer.getKey() + " does not start and end in place " + id + " for " + weapon.id());
                        require(ViewInspect.sampleBody(weapon, frame, bodyStance, animation, t, variation).weapon().equals(bodyRest.weapon(), 1e-3f),
                                "body-hand inspect does not start and end in the hold " + id + " for " + weapon.id() + " " + variation);
                    }
                    // Other players: the weapon in the model's fist never cuts into its head or torso.
                    for (ModelPose pose : POSES) for (int t = 0; t <= animation.duration(); t++) {
                        for (boolean offHand : HeldRig.dual(weapon) ? List.of(false, true) : List.of(false)) {
                            Matrix4f weaponMatrix = new Matrix4f().scale(offHand ? -1.1f : 1.1f, 1.1f, 1.1f)
                                    .mul(ViewInspect.sampleBody(weapon, frame, bodyStance, animation, t, variation).weapon());
                            float push = pose.clearance(hull, weaponMatrix, offHand);
                            // The plugin holds the weapon at most MAX_BODY_PUSH ahead, so every pose must clear within it.
                            // Looking straight ahead, a weapon still in the fist moves only a little.
                            boolean held = ViewInspect.held(animation, t), ahead = pose.equals(POSES.getFirst());
                            if (push > MAX_BODY_PUSH || held && ahead && push > MAX_AHEAD_PUSH) {
                                pushFailures.add(String.format(java.util.Locale.ROOT, "%.2f %s %s t%d %s", push, weapon.id(), id, t, pose));
                            }
                            if (held && ahead) maxBodyPush = Math.max(maxBodyPush, push);
                            Matrix4f pushed = new Matrix4f().translate(0, 0, -push).mul(weaponMatrix);
                            for (Vector3f point : hull) {
                                require(pose.intrusion(pose.toBody(pushed.transformPosition(new Vector3f(point)), offHand)) < 1e-3f,
                                        "body-hand weapon cuts into the model " + id + " for " + weapon.id());
                            }
                        }
                    }
                }
                for (int t = 0; t <= animation.duration(); t++) {
                    var sample = ViewInspect.sample(weapon, frame, stance, animation, t, new Vector3f());
                    require(sample.hand().isFinite() && sample.elbow().isFinite(), "non-finite inspect pose " + id);
                    require(onScreen(sample.hand(), 1.25f) || sample.hand().y < 0, "hand leaves the view upwards " + id);
                    float forearm = sample.hand().distance(sample.elbow());
                    require(forearm > .2f && forearm < 1.4f, "forearm stretched " + id + " " + forearm);
                }
            }
        }
        if (!pushFailures.isEmpty()) {
            pushFailures.sort(java.util.Comparator.reverseOrder());
            pushFailures.stream().limit(40).forEach(System.out::println);
            require(false, pushFailures.size() + " body-hand inspect poses hold the weapon too far from the fist");
        }
        return models.viewAnimations().size();
    }

    private static final List<ViewInspect.Variation> VARIATIONS = List.of(ViewInspect.Variation.NONE, new ViewInspect.Variation(1.12f, 1),
            new ViewInspect.Variation(1.12f, -1), new ViewInspect.Variation(.88f, 1), new ViewInspect.Variation(.88f, -1));
    /** Standing and crouching, looking up, ahead and down, head turned to either side. */
    private static final List<ModelPose> POSES = List.of(new ModelPose(0, 0, false, 1, false), new ModelPose(-30, 0, false, 1, false),
            new ModelPose(30, 0, false, 1, false), new ModelPose(10, 35, false, 1, false), new ModelPose(10, -35, false, 1, false),
            new ModelPose(0, 0, true, 1, false), new ModelPose(30, 0, true, 1, false));
    /** InspectService holds the body-hand weapon at most this far ahead; looking ahead, a held weapon less. */
    private static final float MAX_BODY_PUSH = .45f, MAX_AHEAD_PUSH = .12f;
    private static float maxBodyPush;
    private static final List<String> pushFailures = new ArrayList<>();

    private static InspectAnimation emptyAnimation() {
        return new InspectAnimation("rest", java.util.Map.of("weapon", new InspectAnimation.Group("weapon", null, new Vector3f(),
                List.of(new InspectAnimation.Keyframe(1, null, null, null, InspectAnimation.Ease.LINEAR)))), java.util.Map.of(), 1);
    }

    private static int checkPack(File pack, dev.plattnericus.cases.catalog.Catalog catalog, SkinRenderer renderer) throws Exception {
        int skins = 0;
        try (ZipFile zip = new ZipFile(pack)) {
            for (String shader : List.of("core/entity.vsh", "core/entity.fsh", "core/item.vsh", "core/item.fsh", "include/mccases_arm.glsl")) {
                require(zip.getEntry("assets/minecraft/shaders/" + shader) != null, "missing shader " + shader);
            }
            // The scene split is cut per fragment; the item stages must agree on the varyings.
            String vertex = text(zip, "assets/minecraft/shaders/core/item.vsh"), fragment = text(zip, "assets/minecraft/shaders/core/item.fsh");
            for (String varying : List.of("int mccasesScene;", "vec3 mccasesScenePosition;", "float metalSurface;")) {
                require(vertex.contains("out " + varying) && fragment.contains("in " + varying), "item shader varying " + varying);
            }
            require(fragment.contains("discard") && fragment.contains("metalSheen"), "item scene split and metal sheen");
            // Every reserved tint must be one the vertex shader recognises.
            for (int tint : List.of(0xFEFDFC, HeldRig.NEAR_TINT, HeldRig.FAR_TINT, 0xFCFDFE, 0xFDFCFE)) {
                String mark = "vec3(" + (tint >> 16 & 255) + ".0, " + (tint >> 8 & 255) + ".0, " + (tint & 255) + ".0)";
                require(vertex.contains(mark), "item shader misses tint " + Integer.toHexString(tint));
            }
            require(text(zip, "assets/minecraft/shaders/core/entity.fsh").contains("#include <minecraft:mccases_arm.glsl>"), "entity shader include");
            for (String arm : List.of("right_wide", "right_slim", "left_wide", "left_slim")) {
                require(json(zip, "assets/mccases/items/arm/" + arm + ".json").toString().contains("minecraft:player_head"), "arm " + arm);
            }
            Pattern models = Pattern.compile("\"model\":\"mccases:item/([^\"]+)\"");
            for (SkinDefinition skin : catalog.skins()) {
                var sprite = ImageIO.read(zip.getInputStream(zip.getEntry("assets/mccases/textures/item/skin/" + skin.id() + ".png")));
                require(sprite.getWidth() == SpriteFrame.SIZE && sprite.getHeight() == SpriteFrame.SIZE, "sprite size " + skin.id());
                int opaque = 0;
                for (int y = 0; y < sprite.getHeight(); y++) for (int x = 0; x < sprite.getWidth(); x++) {
                    int pixel = sprite.getRGB(x, y), alpha = pixel >>> 24;
                    require(alpha == 0 || alpha == 255, "translucent sprite edge " + skin.id());
                    if (alpha == 255) opaque++;
                }
                require(opaque > 200, "empty sprite " + skin.id());
                List<String> layers = InspectRig.layers(skin.weapon()).stream().map(InspectRig.Layer::id).distinct().toList();
                for (String layer : layers) {
                    String item = text(zip, "assets/mccases/items/inspect/" + skin.id() + "/" + layer + ".json");
                    require(item.contains(HeldRig.NEAR_SCENE) && item.contains(HeldRig.FAR_SCENE) && item.contains(String.valueOf(HeldRig.NEAR_TINT))
                            && item.contains(String.valueOf(HeldRig.FAR_TINT)), "rig layer scenes " + skin.id() + "/" + layer);
                    String model = text(zip, "assets/mccases/models/item/inspect/" + skin.id() + "/" + layer + ".json");
                    require(!model.contains("\"texture\":\"#skin\"}"), "every rig face carries a tint for the scene marks " + skin.id());
                    if (HeldRig.held(skin.weapon())) require(model.contains("\"firstperson_righthand\":{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[1,1,1]}"),
                            "rig hand display must be neutral " + skin.id());
                    var texture = ImageIO.read(zip.getInputStream(zip.getEntry("assets/mccases/textures/item/inspect/" + skin.id() + "/" + layer + ".png")));
                    for (int y = 0; y < texture.getHeight(); y++) for (int x = 0; x < texture.getWidth(); x++) {
                        int alpha = texture.getRGB(x, y) >>> 24;
                        require(alpha == 0 || alpha == 255, "translucent rig texel " + skin.id() + "/" + layer);
                    }
                }
                if (!HeldRig.held(skin.weapon())) { skins++; continue; }
                String definition = text(zip, "assets/mccases/items/skin/" + skin.id() + ".json");
                JsonParser.parseString(definition);
                for (String context : List.of("fixed", "firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand")) {
                    require(definition.contains("\"when\":\"" + context + "\""), "missing context " + context + " " + skin.id());
                }
                require(definition.contains(HeldRig.INSPECT_STATE) && definition.contains(HeldRig.SLIM_STATE), "hand states " + skin.id());
                require(definition.contains("minecraft:player_head"), "skin arms " + skin.id());
                require(definition.contains("\"hand_animation_on_swap\":false,\"swap_animation_scale\":0}"), "inspect swap would dip the hand " + skin.id());
                for (String layer : layers) require(definition.contains("item/inspect/" + skin.id() + "/" + layer + "\""), "rig layer in hand " + skin.id() + "/" + layer);
                Matcher matcher = models.matcher(definition);
                while (matcher.find()) require(zip.getEntry("assets/mccases/models/item/" + matcher.group(1) + ".json") != null,
                        "missing model " + matcher.group(1));
                skins++;
            }
        }
        require(PackExporter.class != null && renderer != null, "renderer");
        return skins;
    }

    private static String text(ZipFile zip, String name) throws Exception {
        var entry = zip.getEntry(name);
        require(entry != null, "missing pack entry " + name);
        try (var in = zip.getInputStream(entry)) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }

    private static JsonObject json(ZipFile zip, String name) throws Exception {
        return JsonParser.parseString(text(zip, name)).getAsJsonObject();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
