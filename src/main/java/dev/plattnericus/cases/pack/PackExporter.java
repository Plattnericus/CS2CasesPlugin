package dev.plattnericus.cases.pack;

import dev.plattnericus.cases.catalog.CaseDefinition;
import dev.plattnericus.cases.catalog.Catalog;
import dev.plattnericus.cases.catalog.KeyDefinition;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.render.ArgbImage;
import dev.plattnericus.cases.render.SkinRenderer;
import dev.plattnericus.cases.render.TextureException;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import dev.plattnericus.cases.inspect.HeldRig;
import dev.plattnericus.cases.inspect.InspectRig;
import dev.plattnericus.cases.render.SpriteFrame;

/**
 * Builds generated skin sprites, inspect meshes, the first-person view model held by the player's own
 * skin arms, and the Vanilla 26.3 entity and item shaders (metal shading, skin arms, inspect scenes).
 */
public final class PackExporter {

    public static final int MIN_FORMAT = 97;
    public static final int MAX_FORMAT = 99;
    private static final int SPRITE = 64;
    private static final int SKIN_SPRITE = SpriteFrame.SIZE;
    private static final int RIG_CONTENT = SpriteFrame.RIG_CONTENT;
    private static final int RIG_INSET = (SKIN_SPRITE - RIG_CONTENT) / 2;
    private static final double TEXELS_PER_UNIT = SKIN_SPRITE / 16.0;
    /** Vanilla 26.3 shader programs with the MCCases additions, written over the vanilla ones. */
    private static final List<String> SHADERS = List.of("core/entity.vsh", "core/entity.fsh", "core/item.vsh", "core/item.fsh",
            "include/mccases_arm.glsl");
    /**
     * Reserved tints the item shader recognises by their colour ratios: metal faces get the metal
     * sheen; the near and far variants also cut an inspect scene to the first- or third-person view.
     */
    static final int METAL_TINT = 0xFEFDFC, PLAIN_TINT = 0xFFFFFF;
    static final int NEAR_METAL_TINT = 0xFCFDFE, FAR_METAL_TINT = 0xFDFCFE;

    public record Result(int skins, int cases, int keys, List<String> failures) {
    }

    private PackExporter() {
    }

