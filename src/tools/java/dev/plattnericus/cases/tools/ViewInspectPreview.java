package dev.plattnericus.cases.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.plattnericus.cases.catalog.CatalogLoader;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.catalog.WeaponType;
import dev.plattnericus.cases.inspect.HeldRig;
import dev.plattnericus.cases.inspect.InspectAnimation;
import dev.plattnericus.cases.inspect.InspectModels;
import dev.plattnericus.cases.inspect.InspectRig;
import dev.plattnericus.cases.inspect.ViewInspect;
import dev.plattnericus.cases.render.SpriteFrame;
import dev.plattnericus.cases.render.TextureStore;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

/**
 * Software renders of the held-pose inspects as the owner sees them: the exported rig cuboids with
 * their textures, placed by {@link ViewInspect} exactly like the plugin's view-locked scene, and the
 * skin arms as boxes from the hand to the elbow. One contact sheet and one GIF per animation land in
 * the output folder. These are previews for animation work, not Minecraft client screenshots.
 *
 * <p>System properties: {@code mccases.preview.only} (comma separated weapon ids) and
 * {@code mccases.preview.movies} ({@code false} skips the GIFs).
 */
public final class ViewInspectPreview {

    private static final int W = 640, H = 360;
    private static final double TAN = Math.tan(Math.toRadians(35));
    private static final Vector3f LIGHT = new Vector3f(.35f, .8f, .5f).normalize();
    private static final Matrix4f MIRROR = new Matrix4f().scaling(-1, 1, 1);

    private record Quad(Vector3f[] corners, float[][] uv, BufferedImage texture) { }

    private record Box(Matrix4f matrix, int color) { }

    private ViewInspectPreview() {
    }

    public static void main(String[] args) throws Exception {
        File defaults = new File(args[0]);
        File out = new File(args[2]);
        out.mkdirs();
        String only = System.getProperty("mccases.preview.only", "");
        boolean sheets = !Boolean.getBoolean("mccases.preview.traceOnly");
        Set<String> filter = only.isBlank() ? Set.of() : Set.of(only.split(","));
        boolean movies = Boolean.parseBoolean(System.getProperty("mccases.preview.movies", "true"));
        TextureStore textures = new TextureStore(new File(defaults, "textures"));
        var catalog = new CatalogLoader(new File(defaults, "catalog"), textures).load(1).catalog();
        List<String> warnings = new ArrayList<>();
        InspectModels models = new InspectModels(new File(defaults, "inspect.yml"), warnings::add);
        warnings.forEach(System.out::println);
        int rendered = 0;
        try (ZipFile pack = new ZipFile(new File(args[1]))) {
            for (WeaponType weapon : catalog.weapons()) {
                if (!HeldRig.held(weapon) || !filter.isEmpty() && !filter.contains(weapon.id())) continue;
                SkinDefinition skin = catalog.skins().stream().filter(s -> s.weapon() == weapon)
                        .min(Comparator.comparing(SkinDefinition::id)).orElse(null);
                if (skin == null) continue;
                SpriteFrame frame = HeldRig.rigFrame(weapon, textures.weapon(weapon.id(), weapon.textureFolder()));
                HeldRig.Stance stance = HeldRig.stance(weapon, frame);
                Map<String, List<Quad>> layers = new LinkedHashMap<>();
                for (String layer : InspectRig.layers(weapon).stream().map(InspectRig.Layer::id).distinct().toList()) {
                    layers.put(layer, quads(pack, skin.id(), layer));
                }
                List<String> pool = models.viewPool(weapon);
                if (pool == null) continue;
                if (Boolean.getBoolean("mccases.preview.skins")) {
                    skins(pack, catalog.skins().stream().filter(s -> s.weapon() == weapon).sorted(Comparator.comparing(SkinDefinition::id)).toList(),
                            weapon, frame, stance, models.viewAnimation(pool.getFirst()), new File(out, "skins_" + weapon.id() + ".png"));
                    rendered++;
                    continue;
                }
                for (String id : pool) {
                    InspectAnimation animation = models.viewAnimation(id);
                    if (System.getProperty("mccases.preview.trace", "").equals(id)) trace(weapon, frame, stance, animation);
                    if (sheets) sheet(weapon, frame, stance, layers, animation, new File(out, weapon.id() + "_" + id + ".png"));
                    if (movies) movie(weapon, frame, stance, layers, animation, new File(out, weapon.id() + "_" + id + ".gif"));
                    rendered++;
                }
                System.out.println("rendered " + weapon.id() + " " + pool);
            }
        }
        System.out.println("PASS: " + rendered + " held-pose inspect previews in " + out);
    }

