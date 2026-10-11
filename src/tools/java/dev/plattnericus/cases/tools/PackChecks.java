package dev.plattnericus.cases.tools;

import com.google.gson.*;
import dev.plattnericus.cases.catalog.*;
import dev.plattnericus.cases.inspect.InspectRig;
import dev.plattnericus.cases.pack.PackExporter;
import dev.plattnericus.cases.render.RenderSettings;
import dev.plattnericus.cases.render.SkinRenderer;
import dev.plattnericus.cases.render.TextureException;
import dev.plattnericus.cases.render.TextureStore;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.*;
import java.util.zip.ZipFile;

/** Inspect the actual exported assets, including every side-face UV and both hand transforms. */
public final class PackChecks {
    private PackChecks() { }
    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        var textures = new TextureStore(new File(root, "textures"));
        var catalog = new CatalogLoader(new File(root, "catalog"), textures).load(1).catalog();
        var renderer = new SkinRenderer(textures, RenderSettings.defaults());
        int faces = 0, layers = 0;
        var examples = new TreeMap<String, SkinDefinition>();
        int rows = (catalog.weapons().size() + 7) / 8;
        var sheet = new BufferedImage(8 * 180, rows * 150, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics(); graphics.setColor(new Color(0x20242b)); graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        try (var zip = new ZipFile(args[1])) {
            var entries = dev.plattnericus.cases.pack.ResourcePackInstaller.entries(zip);
            Map<String, String> sourceHashes = caseSourceHashes();
            require(sourceHashes.size() == catalog.cases().size(), "case artwork source table is incomplete");
            require(text(zip, "case-art/SOURCE.md").equals(sourceRecord()), "case artwork source record differs from the plugin source");
            for (String forbidden : java.util.List.of("assets/minecraft/textures/font/custom/prosse.png",
                    "assets/minecraft/textures/font/custom/kirbis.png", "assets/minecraft/textures/font/custom/hirsch.png",
                    "FUSION-MANIFEST.json")) require(!entries.containsKey(forbidden), "retired Fusion asset survived: " + forbidden);
            for (var skin : catalog.skins()) {
                String base = "assets/mccases/";
                var sprite = png(zip, base + "textures/item/skin/" + skin.id() + ".png");
                int size = sprite.getWidth();
                require(size >= 64 && size == sprite.getHeight() && (size & (size - 1)) == 0, "sprite dimensions " + skin.id());
                require(size == 128, "skin edge resolution " + skin.id());
                var expectedSprite = PackExporter.sprite(renderer, skin);
                int opaquePixels = 0;
                for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                    int pixel = sprite.getRGB(x, y), alpha = pixel >>> 24;
                    require(alpha == 0 || alpha == 255, "translucent extruded edge " + skin.id());
                    require(alpha != 0 || (pixel & 0xffffff) == 0, "hidden color fringe " + skin.id());
                    if (alpha == 255) opaquePixels++;
                    require(pixel == expectedSprite.getRGB(x, y), "skin renderer output/source mismatch " + skin.id());
                }
                require(opaquePixels >= 64, "empty generated skin " + skin.id());
                for (int i = 0; i < size; i++) require((sprite.getRGB(i, 0) >>> 24) == 0 && (sprite.getRGB(i, size - 1) >>> 24) == 0
                        && (sprite.getRGB(0, i) >>> 24) == 0 && (sprite.getRGB(size - 1, i) >>> 24) == 0, "sprite cropped at canvas boundary " + skin.id());
                var model = json(zip, base + "models/item/skin/" + skin.id() + ".json");
                var display = model.getAsJsonObject("display");
                require(display != null && vec(display.getAsJsonObject("fixed").getAsJsonArray("rotation")).lengthSquared() == 0,
                        "sprite inherits reversed FIXED transform " + skin.id());
                var item = json(zip, base + "items/skin/" + skin.id() + ".json").getAsJsonObject("model");
                require(item.get("type").getAsString().equals("minecraft:select"), "held geometry selector " + skin.id());
                require(item.get("property").getAsString().equals("minecraft:display_context"), "held context " + skin.id());
                var cases = item.getAsJsonArray("cases");
                boolean glove = skin.weapon().category() == WeaponCategory.GLOVE;
                require(cases.size() == (glove ? 1 : 2), "held display-context coverage " + skin.id());
                var first = cases.get(0).getAsJsonObject();
                var firstModels = first.getAsJsonObject("model").getAsJsonArray("models");
                require(first.getAsJsonObject("model").get("type").getAsString().equals("minecraft:composite"), "missing held joints " + skin.id());
                long layerCount = InspectRig.layers(skin.weapon()).stream().map(InspectRig.Layer::id).distinct().count();
                if (glove) {
                    require(firstModels.size() == layerCount, "glove held composite changed " + skin.id());
                } else {
                    require(first.getAsJsonArray("when").toString().contains("firstperson_"), "arm is not limited to first person " + skin.id());
                    require(firstModels.size() == layerCount + 1, "first-person arm missing from held composite " + skin.id());
                    var third = cases.get(1).getAsJsonObject();
                    require(third.getAsJsonArray("when").toString().contains("thirdperson_")
                            && third.getAsJsonObject("model").getAsJsonArray("models").size() == layerCount,
                            "third-person composite must keep the vanilla player arm " + skin.id());
                    String armStyle = skin.isKnife()
                            ? InspectRig.reverseGrip(skin.weapon()) ? "knife-reverse" : "knife"
                            : "gun";
                    String armPath = "held/arm/" + armStyle;
                    var armItem = json(zip, base + "items/" + armPath + ".json").getAsJsonObject("model");
                    require(armItem.get("type").getAsString().equals("minecraft:model")
                            && armItem.get("model").getAsString().equals("mccases:item/" + armPath), "first-person arm item model " + skin.id());
                    require(firstModels.get(0).getAsJsonObject().get("model").getAsString().equals("mccases:item/" + armPath), "first-person arm ordering " + skin.id());
                    var armModel = json(zip, base + "models/item/" + armPath + ".json");
                    checkGrip(skin, armModel.getAsJsonObject("display"));
                    require(armModel.getAsJsonArray("elements").size() == 3, "arm and sleeve geometry missing " + skin.id());
                    for (var element : armModel.getAsJsonArray("elements")) {
                        var from = vec(element.getAsJsonObject().getAsJsonArray("from"));
                        var to = vec(element.getAsJsonObject().getAsJsonArray("to"));
                        require(to.x > from.x && to.y > from.y && to.z > from.z, "invalid arm geometry " + skin.id());
                    }
                }
                var selected = json(zip, base + "models/item/trade/selected/" + skin.id() + ".json");
                require(vec(selected.getAsJsonObject("display").getAsJsonObject("fixed").getAsJsonArray("rotation")).lengthSquared() == 0, "selected icon reversed");
                for (String layer : InspectRig.layers(skin.weapon()).stream().map(InspectRig.Layer::id).distinct().toList()) {
                    String path = "inspect/" + skin.id() + "/" + layer;
                    var texture = png(zip, base + "textures/item/" + path + ".png");
                    var expectedLayer = expectedLayer(renderer, skin, layer);
                    int layerPixels = 0;
                    for (int y=0;y<128;y++) for (int x=0;x<128;x++) {
                        int pixel = texture.getRGB(x,y);
                        require(pixel == expectedLayer.getRGB(x,y), "inspect renderer output/source mismatch " + path);
                        if ((pixel >>> 24) == 255) layerPixels++;
                    }
                    require(layerPixels >= 8, "empty inspect texture " + path);
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
            require(entries.keySet().stream().filter(n -> n.matches("assets/mccases/models/item/held/arm/(gun|knife|knife-reverse)\\.json")).count() == 3,
                    "first-person arm models should share the three grip profiles");
            require(entries.keySet().stream().filter(n -> n.startsWith("assets/mccases/textures/item/skin/") && n.endsWith(".png")).count() == catalog.skins().size(), "exported skin texture count");
            require(entries.keySet().stream().filter(n -> n.startsWith("assets/mccases/textures/item/case/") && n.endsWith(".png")).count() == catalog.cases().size(), "exported case texture count");
            for (var caseDef : catalog.cases()) {
                byte[] sourcePng = caseArtBytes(caseDef.id());
                var expectedCase = ImageIO.read(new java.io.ByteArrayInputStream(sourcePng));
                var actualCase = png(zip, "assets/mccases/textures/item/case/" + caseDef.id() + ".png");
                require(expectedCase.getWidth() == 128 && expectedCase.getHeight() == 128, "case art edge resolution " + caseDef.id());
                require(imagesEqual(expectedCase, actualCase), "case art source mismatch " + caseDef.id());
                String hash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sourcePng));
                require(hash.equals(sourceHashes.get(caseDef.id() + ".png")), "case art source hash mismatch " + caseDef.id());
                require(java.util.Arrays.equals(sourcePng, assetBytes(zip, "assets/mccases/textures/item/case/" + caseDef.id() + ".png")),
                        "case art bytes changed during export " + caseDef.id());
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
                + " opaque rim faces, front/back UV orientation, catalog-rendered skins and articulated layers, all " + catalog.cases().size()
                + " detailed case textures, unclipped sprites, three shared pixel-arm profiles, solid held composites and native first/third-person palm anchors in both hands. Sheet: " + output);
    }