    public static Result export(Catalog catalog, SkinRenderer renderer, String namespace, String author, File target) throws IOException {
        List<String> failures = new ArrayList<>();
        int skins = 0;
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(target))) {
            text(zip, "pack.mcmeta", """
                    {
                      "pack": {
                        "description": "MCCases skins by %s",
                        "min_format": [%d, 1],
                        "max_format": %d
                      }
                    }
                    """.formatted(author, MIN_FORMAT, MAX_FORMAT));
            for (String shader : SHADERS) {
                String source = "/pack-shaders/" + shader.substring(shader.indexOf('/') + 1);
                try (var input = PackExporter.class.getResourceAsStream(source)) {
                    if (input == null) throw new IOException("Missing Vanilla 26.3 shader: " + source);
                    text(zip, "assets/minecraft/shaders/" + shader, new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            armModels(zip, namespace);
            text(zip, "README.txt", """
                    MCCases resource pack (optional)
                    -----------------------------------
                    Models and textures live in assets/%1$s/.
                    Skin textures are rendered from the active MCCases catalog and bundled weapon maps.
                    In first person and during inspects the holder's own Minecraft skin arms hold the
                    weapon; the arms are drawn from each player's own skin by their own client.
                    Case artwork source and hashes are included in case-art/SOURCE.md.
                    assets/minecraft/shaders/ holds the Vanilla 26.3 entity and item programs with
                    MCCases additions: metal shading for marked faces, the skin arms and the split
                    between the first- and third-person inspect scenes. They require Minecraft 26.3.
                    Merge both the namespace and these shader programs into another pack.

                    Enable it in plugins/MCCases/config.yml -> resource-pack.enabled: true
                    only when every player has this pack loaded; otherwise the items show
                    the missing-model texture.
                    """.formatted(namespace));
            try (var notice = PackExporter.class.getResourceAsStream("/defaults/LEGAL-NOTICE.txt")) {
                if (notice == null) throw new IOException("Missing bundled legal notice");
                text(zip, "LEGAL-NOTICE.txt", new String(notice.readAllBytes(), StandardCharsets.UTF_8));
            }
            try (var source = PackExporter.class.getResourceAsStream("/case-art/SOURCE.md")) {
                if (source == null) throw new IOException("Missing case artwork source record");
                text(zip, "case-art/SOURCE.md", new String(source.readAllBytes(), StandardCharsets.UTF_8));
            }
            var heldSkins = catalog.skins();
            BufferedImage logo = null;
            for (SkinDefinition skin : catalog.skins().stream().sorted(Comparator.comparing(SkinDefinition::id)).toList()) {
                BufferedImage sprite;
                try {
                    sprite = sprite(renderer, skin);
                } catch (TextureException e) {
                    failures.add(skin.id() + ": " + e.getMessage());
                    continue;
                }
                if (logo == null && skin.isKnife()) {
                    logo = sprite;
                }
                String path = "skin/" + skin.id();
                png(zip, "assets/" + namespace + "/textures/item/" + path + ".png", sprite);
                model(zip, namespace, path, "minecraft:item/generated", FIXED_DISPLAY);
                String selected = "trade/selected/" + skin.id();
                png(zip, "assets/" + namespace + "/textures/item/" + selected + ".png", selectedSprite(sprite));
                model(zip, namespace, selected, "minecraft:item/generated", FIXED_DISPLAY);
                SpriteFrame frame;
                try {
                    rig(zip, namespace, skin, renderer);
                    frame = HeldRig.rigFrame(skin.weapon(), renderer.textures().weapon(skin.weapon().id(), skin.weapon().textureFolder()));
                }
                catch (TextureException e) { throw new IOException("inspect rig " + skin.id(), e); }
                if (HeldRig.held(skin.weapon())) text(zip, "assets/" + namespace + "/items/skin/" + skin.id() + ".json", skinDefinition(namespace, skin, frame));
                else heldItem(zip, namespace, skin);
                skins++;
            }
            for (CaseDefinition c : catalog.cases().stream().sorted(Comparator.comparing(CaseDefinition::id)).toList()) {
                String path = "case/" + c.id();
                pngBytes(zip, "assets/" + namespace + "/textures/item/" + path + ".png", caseTexture(c.id()));
                model(zip, namespace, path, "minecraft:item/generated");
            }
            for (KeyDefinition k : catalog.keys().stream().sorted(Comparator.comparing(KeyDefinition::id)).toList()) {
                String path = "key/" + k.id();
                png(zip, "assets/" + namespace + "/textures/item/" + path + ".png", hardEdges(keySprite(k.color())));
                model(zip, namespace, path, "minecraft:item/generated");
            }
            if (logo != null) {
                png(zip, "pack.png", logo);
            }
            return new Result(skins, catalog.cases().size(), catalog.keys().size(), failures);
        }
    }

    private static final String FIXED_DISPLAY = "\"display\":{\"fixed\":{\"rotation\":[0,0,0],\"scale\":[1,1,1]}},\n";

    private static void model(ZipOutputStream zip, String ns, String path, String parent) throws IOException {
        model(zip, ns, path, parent, null);
    }

    /** Textured cuboids give the inspect asset thickness from every angle, with separate joints. */
    private static void rig(ZipOutputStream zip, String ns, SkinDefinition skin, SkinRenderer renderer) throws IOException, TextureException {
        BufferedImage canvas = null;
        for (String layer : dev.plattnericus.cases.inspect.InspectRig.layers(skin.weapon()).stream().map(dev.plattnericus.cases.inspect.InspectRig.Layer::id).distinct().toList()) {
            BufferedImage texture = new BufferedImage(SKIN_SPRITE, SKIN_SPRITE, BufferedImage.TYPE_INT_ARGB);
            if (canvas == null) {
                double fl = Math.max(skin.minFloat(), Math.min(skin.maxFloat(), .02));
                ArgbImage source = InspectRig.presentation(skin.weapon(), renderer.render(skin, 0, fl, 0).image());
                canvas = new BufferedImage(SKIN_SPRITE, SKIN_SPRITE, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = canvas.createGraphics();
                graphics.drawImage(source.scaledTo(RIG_CONTENT, RIG_CONTENT).toBufferedImage(), RIG_INSET, RIG_INSET, null);
                graphics.dispose();
            }
            boolean[][] mask = new boolean[SKIN_SPRITE][SKIN_SPRITE];
            for (int y = 0; y < SKIN_SPRITE; y++) for (int x = 0; x < SKIN_SPRITE; x++) {
                int pixel = canvas.getRGB(x, y);
                double originalX = (x + .5 - RIG_INSET) / RIG_CONTENT, originalY = (y + .5 - RIG_INSET) / RIG_CONTENT;
                if (dev.plattnericus.cases.inspect.InspectRig.reverseGrip(skin.weapon())) { originalX = 1 - originalX; originalY = 1 - originalY; }
                if ((pixel >>> 24) >= 128 && layer.equals(dev.plattnericus.cases.inspect.InspectRig.layerAt(skin.weapon(), originalX, originalY))) {
                    // Geometry covers this texel completely. Partial alpha made the solid rim
                    // see-through and caused dark seams during spins.
                    texture.setRGB(x, y, pixel | 0xFF000000); mask[y][x] = true;
                }
            }
            String path = "inspect/" + skin.id() + "/" + layer;
            png(zip, "assets/" + ns + "/textures/item/" + path + ".png", texture);
            List<String> elements = new ArrayList<>();
            double thickness = skin.isKnife() ? (layer.startsWith("handle") || layer.equals("body") ? 1.2 : .45) : 2.6;
            for (int y = 0; y < SKIN_SPRITE; y++) for (int x = 0; x < SKIN_SPRITE; x++) {
                if (!mask[y][x]) continue;
                int right = x + 1; while (right < SKIN_SPRITE && mask[y][right]) right++;
                int bottom = y + 1;
                outer: while (bottom < SKIN_SPRITE) { for (int xx = x; xx < right; xx++) if (!mask[bottom][xx]) break outer; bottom++; }
                for (int yy = y; yy < bottom; yy++) for (int xx = x; xx < right; xx++) mask[yy][xx] = false;
                double x1 = x / TEXELS_PER_UNIT, x2 = right / TEXELS_PER_UNIT, y1 = y / TEXELS_PER_UNIT, y2 = bottom / TEXELS_PER_UNIT;
                StringBuilder faces = new StringBuilder();
                for (String face : List.of("north", "south", "east", "west", "up", "down")) {
                    if (!faces.isEmpty()) faces.append(',');
                    // Side faces sample just the corresponding boundary texels, rather than
                    // stretching the whole weapon face across its thickness. Half-texel insets
                    // keep every sample inside an opaque pixel, including one-pixel edges.
                    double u1 = (x + .5) / TEXELS_PER_UNIT, u2 = (right - .5) / TEXELS_PER_UNIT;
                    double v1 = (y + .5) / TEXELS_PER_UNIT, v2 = (bottom - .5) / TEXELS_PER_UNIT;
                    if (face.equals("north")) { double swap = u1; u1 = u2; u2 = swap; }
                    if (face.equals("east")) u1 = u2;
                    if (face.equals("west")) u2 = u1;
                    if (face.equals("up")) v2 = v1;
                    if (face.equals("down")) v1 = v2;
                    double centerX = (x + right) / 2.0 / SKIN_SPRITE;
                    if (InspectRig.reverseGrip(skin.weapon())) centerX = 1 - centerX;
                    var blade = skin.weapon().region("blade");
                    boolean metal = skin.weapon().category() != dev.plattnericus.cases.catalog.WeaponCategory.GLOVE
                            && (!skin.isKnife() || centerX >= (blade == null ? .46 : blade.x1()));
                    // Plain faces carry tint 1 so the inspect scene marks reach every face of the rig.
                    faces.append("\"").append(face).append("\":{\"uv\":[").append(u1).append(',').append(v1).append(',').append(u2).append(',').append(v2).append("],\"texture\":\"#skin\"")
                            .append(metal ? ",\"tintindex\":0" : ",\"tintindex\":1").append('}');
                }
                elements.add("{\"from\":[" + x1 + ',' + (16-y2) + ',' + (8-thickness/2) + "],\"to\":[" + x2 + ',' + (16-y1) + ',' + (8+thickness/2) + "],\"faces\":{" + faces + "}}");
            }
            text(zip, "assets/" + ns + "/items/" + path + ".json", "{\"model\":" + layerModel(ns, path) + "}");
            // Held weapons get their hand placement from the item definition (see skinDefinition).
            String display = HeldRig.held(skin.weapon()) ? NEUTRAL_DISPLAY : heldDisplay(skin);
            text(zip, "assets/" + ns + "/models/item/" + path + ".json", "{\"ambientocclusion\":false," + display + "\"textures\":{\"skin\":\"" + ns + ":item/" + path + "\",\"particle\":\"#skin\"},\"elements\":[" + String.join(",", elements) + "]}");
        }
    }

    /** Select the skin model by view so Minecraft renders the skin item in the native player hand. */
    private static void heldItem(ZipOutputStream zip, String ns, SkinDefinition skin) throws IOException {
        var parts = InspectRig.layers(skin.weapon()).stream().map(InspectRig.Layer::id).distinct()
                .map(layer -> metalModel(ns, "inspect/" + skin.id() + "/" + layer)).toList();
        String weapon = "{\"type\":\"minecraft:composite\",\"models\":[" + String.join(",", parts) + "]}";
        String selection = "\"cases\":["
                + "{\"when\":[\"fixed\"],\"model\":" + weapon + "},"
                + "{\"when\":[\"firstperson_righthand\",\"firstperson_lefthand\"],\"model\":" + weapon + "},"
                + "{\"when\":[\"thirdperson_righthand\",\"thirdperson_lefthand\"],\"model\":" + weapon + "}],";
        text(zip, "assets/" + ns + "/items/skin/" + skin.id() + ".json", "{\"model\":{\"type\":\"minecraft:select\",\"property\":\"minecraft:display_context\","
                + selection
                + "\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"" + ns + ":item/skin/" + skin.id() + "\"}}}");
    }

    private static String metalModel(String ns, String path) {
        return tinted(ns, path, METAL_TINT, PLAIN_TINT);
    }

    private static String tinted(String ns, String path, int metal, int plain) {
        return "{\"type\":\"minecraft:model\",\"model\":\"" + ns + ":item/" + path + "\",\"tints\":[{\"type\":\"minecraft:constant\",\"value\":"
                + metal + "},{\"type\":\"minecraft:constant\",\"value\":" + plain + "}]}";
    }

    /**
     * One rig layer as an item: inspect scenes mark it for the item shader's near-only or away-only
     * scene through custom model data; everywhere else it is the plain layer.
     */
    private static String layerModel(String ns, String path) {
        return "{\"type\":\"minecraft:select\",\"property\":\"minecraft:custom_model_data\",\"index\":0,\"cases\":["
                + "{\"when\":\"" + HeldRig.NEAR_SCENE + "\",\"model\":" + tinted(ns, path, NEAR_METAL_TINT, HeldRig.NEAR_TINT) + "},"
                + "{\"when\":\"" + HeldRig.FAR_SCENE + "\",\"model\":" + tinted(ns, path, FAR_METAL_TINT, HeldRig.FAR_TINT) + "}],"
                + "\"fallback\":" + metalModel(ns, path) + "}";
    }

    /** All layers of a skin's rig, plain. */
    private static String rigModel(String ns, SkinDefinition skin) {
        var parts = InspectRig.layers(skin.weapon()).stream().map(InspectRig.Layer::id).distinct()
                .map(layer -> metalModel(ns, "inspect/" + skin.id() + "/" + layer)).toList();
        return parts.size() == 1 ? parts.getFirst() : "{\"type\":\"minecraft:composite\",\"models\":[" + String.join(",", parts) + "]}";
    }

    private static String transformed(String model, Matrix4f transformation) {
        return "{\"type\":\"minecraft:composite\",\"transformation\":" + matrix(transformation) + ",\"models\":[" + model + "]}";
    }

    private static final String NEUTRAL_DISPLAY = "\"display\":{\"fixed\":{\"rotation\":[0,0,0],\"scale\":[1,1,1]},"
            + "\"firstperson_righthand\":{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[1,1,1]},"
            + "\"firstperson_lefthand\":{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[1,1,1]},"
            + "\"thirdperson_righthand\":{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[1,1,1]},"
            + "\"thirdperson_lefthand\":{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[1,1,1]}},";

    /**
     * Item definition of a held skin. Menus and the ground show the sprite, item frames and displays
     * the 3D rig; third person holds the grip in the palm with the blade or muzzle forward; first person
     * shows the view model held by the holder's own skin arms. The custom model data string selects
     * their width, and the inspect state hides the hand model while a client-side scene plays, so the
     * item keeps its hotbar icon.
     */
    static String skinDefinition(String ns, SkinDefinition skin, SpriteFrame frame) {
        String rig = rigModel(ns, skin);
        String third = "{\"type\":\"minecraft:select\",\"property\":\"minecraft:custom_model_data\",\"index\":0,\"cases\":["
                + "{\"when\":\"" + HeldRig.INSPECT_STATE + "\",\"model\":{\"type\":\"minecraft:empty\"}}],"
                + "\"fallback\":" + transformed(rig, HeldRig.thirdPerson(skin.weapon(), frame)) + "}";
        String first = "{\"type\":\"minecraft:select\",\"property\":\"minecraft:custom_model_data\",\"index\":0,\"cases\":["
                + "{\"when\":\"" + HeldRig.INSPECT_STATE + "\",\"model\":{\"type\":\"minecraft:empty\"}},"
                + "{\"when\":\"" + HeldRig.SLIM_STATE + "\",\"model\":" + useStates(ns, skin, frame, rig, true) + "}],"
                + "\"fallback\":" + useStates(ns, skin, frame, rig, false) + "}";
        return "{\"model\":{\"type\":\"minecraft:select\",\"property\":\"minecraft:display_context\",\"cases\":["
                + "{\"when\":\"fixed\",\"model\":" + rig + "},"
                + "{\"when\":\"firstperson_righthand\",\"model\":" + first + "},"
                + "{\"when\":\"firstperson_lefthand\",\"model\":{\"type\":\"minecraft:composite\",\"transformation\":" + MIRROR
                + ",\"models\":[" + first + "]}},"
                + "{\"when\":\"thirdperson_righthand\",\"model\":" + third + "},"
                + "{\"when\":\"thirdperson_lefthand\",\"model\":{\"type\":\"minecraft:composite\",\"transformation\":" + MIRROR
                + ",\"models\":[" + third + "]}}],"
                + "\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"" + ns + ":item/skin/" + skin.id() + "\"}},"
                // Inspects swap the held item for its client-side copy (a loaded crossbow, for the crossbow
                // hold) and back; neither the swap nor the item type change may dip the hand.
                + "\"hand_animation_on_swap\":false,\"swap_animation_scale\":0}";
    }

    /** Mirrors item-local space for the left hand: arms swap sides and become the other arm. */
    private static final String MIRROR = "[-1,0,0,1,0,1,0,0,0,0,1,0,0,0,0,1]";

    /** Knives hold one pose; bows aim while drawn; crossbows reload while charging and stay ready when loaded. */
    private static String useStates(String ns, SkinDefinition skin, SpriteFrame frame, String rig, boolean slim) {
        if (skin.isKnife()) return view(ns, skin, frame, rig, HeldRig.Pose.IDLE, slim);
        String bow = "{\"type\":\"minecraft:condition\",\"property\":\"minecraft:using_item\",\"on_true\":"
                + view(ns, skin, frame, rig, HeldRig.Pose.BOW_DRAWN, slim) + ",\"on_false\":" + view(ns, skin, frame, rig, HeldRig.Pose.IDLE, slim) + "}";
        String crossbow = "{\"type\":\"minecraft:condition\",\"property\":\"minecraft:using_item\",\"on_true\":"
                + view(ns, skin, frame, rig, HeldRig.Pose.CROSSBOW_CHARGING, slim) + ",\"on_false\":{\"type\":\"minecraft:select\",\"property\":\"minecraft:charge_type\","
                + "\"cases\":[{\"when\":\"none\",\"model\":" + view(ns, skin, frame, rig, HeldRig.Pose.IDLE, slim) + "}],\"fallback\":"
                + view(ns, skin, frame, rig, HeldRig.Pose.CROSSBOW_LOADED, slim) + "}}";
        // Only crossbows carry the charged projectiles component, so one definition serves both slots.
        return "{\"type\":\"minecraft:condition\",\"property\":\"minecraft:has_component\",\"component\":\"minecraft:charged_projectiles\","
                + "\"on_true\":" + crossbow + ",\"on_false\":" + bow + "}";
    }

    private static String view(String ns, SkinDefinition skin, SpriteFrame frame, String rig, HeldRig.Pose pose, boolean slim) {
        List<String> models = new ArrayList<>();
        for (HeldRig.Hand hand : HeldRig.firstPerson(skin.weapon(), frame, pose, slim)) {
            models.add(transformed(rig, hand.weapon()));
            for (HeldRig.Arm arm : hand.arms()) models.add(armModel(ns, arm.matrix()));
        }
        return "{\"type\":\"minecraft:composite\",\"models\":[" + String.join(",", models) + "]}";
    }

    private static String armModel(String ns, Matrix4f transformation) {
        return "{\"type\":\"minecraft:special\",\"base\":\"" + ns + ":item/arm_base\",\"model\":{\"type\":\"minecraft:player_head\"},\"transformation\":"
                + matrix(transformation) + "}";
    }

    /**
     * Skin arms for the inspect scenes: the box of a player head, wearing the holder's own skin, in the
     * proportions the entity shader recognises as an arm. The hand end sits at the item origin.
     */
    private static void armModels(ZipOutputStream zip, String ns) throws IOException {
        text(zip, "assets/" + ns + "/models/item/arm_base.json", "{\"textures\":{\"particle\":\"minecraft:entity/player/wide/steve\"}}");
        for (boolean left : List.of(false, true)) for (boolean slim : List.of(false, true)) {
            var signature = slim ? HeldRig.SLIM : HeldRig.WIDE;
            var matrix = new Matrix4f().translation(.5f, .5f, .5f).scale((left ? -1 : 1) * signature.x(), signature.y(), signature.z());
            text(zip, "assets/" + ns + "/items/arm/" + (left ? "left" : "right") + "_" + (slim ? "slim" : "wide") + ".json",
                    "{\"model\":" + armModel(ns, matrix) + "}");
        }
    }

    /** Row-major 4x4 transformation accepted by item definitions. */
    static String matrix(Matrix4f m) {
        StringBuilder out = new StringBuilder("[");
        for (int row = 0; row < 4; row++) for (int column = 0; column < 4; column++) {
            if (out.length() > 1) out.append(',');
            float value = m.getRowColumn(row, column);
            out.append(Math.abs(value) < 5e-7f ? "0" : String.format(java.util.Locale.ROOT, "%.6f", value).replaceFirst("\\.?0+$", ""));
        }
        return out.append(']').toString();
    }

    /**
     * Minecraft extrudes every texel with any coverage into a solid edge. Fractional alpha left those
     * edges see-through, so edges looked missing from some angles. Coverage below one half becomes
     * empty (without a colour fringe); the rest is opaque.
     */
    static BufferedImage hardEdges(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                image.setRGB(x, y, (pixel >>> 24) >= 128 ? pixel | 0xFF000000 : 0);
            }
        }
        return image;
    }

    private static String heldDisplay(SkinDefinition skin) {
        // ItemInHandLayer applies X=-90,Y=180 before these rotations. Compensating for that
        // keeps the weapon's grip aligned with the player's palm and its blade broadside.
        boolean knife = skin.isKnife(), glove = skin.weapon().category() == dev.plattnericus.cases.catalog.WeaponCategory.GLOVE;
        boolean reverse = InspectRig.reverseGrip(skin.weapon());
        double gripX = knife ? (reverse ? .72 : .29) : .36;
        double gripY = knife ? .50 : .57;
        // Keep the full silhouette readable while leaving the player's real arm to Minecraft.
        float firstScale = glove ? .80f : knife ? .62f : .58f;
        float thirdScale = glove ? .85f : knife ? 1.05f : 1.0f;
        StringBuilder display = new StringBuilder("\"display\":{\"fixed\":{\"rotation\":[0,0,0],\"scale\":[1,1,1]}");
        for (boolean first : List.of(true, false)) for (boolean left : List.of(false, true)) {
            float firstYaw = knife ? 15 : skin.weapon().category() == dev.plattnericus.cases.catalog.WeaponCategory.GLOVE ? -20 : 40;
            Vector3f angles = first ? new Vector3f(-5, firstYaw, 20) : new Vector3f(90, knife ? 150 : 145, 0);
            float scale = first ? firstScale : thirdScale;
            Vector3f grip = new Vector3f((float)(16 * (gripX - .5)), (float)(16 * (.5 - gripY)), 0).mul(scale)
                    .rotate(new Quaternionf().rotationXYZ((float)Math.toRadians(angles.x), (float)Math.toRadians(angles.y), (float)Math.toRadians(angles.z)));
            Vector3f translation = grip.negate();
            if (first) translation.add(-1.5f, 3.2f, -3.5f);
            // Native Y/Z and translation mirroring plus a mirrored mesh produce a true left-hand grip.
            display.append(",\"").append(first ? "firstperson" : "thirdperson").append(left ? "_lefthand" : "_righthand")
                    .append("\":{\"rotation\":[").append(angles.x).append(',').append(angles.y).append(',').append(angles.z)
                    .append("],\"translation\":[").append(translation.x).append(',').append(translation.y).append(',').append(translation.z)
                    .append("],\"scale\":[").append(left ? -scale : scale).append(',').append(scale).append(',').append(scale).append("]}");
        }
        return display.append("},").toString();
    }

    private static void model(ZipOutputStream zip, String ns, String path, String parent, String display) throws IOException {
        if (!path.startsWith("skin/")) text(zip, "assets/" + ns + "/items/" + path + ".json",
                "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \"" + ns + ":item/" + path + "\"\n  }\n}\n");
        text(zip, "assets/" + ns + "/models/item/" + path + ".json",
                "{\n  \"parent\": \"" + parent + "\",\n" + (display == null ? "" : display)
                        + "  \"textures\": {\n    \"layer0\": \"" + ns + ":item/" + path + "\"\n  }\n}\n");
    }

    /** Skin sprite: showcase render, cropped, knives turned to the vanilla sword diagonal. */
    public static BufferedImage sprite(SkinRenderer renderer, SkinDefinition skin) throws TextureException {
        double fl = Math.max(skin.minFloat(), Math.min(skin.maxFloat(), 0.02));
        ArgbImage img = dev.plattnericus.cases.inspect.InspectRig.presentation(skin.weapon(), renderer.render(skin, 0, fl, 0).image());
        BufferedImage src = img.toBufferedImage();
        if (skin.isKnife()) {
            int side = (int) Math.ceil(Math.hypot(src.getWidth(), src.getHeight()));
            BufferedImage rotated = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = rotated.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.translate(side / 2.0, side / 2.0);
            g.rotate(Math.toRadians(-45));
            g.translate(-src.getWidth() / 2.0, -src.getHeight() / 2.0);
            g.drawImage(src, 0, 0, null);
            g.dispose();
            src = rotated;
        }
        ArgbImage cropped = crop(ArgbImage.from(src));
        int w = cropped.width();
        int h = cropped.height();
        double scale = (SKIN_SPRITE - 4.0) / Math.max(w, h);
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));
        ArgbImage fitted = cropped.scaledTo(tw, th);
        // Minecraft extrudes every nonzero texel. Binary coverage avoids translucent edge spikes.
        int[] pixels = fitted.pixels();
        for (int i = 0; i < pixels.length; i++) pixels[i] = (pixels[i] >>> 24) >= 128 ? pixels[i] | 0xff000000 : 0;
        BufferedImage out = new BufferedImage(SKIN_SPRITE, SKIN_SPRITE, BufferedImage.TYPE_INT_ARGB);
        out.setRGB((SKIN_SPRITE - tw) / 2, (SKIN_SPRITE - th) / 2, tw, th, pixels, 0, tw);
        return out;
    }

    private static ArgbImage crop(ArgbImage img) {
        int minX = img.width(), minY = img.height(), maxX = -1, maxY = -1;
        for (int y = 0; y < img.height(); y++) {
            for (int x = 0; x < img.width(); x++) {
                if ((img.get(x, y) >>> 24) > 10) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return img;
        }
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            System.arraycopy(img.pixels(), (minY + y) * img.width() + minX, px, y * w, w);
        }
        return new ArgbImage(w, h, px);
    }

    /** GUI-only selection tile; the normal equipment sprite stays transparent. */
    static BufferedImage selectedSprite(BufferedImage sprite) {
        BufferedImage out = new BufferedImage(sprite.getWidth(), sprite.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.scale(sprite.getWidth() / (double) SPRITE, sprite.getHeight() / (double) SPRITE);
        g.setColor(new Color(0x286b2b));
        g.fillRect(0, 0, SPRITE, SPRITE);
        g.setColor(new Color(0x72c64a));
        g.fillRect(0, 0, SPRITE, 3);
        g.fillRect(0, 0, 3, SPRITE);
        g.setColor(new Color(0x184b1b));
        g.fillRect(0, SPRITE - 3, SPRITE, 3);
        g.fillRect(SPRITE - 3, 0, 3, SPRITE);
        g.drawImage(sprite, 0, 0, SPRITE, SPRITE, null);
        g.dispose();
        return out;
    }

    private static byte[] caseTexture(String id) throws IOException {
        String resource = "/case-art/" + id + ".png";
        try (var input = PackExporter.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing bundled case artwork: " + resource);
            byte[] data = input.readAllBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null || image.getWidth() != 128 || image.getHeight() != 128) {
                throw new IOException("Invalid bundled case artwork: " + resource);
            }
            return data;
        }
    }

    static BufferedImage keySprite(int rgb) {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color base = new Color(rgb);
        g.rotate(Math.toRadians(-45), 16, 16);
        g.setColor(base.darker());
        g.setStroke(new BasicStroke(3.2f));
        g.draw(new Ellipse2D.Double(3, 11, 10, 10));
        g.fillRect(12, 14, 17, 4);
        g.fillRect(23, 18, 3, 5);
        g.fillRect(27, 18, 2, 4);
        g.setColor(base);
        g.setStroke(new BasicStroke(1.6f));
        g.draw(new Ellipse2D.Double(3, 11, 10, 10));
        g.fillRect(13, 15, 15, 2);
        g.dispose();
        return img;
    }

    private static void text(ZipOutputStream zip, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name); entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void png(ZipOutputStream zip, String name, BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        ZipEntry entry = new ZipEntry(name); entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(bytes.toByteArray());
        zip.closeEntry();
    }

    private static void pngBytes(ZipOutputStream zip, String name, byte[] image) throws IOException {
        ZipEntry entry = new ZipEntry(name); entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(image);
        zip.closeEntry();
    }
}