    /**
     * Every skin of a weapon in the hold and halfway through the weapon's first inspect, from its own
     * exported rig layers: one tile pair per skin, so a skin with a missing or broken rig stands out.
     */
    private static void skins(ZipFile pack, List<SkinDefinition> skins, WeaponType weapon, SpriteFrame frame, HeldRig.Stance stance,
                              InspectAnimation animation, File file) throws Exception {
        int tw = 228, th = 180, columns = 8;
        int rows = (skins.size() + columns / 2 - 1) / (columns / 2);
        BufferedImage sheet = new BufferedImage(columns * tw, rows * th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        int mid = animation.duration() / 2;
        for (int i = 0; i < skins.size(); i++) {
            Map<String, List<Quad>> layers = new LinkedHashMap<>();
            for (String layer : InspectRig.layers(weapon).stream().map(InspectRig.Layer::id).distinct().toList()) {
                layers.put(layer, quads(pack, skins.get(i).id(), layer));
            }
            for (int k = 0; k < 2; k++) {
                BufferedImage image = render(weapon, frame, stance, layers, animation, k == 0 ? 0 : mid, 1280, 720);
                int x = (i % (columns / 2)) * 2 + k, y = i / (columns / 2);
                g.drawImage(image.getSubimage(1280 - 760, 720 - 600, 760, 600), x * tw, y * th, tw, th, null);
            }
            label(g, (i % (columns / 2)) * 2 * tw, i / (columns / 2) * th, skins.get(i).id());
        }
        g.dispose();
        ImageIO.write(sheet, "png", file);
        System.out.println("skins " + weapon.id() + ": " + skins.size());
    }

    /** Per tick: grip and tip in view space, and how squarely the flat faces the eyes (|normal . to camera|). */
    private static void trace(WeaponType weapon, SpriteFrame frame, HeldRig.Stance stance, InspectAnimation animation) {
        Vector3f tipCanvas = HeldRig.inGripFrame(weapon, frame, .95, .5);
        for (int t = 0; t <= animation.duration(); t++) {
            ViewInspect.Sample sample = ViewInspect.sample(weapon, frame, stance, animation, t, ViewInspect.Variation.NONE);
            Matrix4f m = new Matrix4f(sample.weapon()).mul(HeldRig.gripFrame(weapon, frame).invert(new Matrix4f()));
            Vector3f grip = m.transformPosition(new Vector3f()), tip = m.transformPosition(new Vector3f(tipCanvas));
            Vector3f normal = m.transformDirection(new Vector3f(0, 0, 1)).normalize();
            float facing = Math.abs(normal.dot(new Vector3f(grip).negate().normalize()));
            System.out.printf(java.util.Locale.ROOT, "%s t%-3d grip %6.2f %6.2f %6.2f  tip %6.2f %6.2f %6.2f  facing %.2f  held %s%n", animation.id(), t,
                    grip.x, grip.y, grip.z, tip.x, tip.y, tip.z, facing, ViewInspect.held(animation, t));
        }
    }

    /** The faces of one exported rig layer in item model space (blocks), with its texture. */
    private static List<Quad> quads(ZipFile pack, String skin, String layer) throws Exception {
        String base = "assets/mccases/";
        String path = "inspect/" + skin + "/" + layer;
        var modelEntry = pack.getEntry(base + "models/item/" + path + ".json");
        var textureEntry = pack.getEntry(base + "textures/item/" + path + ".png");
        if (modelEntry == null || textureEntry == null) throw new IllegalStateException("missing rig layer " + path);
        BufferedImage texture = ImageIO.read(pack.getInputStream(textureEntry));
        JsonObject model = JsonParser.parseString(new String(pack.getInputStream(modelEntry).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        List<Quad> quads = new ArrayList<>();
        for (var element : model.getAsJsonArray("elements")) {
            JsonObject e = element.getAsJsonObject();
            Vector3f a = vec(e.getAsJsonArray("from")).div(16), b = vec(e.getAsJsonArray("to")).div(16);
            JsonObject faces = e.getAsJsonObject("faces");
            for (String face : faces.keySet()) {
                JsonArray uv = faces.getAsJsonObject(face).getAsJsonArray("uv");
                float u1 = uv.get(0).getAsFloat(), v1 = uv.get(1).getAsFloat(), u2 = uv.get(2).getAsFloat(), v2 = uv.get(3).getAsFloat();
                quads.add(new Quad(corners(face, a, b), new float[][]{{u1, v1}, {u1, v2}, {u2, v2}, {u2, v1}}, texture));
            }
        }
        return quads;
    }

    /** Minecraft's face vertex order: top-left, bottom-left, bottom-right, top-right of the texture. */
    private static Vector3f[] corners(String face, Vector3f a, Vector3f b) {
        return switch (face) {
            case "south" -> new Vector3f[]{v(a.x, b.y, b.z), v(a.x, a.y, b.z), v(b.x, a.y, b.z), v(b.x, b.y, b.z)};
            case "north" -> new Vector3f[]{v(b.x, b.y, a.z), v(b.x, a.y, a.z), v(a.x, a.y, a.z), v(a.x, b.y, a.z)};
            case "east" -> new Vector3f[]{v(b.x, b.y, b.z), v(b.x, a.y, b.z), v(b.x, a.y, a.z), v(b.x, b.y, a.z)};
            case "west" -> new Vector3f[]{v(a.x, b.y, a.z), v(a.x, a.y, a.z), v(a.x, a.y, b.z), v(a.x, b.y, b.z)};
            case "up" -> new Vector3f[]{v(a.x, b.y, a.z), v(a.x, b.y, b.z), v(b.x, b.y, b.z), v(b.x, b.y, a.z)};
            default -> new Vector3f[]{v(a.x, a.y, b.z), v(a.x, a.y, a.z), v(b.x, a.y, a.z), v(b.x, a.y, b.z)};
        };
    }

    /** Sixteen ticks over the whole inspect, cropped to the hand: the right part of a 1280x720 frame. */
    private static void sheet(WeaponType weapon, SpriteFrame frame, HeldRig.Stance stance, Map<String, List<Quad>> layers,
                              InspectAnimation animation, File file) throws Exception {
        int columns = 4, rows = 4, tw = 380, th = 300;
        BufferedImage sheet = new BufferedImage(columns * tw, rows * th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        for (int i = 0; i < columns * rows; i++) {
            int t = Math.round(animation.duration() * i / (float) (columns * rows - 1));
            BufferedImage image = render(weapon, frame, stance, layers, animation, t, 1280, 720);
            g.drawImage(image.getSubimage(1280 - 2 * tw, 720 - 2 * th, 2 * tw, 2 * th), i % columns * tw, i / columns * th, tw, th, null);
            label(g, i % columns * tw, i / columns * th, "t" + t);
        }
        label(g, 0, rows * th - 16, weapon.id() + " " + animation.id());
        g.dispose();
        ImageIO.write(sheet, "png", file);
    }

    private static void movie(WeaponType weapon, SpriteFrame frame, HeldRig.Stance stance, Map<String, List<Quad>> layers,
                              InspectAnimation animation, File file) throws Exception {
        var writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (var stream = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(stream);
            writer.prepareWriteSequence(null);
            for (int tick = -6; tick <= animation.duration() + 6; tick++) {
                int t = Math.max(0, Math.min(animation.duration(), tick));
                BufferedImage image = render(weapon, frame, stance, layers, animation, t);
                Graphics2D g = image.createGraphics();
                label(g, 0, 0, weapon.id() + " " + animation.id() + " t" + t);
                g.dispose();
                var metadata = writer.getDefaultImageMetadata(new javax.imageio.ImageTypeSpecifier(image), writer.getDefaultWriteParam());
                String format = metadata.getNativeMetadataFormatName();
                var tree = (javax.imageio.metadata.IIOMetadataNode) metadata.getAsTree(format);
                var control = (javax.imageio.metadata.IIOMetadataNode) tree.getElementsByTagName("GraphicControlExtension").item(0);
                control.setAttribute("delayTime", "5");
                control.setAttribute("disposalMethod", "none");
                if (tick == -6) {
                    var extensions = new javax.imageio.metadata.IIOMetadataNode("ApplicationExtensions");
                    var loop = new javax.imageio.metadata.IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID", "NETSCAPE");
                    loop.setAttribute("authenticationCode", "2.0");
                    loop.setUserObject(new byte[]{1, 0, 0});
                    extensions.appendChild(loop);
                    tree.appendChild(extensions);
                }
                metadata.setFromTree(format, tree);
                writer.writeToSequence(new javax.imageio.IIOImage(image, null, metadata), writer.getDefaultWriteParam());
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    /** One tick in the 70 degree, 16:9 first-person view. */
    static BufferedImage render(WeaponType weapon, SpriteFrame frame, HeldRig.Stance stance, Map<String, List<Quad>> layers,
                                InspectAnimation animation, int t) {
        return render(weapon, frame, stance, layers, animation, t, W, H);
    }

    static BufferedImage render(WeaponType weapon, SpriteFrame frame, HeldRig.Stance stance, Map<String, List<Quad>> layers,
                                InspectAnimation animation, int t, int W, int H) {
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        float[] depth = new float[W * H];
        Arrays.fill(depth, Float.POSITIVE_INFINITY);
        for (int y = 0; y < H; y++) {
            int sky = y < H * .48 ? lerp(0x7fa7ff, 0xbcd3ff, y / (H * .48f)) : lerp(0x5f8f3a, 0x4a7a2c, (y - H * .48f) / (H * .52f));
            for (int x = 0; x < W; x++) image.setRGB(x, y, sky);
        }
        ViewInspect.Sample sample = ViewInspect.sample(weapon, frame, stance, animation, t, ViewInspect.Variation.NONE);
        boolean dual = HeldRig.dual(weapon);
        for (boolean copy : dual ? List.of(false, true) : List.of(false)) {
            Matrix4f side = copy ? MIRROR : new Matrix4f();
            for (var layer : layers.entrySet()) {
                Matrix4f m = new Matrix4f(side).mul(sample.weapon(layer.getKey()));
                for (Quad q : layer.getValue()) quad(image, depth, m, q);
            }
            arm(image, depth, side, sample.hand(), sample.elbow(), sample.reference(), stance.armScale(), copy);
            if (!copy && sample.supportHand() != null) {
                arm(image, depth, side, sample.supportHand(), sample.supportElbow(), new Vector3f(-1, 0, 0), stance.armScale(), true);
            }
        }
        return image;
    }

    private static void arm(BufferedImage image, float[] depth, Matrix4f side, Vector3f hand, Vector3f elbow, Vector3f reference,
                            float scale, boolean left) {
        Vector3f h = side.transformPosition(new Vector3f(hand)), e = side.transformPosition(new Vector3f(elbow));
        Vector3f outward = side.transformDirection(new Vector3f(reference));
        Matrix4f frame = HeldRig.armFrame(h, e, outward);
        float w = .125f * scale, d = .14f * scale, length = .75f * scale;
        // Sleeve towards the elbow, skin at the hand.
        box(image, depth, new Matrix4f(frame).translate(0, -length, 0).scale(2 * w, length * .55f, 2 * d).translate(-.5f, 0, -.5f), 0x3c6fb0);
        box(image, depth, new Matrix4f(frame).translate(0, -length * .45f, 0).scale(2 * w, length * .45f, 2 * d).translate(-.5f, 0, -.5f),
                left ? 0xd9a77e : 0xe8b48a);
    }

    private static void box(BufferedImage image, float[] depth, Matrix4f m, int color) {
        BufferedImage texture = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        texture.setRGB(0, 0, 0xff000000 | color);
        Vector3f a = new Vector3f(), b = new Vector3f(1, 1, 1);
        for (String face : List.of("north", "south", "east", "west", "up", "down")) {
            quad(image, depth, m, new Quad(corners(face, a, b), new float[][]{{0, 0}, {0, 16}, {16, 16}, {16, 0}}, texture));
        }
    }

    private static void quad(BufferedImage image, float[] depth, Matrix4f m, Quad q) {
        Vector3f[] p = new Vector3f[4];
        for (int i = 0; i < 4; i++) p[i] = m.transformPosition(new Vector3f(q.corners()[i]));
        Vector3f normal = new Vector3f(p[1]).sub(p[0]).cross(new Vector3f(p[3]).sub(p[0]));
        if (normal.lengthSquared() < 1e-12f) normal = new Vector3f(new Vector3f(p[2]).sub(p[1])).cross(new Vector3f(p[0]).sub(p[1]));
        if (normal.lengthSquared() < 1e-12f) return;
        normal.normalize();
        float shade = .55f + .45f * Math.abs(normal.dot(LIGHT));
        triangle(image, depth, p, q, new int[]{0, 1, 2}, shade);
        triangle(image, depth, p, q, new int[]{0, 2, 3}, shade);
    }

    /** Clips a triangle against the near plane, then rasterises the pieces with perspective-correct texturing. */
    private static void triangle(BufferedImage image, float[] depth, Vector3f[] p, Quad q, int[] index, float shade) {
        List<float[]> polygon = new ArrayList<>();
        for (int k : index) polygon.add(new float[]{p[k].x, p[k].y, p[k].z, q.uv()[k][0], q.uv()[k][1]});
        List<float[]> clipped = new ArrayList<>();
        for (int k = 0; k < polygon.size(); k++) {
            float[] a = polygon.get(k), b = polygon.get((k + 1) % polygon.size());
            boolean inA = a[2] <= -NEAR, inB = b[2] <= -NEAR;
            if (inA) clipped.add(a);
            if (inA != inB) {
                float u = (-NEAR - a[2]) / (b[2] - a[2]);
                float[] c = new float[5];
                for (int i = 0; i < 5; i++) c[i] = a[i] + (b[i] - a[i]) * u;
                clipped.add(c);
            }
        }
        for (int k = 1; k + 1 < clipped.size(); k++) raster(image, depth, clipped.get(0), clipped.get(k), clipped.get(k + 1), q.texture(), shade);
    }

    private static final float NEAR = .05f;

    private static void raster(BufferedImage image, float[] depth, float[] a, float[] b, float[] c, BufferedImage texture, float shade) {
        float[][] v = {a, b, c};
        float[] sx = new float[3], sy = new float[3], iz = new float[3], uz = new float[3], vz = new float[3];
        int width = image.getWidth(), height = image.getHeight();
        for (int k = 0; k < 3; k++) {
            float z = -v[k][2];
            sx[k] = (float) ((v[k][0] / z / (TAN * width / height) + 1) / 2 * width);
            sy[k] = (float) ((1 - v[k][1] / z / TAN) / 2 * height);
            iz[k] = 1 / z;
            uz[k] = v[k][3] * iz[k];
            vz[k] = v[k][4] * iz[k];
        }
        float area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0]);
        if (Math.abs(area) < 1e-6f) return;
        int x0 = Math.max(0, (int) Math.floor(Math.min(sx[0], Math.min(sx[1], sx[2]))));
        int x1 = Math.min(width - 1, (int) Math.ceil(Math.max(sx[0], Math.max(sx[1], sx[2]))));
        int y0 = Math.max(0, (int) Math.floor(Math.min(sy[0], Math.min(sy[1], sy[2]))));
        int y1 = Math.min(height - 1, (int) Math.ceil(Math.max(sy[0], Math.max(sy[1], sy[2]))));
        int tw = texture.getWidth(), th = texture.getHeight();
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float px = x + .5f, py = y + .5f;
                float w0 = ((sx[1] - px) * (sy[2] - py) - (sx[2] - px) * (sy[1] - py)) / area;
                float w1 = ((sx[2] - px) * (sy[0] - py) - (sx[0] - px) * (sy[2] - py)) / area;
                float w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                float z = 1 / (w0 * iz[0] + w1 * iz[1] + w2 * iz[2]);
                int i = y * width + x;
                if (z >= depth[i]) continue;
                float u = (w0 * uz[0] + w1 * uz[1] + w2 * uz[2]) * z, t = (w0 * vz[0] + w1 * vz[1] + w2 * vz[2]) * z;
                int tx = Math.max(0, Math.min(tw - 1, (int) (u / 16 * tw))), ty = Math.max(0, Math.min(th - 1, (int) (t / 16 * th)));
                int texel = texture.getRGB(tx, ty);
                if ((texel >>> 24) < 128) continue;
                depth[i] = z;
                int r = Math.min(255, (int) (((texel >> 16) & 255) * shade)), g = Math.min(255, (int) (((texel >> 8) & 255) * shade));
                int bl = Math.min(255, (int) ((texel & 255) * shade));
                image.setRGB(x, y, r << 16 | g << 8 | bl);
            }
        }
    }

    private static void label(Graphics2D g, int x, int y, String text) {
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(x, y, 8 + 7 * text.length(), 16);
        g.setColor(Color.WHITE);
        g.drawString(text, x + 4, y + 12);
    }

    private static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }

    private static Vector3f vec(JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }
}