    private static BufferedImage expectedLayer(SkinRenderer renderer, SkinDefinition skin, String layer) throws TextureException {
        int inset = 4, content = 120;
        var source = InspectRig.presentation(skin.weapon(), renderer.render(skin, 0,
                Math.max(skin.minFloat(), Math.min(skin.maxFloat(), .02)), 0).image());
        var canvas = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        var graphics = canvas.createGraphics();
        graphics.drawImage(source.scaledTo(content, content).toBufferedImage(), inset, inset, null);
        graphics.dispose();
        var result = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        boolean reverse = InspectRig.reverseGrip(skin.weapon());
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            double originalX = (x + .5 - inset) / content, originalY = (y + .5 - inset) / content;
            if (reverse) { originalX = 1 - originalX; originalY = 1 - originalY; }
            int pixel = canvas.getRGB(x, y);
            if ((pixel >>> 24) >= 128 && layer.equals(InspectRig.layerAt(skin.weapon(), originalX, originalY))) result.setRGB(x, y, pixel | 0xff000000);
        }
        return result;
    }

    private static byte[] caseArtBytes(String id) throws java.io.IOException {
        try (var input = PackChecks.class.getResourceAsStream("/case-art/" + id + ".png")) {
            require(input != null, "missing source case artwork " + id);
            return input.readAllBytes();
        }
    }

    private static Map<String, String> caseSourceHashes() throws java.io.IOException {
        var result = new TreeMap<String, String>();
        Matcher matcher = Pattern.compile("^\\| `([^`]+\\.png)` \\| `([0-9a-f]{64})` \\|$", Pattern.MULTILINE).matcher(sourceRecord());
        while (matcher.find()) require(result.putIfAbsent(matcher.group(1), matcher.group(2)) == null, "duplicate case source hash " + matcher.group(1));
        return result;
    }

    private static String sourceRecord() throws java.io.IOException {
        try (var input = PackChecks.class.getResourceAsStream("/case-art/SOURCE.md")) {
            require(input != null, "missing case art source record");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String text(ZipFile zip, String name) throws java.io.IOException {
        return new String(assetBytes(zip, name), StandardCharsets.UTF_8);
    }

    private static byte[] assetBytes(ZipFile zip, String name) throws java.io.IOException {
        var entry = zip.getEntry(name); require(entry != null, "missing pack asset " + name);
        try (var input = zip.getInputStream(entry)) { return input.readAllBytes(); }
    }

    private static boolean imagesEqual(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
        for (int y = 0; y < a.getHeight(); y++) for (int x = 0; x < a.getWidth(); x++) if (a.getRGB(x,y) != b.getRGB(x,y)) return false;
        return true;
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
            float expectedScale = skin.weapon().category() == WeaponCategory.GLOVE
                    ? first ? .80f : .85f
                    : skin.isKnife() ? first ? .62f : 1.05f : first ? .58f : 1.0f;
            require(Math.abs(Math.abs(scale.x) - expectedScale) < .001
                    && Math.abs(scale.y - expectedScale) < .001 && Math.abs(scale.z - expectedScale) < .001,
                    "unexpected held-model scale " + skin.id() + "/" + context);
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
