package dev.plattnericus.cases.tools;

import com.google.gson.*;
import dev.plattnericus.cases.catalog.*;
import dev.plattnericus.cases.inspect.InspectRig;
import dev.plattnericus.cases.render.TextureStore;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** Inspect the actual exported assets, including every side-face UV and both hand transforms. */
public final class PackChecks {
    private PackChecks() { }
    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        var catalog = new CatalogLoader(new File(root, "catalog"), new TextureStore(new File(root, "textures"))).load(1).catalog();
        int faces = 0, layers = 0;
        Map<String, BufferedImage> originals = new HashMap<>();
        try (var input = PackChecks.class.getResourceAsStream("/original-skins.zip"); var source = new ZipInputStream(Objects.requireNonNull(input))) {
            java.util.zip.ZipEntry entry;
            while ((entry = source.getNextEntry()) != null) {
                if (!entry.isDirectory()) originals.put(entry.getName(), ImageIO.read(new java.io.ByteArrayInputStream(source.readAllBytes())));
            }
        }
        require(originals.size() == 2192, "incomplete original source artwork");
        var examples = new TreeMap<String, SkinDefinition>();
        int rows = (catalog.weapons().size() + 7) / 8;
        var sheet = new BufferedImage(8 * 180, rows * 150, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics(); graphics.setColor(new Color(0x20242b)); graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        try (var zip = new ZipFile(args[1])) {
            for (var skin : catalog.skins()) {
                String base = "assets/mccases/";
                var sprite = png(zip, base + "textures/item/skin/" + skin.id() + ".png");
                var originalSprite = originals.get(base + "textures/item/skin/" + skin.id() + ".png");
                require(originalSprite != null, "missing stock source sprite " + skin.id());
                int size = sprite.getWidth();
                require(size >= 64 && size == sprite.getHeight() && (size & (size - 1)) == 0, "sprite dimensions " + skin.id());
                require(size == 128, "skin edge resolution " + skin.id());
                for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                    int pixel = sprite.getRGB(x, y), alpha = pixel >>> 24;
                    require(alpha == 0 || alpha == 255, "translucent extruded edge " + skin.id());
                    require(alpha != 0 || (pixel & 0xffffff) == 0, "hidden color fringe " + skin.id());
                    int supplied = originalSprite.getRGB(x,y);
                    int expected = x > 0 && y > 0 && x < size-1 && y < size-1 && (supplied >>> 24) >= 128 ? supplied | 0xff000000 : 0;
                    require(pixel == expected, "original skin colours/pattern changed " + skin.id());
                }
                for (int i = 0; i < size; i++) require((sprite.getRGB(i, 0) >>> 24) == 0 && (sprite.getRGB(i, size - 1) >>> 24) == 0
                        && (sprite.getRGB(0, i) >>> 24) == 0 && (sprite.getRGB(size - 1, i) >>> 24) == 0, "sprite cropped at canvas boundary " + skin.id());
                var model = json(zip, base + "models/item/skin/" + skin.id() + ".json");
                var display = model.getAsJsonObject("display");
                require(display != null && vec(display.getAsJsonObject("fixed").getAsJsonArray("rotation")).lengthSquared() == 0,
                        "sprite inherits reversed FIXED transform " + skin.id());
                var item = json(zip, base + "items/skin/" + skin.id() + ".json").getAsJsonObject("model");
                require(item.get("type").getAsString().equals("minecraft:select"), "held geometry selector " + skin.id());
                require(item.get("property").getAsString().equals("minecraft:display_context"), "held context " + skin.id());
                require(item.getAsJsonArray("cases").get(0).getAsJsonObject().getAsJsonObject("model").get("type").getAsString().equals("minecraft:composite"), "missing held joints " + skin.id());
                var selected = json(zip, base + "models/item/trade/selected/" + skin.id() + ".json");
                require(vec(selected.getAsJsonObject("display").getAsJsonObject("fixed").getAsJsonArray("rotation")).lengthSquared() == 0, "selected icon reversed");
                for (String layer : InspectRig.layers(skin.weapon()).stream().map(InspectRig.Layer::id).distinct().toList()) {
                    String path = "inspect/" + skin.id() + "/" + layer;
                    var texture = png(zip, base + "textures/item/" + path + ".png");
                    var supplied = originals.get(base + "textures/item/" + path + ".png");
                    require(supplied != null, "missing original inspect layer " + path);
                    for (int y=0;y<128;y++) for (int x=0;x<128;x++) {
                        int pixel = supplied.getRGB(InspectRig.reverseGrip(skin.weapon()) ? 127-x : x, InspectRig.reverseGrip(skin.weapon()) ? 127-y : y);
                        require(texture.getRGB(x,y) == ((pixel >>> 24) >= 128 ? pixel | 0xff000000 : 0), "original inspect artwork changed " + path);
                    }
                    require(texture.getWidth() == 128 && texture.getHeight() == 128, "inspect edge resolution " + path);
                    var rig = json(zip, base + "models/item/" + path + ".json");
                    checkGrip(skin, rig.getAsJsonObject("display"));
                    require(rig.getAsJsonObject("textures").get("particle").getAsString().equals("#skin"), "missing particle texture " + path);
                    require(!rig.getAsJsonArray("elements").isEmpty(), "empty inspect layer " + path);
                    for (var entry : rig.getAsJsonArray("elements")) {
                        var element = entry.getAsJsonObject();
                        var from = vec(element.getAsJsonArray("from")); var to = vec(element.getAsJsonArray("to"));
                        require(to.x > from.x && to.y > from.y && to.z > from.z, "inverted geometry " + path);
                        for (var face : element.getAsJsonObject("faces").entrySet()) {
                            var uv = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                            double u1 = uv.get(0).getAsDouble(), v1 = uv.get(1).getAsDouble();
                            double u2 = uv.get(2).getAsDouble(), v2 = uv.get(3).getAsDouble();
                            for (int sample = 0; sample <= 8; sample++) {
                                int x = (int)((u1 + (u2-u1)*sample/8) * texture.getWidth() / 16);
                                int y = (int)((v1 + (v2-v1)*sample/8) * texture.getHeight() / 16);
                                require(x >= 0 && x < texture.getWidth() && y >= 0 && y < texture.getHeight() && (texture.getRGB(x,y) >>> 24) == 255,
                                        "transparent/dark rim UV " + path + "/" + face.getKey());
                            }
                            switch (face.getKey()) {
                                case "east", "west" -> require(u1 == u2, "side stretches entire weapon texture " + path);
                                case "up", "down" -> require(v1 == v2, "rim stretches entire weapon texture " + path);
                                case "north" -> require(u1 >= u2, "north face mirrored " + path);
                                case "south" -> require(u1 <= u2, "south face mirrored " + path);
                                default -> throw new AssertionError("unknown face");
                            }
                            faces++;
                        }
                    }
                    layers++;
                }
                examples.merge(skin.weapon().id(), skin, (a,b) -> a.id().compareTo(b.id()) < 0 ? a : b);
            }
            int i = 0;
            for (var skin : examples.values()) {
                int x = i % 8 * 180, y = i / 8 * 150;
                graphics.setColor(Color.WHITE); graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); graphics.drawString(skin.weapon().name(), x + 8, y + 18);
                graphics.drawImage(png(zip, "assets/mccases/textures/item/skin/" + skin.id() + ".png"), x + 8, y + 30, 80, 80, null);
                for (String layer : InspectRig.layers(skin.weapon()).stream().map(InspectRig.Layer::id).distinct().toList())
                    graphics.drawImage(png(zip, "assets/mccases/textures/item/inspect/" + skin.id() + "/" + layer + ".png"), x + 92, y + 30, 80, 80, null);
                graphics.setColor(new Color(0xaab5c4)); graphics.drawString("icon             inspect rig", x + 8, y + 131); i++;
            }
        }
        graphics.dispose(); File output = new File(args[2]); output.mkdirs(); ImageIO.write(sheet, "png", new File(output, "all-weapons.png"));
        System.out.println("PASS: all " + catalog.skins().size() + " exported skins / " + layers + " inspect layers / " + faces
                + " opaque rim faces, front/back UV orientation, original GUI artwork, unclipped sprites, solid held composites and native first/third-person palm anchors in both hands. Sheet: " + output);
    }
    private static void checkGrip(SkinDefinition skin, JsonObject display) {
        boolean reverse = InspectRig.reverseGrip(skin.weapon());
        var grip = new Vector3f((float)((skin.isKnife() ? (reverse ? .72 : .29) : .36) - .5),
                (float)(.5 - (skin.isKnife() ? .50 : .57)), 0);
        for (boolean first : java.util.List.of(true, false)) for (boolean left : java.util.List.of(false, true)) {
            String context = (first ? "firstperson" : "thirdperson") + (left ? "_lefthand" : "_righthand");
            var transform = display.getAsJsonObject(context);
            require(transform != null, "missing native hand transform " + skin.id() + "/" + context);
            var rotation = vec(transform.getAsJsonArray("rotation"));
            var translation = vec(transform.getAsJsonArray("translation")).div(16);
            var scale = vec(transform.getAsJsonArray("scale"));
            if (left) { translation.x = -translation.x; rotation.mul(1, -1, -1); }
            var model = new Matrix4f().translation(translation).rotateXYZ((float)Math.toRadians(rotation.x), (float)Math.toRadians(rotation.y), (float)Math.toRadians(rotation.z)).scale(scale);
            var anchored = model.transformPosition(new Vector3f(grip));
            var expected = first ? new Vector3f(left ? 1.5f : -1.5f,3.2f,-3.5f).div(16) : new Vector3f();
            require(anchored.distance(expected) < .001, "grip detached from palm " + skin.id() + "/" + context);
            if (first && skin.weapon().category() != WeaponCategory.GLOVE) {
                var tip = model.transformDirection(new Vector3f(reverse ? -1 : 1,0,0));
                require(reverse ? tip.z > .1 : tip.z < -.1, "first-person blade/muzzle faces the player " + skin.id() + "/" + context);
            }
            if (!first) {
                // Actual adult ItemInHandLayer, not merely the item's own display rotation.
                var nativeHand = new Matrix4f().rotateX((float)-Math.PI/2).rotateY((float)Math.PI).translate(left ? -1f/16 : 1f/16,2f/16,-10f/16).mul(model);
                var tip = nativeHand.transformDirection(new Vector3f(reverse ? -1 : 1,0,0));
                require(reverse ? tip.z > .25 : tip.z < -.25, "native blade/muzzle direction " + skin.id() + "/" + context);
                var normal = nativeHand.transformDirection(new Vector3f(0,0,1)).normalize();
                require(Math.abs(normal.z) > .5, "edge-on F5 model " + skin.id() + "/" + context);
            }
        }
    }
    private static BufferedImage png(ZipFile zip, String name) throws Exception {
        var entry = zip.getEntry(name); require(entry != null, "missing pack asset " + name);
        try (var in = zip.getInputStream(entry)) { var image = ImageIO.read(in); require(image != null, "invalid PNG " + name); return image; }
    }
    private static JsonObject json(ZipFile zip, String name) throws Exception {
        var entry = zip.getEntry(name); require(entry != null, "missing pack model " + name);
        try (var in = zip.getInputStream(entry)) { return JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject(); }
    }
    private static Vector3f vec(JsonArray a) { return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
