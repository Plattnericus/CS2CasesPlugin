package dev.plattnericus.cases.tools;

import com.google.gson.*;
import dev.plattnericus.cases.catalog.*;
import dev.plattnericus.cases.inspect.InspectRig;
import dev.plattnericus.cases.render.TextureStore;
import org.joml.Vector3f;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.zip.ZipFile;

/** Inspect the actual exported assets, including every side-face UV and both hand transforms. */
public final class PackChecks {
    private PackChecks() { }
    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        var catalog = new CatalogLoader(new File(root, "catalog"), new TextureStore(new File(root, "textures"))).load(1).catalog();
        int faces = 0, layers = 0;
        var examples = new TreeMap<String, SkinDefinition>();
        int rows = (catalog.weapons().size() + 7) / 8;
        var sheet = new BufferedImage(8 * 180, rows * 150, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics(); graphics.setColor(new Color(0x20242b)); graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        try (var zip = new ZipFile(args[1])) {
            for (var skin : catalog.skins()) {
                String base = "assets/mccases/";
                var sprite = png(zip, base + "textures/item/skin/" + skin.id() + ".png");
                int size = sprite.getWidth();
                require(size >= 64 && size == sprite.getHeight() && (size & (size - 1)) == 0, "sprite dimensions " + skin.id());
                for (int i = 0; i < size; i++) require((sprite.getRGB(i, 0) >>> 24) == 0 && (sprite.getRGB(i, size - 1) >>> 24) == 0
                        && (sprite.getRGB(0, i) >>> 24) == 0 && (sprite.getRGB(size - 1, i) >>> 24) == 0, "sprite cropped at canvas boundary " + skin.id());
                var model = json(zip, base + "models/item/skin/" + skin.id() + ".json");
                var display = model.getAsJsonObject("display");
                require(display != null && vec(display.getAsJsonObject("fixed").getAsJsonArray("rotation")).lengthSquared() == 0,
                        "sprite inherits reversed FIXED transform " + skin.id());
                for (String mode : java.util.List.of("firstperson", "thirdperson")) for (boolean left : java.util.List.of(false, true)) {
                    Vector3f angles = vec(display.getAsJsonObject(mode + (left ? "_lefthand" : "_righthand")).getAsJsonArray("rotation"));
                    if (left) angles.mul(1, -1, -1); // Minecraft's left-hand ItemTransform application.
                    var tip = new Vector3f(1, skin.isKnife() ? 1 : 0, 0).rotateX((float)Math.toRadians(angles.x))
                            .rotateZ((float)Math.toRadians(angles.z)).rotateY((float)Math.toRadians(angles.y));
                    require(tip.z < -.1, "muzzle/blade points back at the player " + skin.id() + "/" + mode + "/" + left);
                }
                var selected = json(zip, base + "models/item/trade/selected/" + skin.id() + ".json");
                require(vec(selected.getAsJsonObject("display").getAsJsonObject("fixed").getAsJsonArray("rotation")).lengthSquared() == 0, "selected icon reversed");
                for (String layer : InspectRig.layers(skin.weapon()).stream().map(InspectRig.Layer::id).distinct().toList()) {
                    String path = "inspect/" + skin.id() + "/" + layer;
                    var texture = png(zip, base + "textures/item/" + path + ".png");
                    var rig = json(zip, base + "models/item/" + path + ".json");
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
                + " opaque rim faces, front/back UV orientation, unclipped sprites, both hands and first/third-person forward-facing blades/muzzles. Sheet: " + output);
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
