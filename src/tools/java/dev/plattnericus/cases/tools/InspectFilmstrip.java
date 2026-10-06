package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.inspect.InspectAnimation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders inspect animations as filmstrips seen from the player's camera (x right, y up, looking
 * along -z), using the same keyframe math as the plugin. Used to check animations without a client.
 */
public final class InspectFilmstrip {

    private static BufferedImage sprite;
    private static List<Vector3f> spriteOutline;
    private static boolean handView;
    private static final List<String> framingFailures = new ArrayList<>();

    private record Part(String group, String material, Vector3f pos, Vector3f size, Vector3f rot, boolean block) {
    }

    private record Face(Polygon poly, double depth, Color color) {
    }

    private InspectFilmstrip() {
    }

    public static void main(String[] args) throws Exception {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(args[0]));
        File out = new File(args[1]);
        out.mkdirs();
        ANCHOR.set((float) y.getDouble("anchor.right"), (float) y.getDouble("anchor.up"), -(float) y.getDouble("anchor.forward"));
        float scale = (float) y.getDouble("model-scale", 1);
        for (String id : y.getConfigurationSection("models").getKeys(false)) {
            var model = y.getConfigurationSection("models." + id);
            while (model.contains("copy")) model = y.getConfigurationSection("models." + model.getString("copy"));
            List<Part> parts = parts(model.getConfigurationSection("parts"));
            String animation = model.getString("animation", "default");
            for (String suffix : List.of("", "_reverse", "_flourish", "_quick")) {
                if (!y.isConfigurationSection("animations." + animation + suffix)) continue;
                InspectAnimation anim = animation(animation + suffix, y.getConfigurationSection("animations." + animation + suffix));
                checkFraming(parts, anim, scale);
                render(parts, anim, scale, new File(out, id + suffix + ".png"));
                // One movie per family/variant; model copies still get their own filmstrip.
                if (id.equals("karambit") || id.equals("butterfly") || id.equals("skeleton")
                        || id.equals("m9_bayonet") || id.equals("folding") || !model.getString("animation").equals("default")) {
                    movie(parts, anim, scale, new File(out, id + suffix + ".gif"));
                }
            }
        }
        var pistol = parts(y.getConfigurationSection("models.pistol.parts"));
        var twirl = animation("deagle_twirl", y.getConfigurationSection("animations.deagle_twirl"));
        checkFraming(pistol, twirl, scale);
        render(pistol, twirl, scale, new File(out, "deagle_twirl.png"));
        movie(pistol, twirl, scale, new File(out, "deagle_twirl.gif"));
        ANCHOR.set((float) y.getDouble("reveal-anchor.right"), (float) y.getDouble("reveal-anchor.up"), -(float) y.getDouble("reveal-anchor.forward"));
        var reveal = animation("reveal", y.getConfigurationSection("animations.reveal"));
        for (String id : List.of("m9_bayonet", "rifle")) {
            var parts = parts(y.getConfigurationSection("models." + id + ".parts"));
            checkFraming(parts, reveal, 1);
            render(parts, reveal, 1, new File(out, id + "_reveal.png"));
        }
        if (args.length > 2) {
            ANCHOR.set((float) y.getDouble("anchor.right"), (float) y.getDouble("anchor.up"), -(float) y.getDouble("anchor.forward"));
            Map<String, String> examples = Map.ofEntries(
                    Map.entry("rifle", "ak47_case_hardened"), Map.entry("pistol", "deagle_printstream"),
                    Map.entry("smg", "mp9_food_chain"), Map.entry("sniper", "awp_atheris"),
                    Map.entry("heavy", "nova_wild_six"), Map.entry("equipment", "zeus_olympus"),
                    Map.entry("karambit", "karambit_fade"), Map.entry("butterfly", "butterfly_doppler"),
                    Map.entry("m9", "m9_bayonet_marble_fade"), Map.entry("skeleton", "skeleton_fade"),
                    Map.entry("default", "flip_doppler"));
            try (var zip = new java.util.zip.ZipFile(args[2])) {
                for (var entry : examples.entrySet()) {
                    var texture = zip.getEntry("assets/mccases/textures/item/skin/" + entry.getValue() + ".png");
                    if (texture == null) throw new IllegalStateException("Missing preview skin " + entry.getValue());
                    try (var input = zip.getInputStream(texture)) { sprite = ImageIO.read(input); }
                    spriteOutline = outline(sprite);
                    boolean knife = List.of("karambit", "butterfly", "m9", "skeleton", "default").contains(entry.getKey());
                    float size = (float) y.getDouble("pack-model-scale");
                    var parts = List.of(new Part("body", "$item", new Vector3f(), new Vector3f(size), new Vector3f(0, 0, knife ? -45 : 0), false));
                    for (String suffix : List.of("", "_reverse", "_flourish", "_quick")) {
                        var anim = animation(entry.getKey() + suffix, y.getConfigurationSection("animations." + entry.getKey() + suffix));
                        checkFraming(parts, anim, scale);
                        render(parts, anim, scale, new File(out, "pack_" + entry.getKey() + suffix + ".png"));
                        if (suffix.isEmpty() && List.of("butterfly", "rifle").contains(entry.getKey())) {
                            movie(parts, anim, scale, new File(out, "pack_" + entry.getKey() + ".gif"));
                        }
                    }
                    if (entry.getKey().equals("pistol")) {
                        checkFraming(parts, twirl, scale);
                        render(parts, twirl, scale, new File(out, "pack_deagle_twirl.png"));
                    }
                    handView = true;
                    ANCHOR.set((float) y.getDouble("hand-anchor.right"), (float) y.getDouble("hand-anchor.up"), -(float) y.getDouble("hand-anchor.forward"));
                    for (String suffix : List.of("", "_reverse", "_flourish", "_quick")) {
                        var anim = animation(entry.getKey() + suffix, y.getConfigurationSection("animations." + entry.getKey() + suffix));
                        float handScale = (float) y.getDouble("hand-model-scale");
                        checkFraming(parts, anim, handScale);
                        render(parts, anim, handScale, new File(out, "hand_pack_" + entry.getKey() + suffix + ".png"));
                        if (suffix.isEmpty() && List.of("butterfly", "rifle").contains(entry.getKey())) {
                            movie(parts, anim, handScale, new File(out, "hand_pack_" + entry.getKey() + ".gif"));
                        }
                    }
                    handView = false;
                    ANCHOR.set((float) y.getDouble("anchor.right"), (float) y.getDouble("anchor.up"), -(float) y.getDouble("anchor.forward"));
                }
            }
            sprite = null;
        }
        if (!framingFailures.isEmpty()) throw new IllegalStateException(String.join("\n", framingFailures));
    }

    private static void checkFraming(List<Part> parts, InspectAnimation animation, float scale) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        int clipped = 0;
        for (int tick = 0; tick <= animation.duration(); tick++) {
            boolean outside = false;
            for (Part part : parts) {
                String group = part.group().equals("body") && animation.hasGroup("roll") ? "roll" : part.group();
                Matrix4f matrix = new Matrix4f().scale(scale).mul(pose(animation, group, tick));
                matrix.translate(part.pos()).rotateXYZ((float) Math.toRadians(part.rot().x), (float) Math.toRadians(part.rot().y),
                        (float) Math.toRadians(part.rot().z)).scale(part.size());
                if (part.block()) matrix.translate(-0.5f, -0.5f, -0.5f);
                List<Vector3f> vertices = new ArrayList<>();
                if (sprite != null && part.material().equals("$item")) vertices = spriteOutline;
                else for (int i = 0; i < 8; i++) vertices.add(part.block() ? new Vector3f(i & 1, (i >> 1) & 1, (i >> 2) & 1)
                            : new Vector3f((i & 1) - 0.5f, ((i >> 1) & 1) - 0.5f, (((i >> 2) & 1) - 0.5f) / 16));
                for (Vector3f vertex : vertices) {
                    Vector3f position = camera(matrix.transformPosition(vertex, new Vector3f()).add(ANCHOR));
                    float factor = (float) (135 / Math.tan(FOV / 2)) / Math.max(0.05f, -position.z);
                    float x = 240 + position.x * factor, yy = 135 - position.y * factor;
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, yy); maxY = Math.max(maxY, yy);
                    if (x < 0 || x > 480 || yy < 0 || yy > 270 || position.z >= -0.05f) outside = true;
                }
            }
            if (outside) clipped++;
        }
        if (clipped > 0) framingFailures.add((handView ? "hand " : "eye ") + animation.id() + " clips at " + clipped + " ticks: "
                + minX + "," + minY + " – " + maxX + "," + maxY);
    }

    private static List<Vector3f> outline(BufferedImage image) {
        List<Vector3f> vertices = new ArrayList<>();
        for (int row = 0; row < image.getHeight(); row++) {
            int left = image.getWidth(), right = -1;
            for (int column = 0; column < image.getWidth(); column++) if ((image.getRGB(column, row) >>> 24) > 0) {
                left = Math.min(left, column); right = Math.max(right, column);
            }
            if (right < left) continue;
            for (int x : new int[]{left, right + 1}) for (int yy : new int[]{row, row + 1})
                vertices.add(new Vector3f(x / (float) image.getWidth() - 0.5f, 0.5f - yy / (float) image.getHeight(), 0));
        }
        return vertices;
    }

    private static void render(List<Part> parts, InspectAnimation anim, float scale, File file) throws Exception {
        int frames = 16;
        int columns = 4;
        int cell = 480;
        int height = 270;
        BufferedImage img = new BufferedImage(columns * cell, 4 * height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x30343c));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int f = 0; f < frames; f++) {
            int cx = (f % columns) * cell;
            int cy = (f / columns) * height;
            java.awt.Shape clip = g.getClip();
            g.setClip(cx, cy, cell, height);
            int t = Math.round(anim.duration() * f / (float) (frames - 1));
            drawFrame(parts, anim, t, scale, g, cx, cy, cell, height);
            g.setClip(clip);
        }
        g.dispose();
        ImageIO.write(img, "png", file);
    }

    private static void drawFrame(List<Part> parts, InspectAnimation anim, int t, float scale,
                                  Graphics2D g, int x, int y, int width, int height) {
        if (handView) avatar(g, x + width / 2, y + height / 2, height);
        List<Face> faces = new ArrayList<>();
        for (Part p : parts) {
            String group = p.group().equals("body") && anim.hasGroup("roll") ? "roll" : p.group();
            Matrix4f m = new Matrix4f().scale(scale).mul(pose(anim, group, t));
            m.translate(p.pos()).rotateXYZ((float) Math.toRadians(p.rot().x), (float) Math.toRadians(p.rot().y),
                    (float) Math.toRadians(p.rot().z)).scale(p.size());
            if (p.block()) m.translate(-0.5f, -0.5f, -0.5f);
            if (sprite != null && p.material().equals("$item")) {
                drawSprite(m, g, x + width / 2, y + height / 2, height);
            } else cube(m, color(p.material()), x + width / 2, y + height / 2, height, faces);
        }
        faces.sort(Comparator.comparingDouble(Face::depth));
        for (Face face : faces) {
            g.setColor(face.color());
            g.fillPolygon(face.poly());
            g.setColor(face.color().darker());
            g.setStroke(new BasicStroke(0.6f));
            g.drawPolygon(face.poly());
        }
        g.setColor(new Color(0x8a909a));
        g.drawString(anim.id() + " · t=" + t, x + 10, y + 20);
        g.drawRect(x, y, width - 1, height - 1);
    }

    /** Projects the generated item texture's texels in the same plane as a FIXED item display. */
    private static void drawSprite(Matrix4f matrix, Graphics2D graphics, int ox, int oy, int height) {
        double focal = (height / 2.0) / Math.tan(FOV / 2);
        for (int row = 0; row < sprite.getHeight(); row++) for (int column = 0; column < sprite.getWidth(); column++) {
            int argb = sprite.getRGB(column, row);
            if ((argb >>> 24) == 0) continue;
            Polygon polygon = new Polygon();
            for (int corner = 0; corner < 4; corner++) {
                float px = (column + (corner == 1 || corner == 2 ? 1f : 0)) / sprite.getWidth() - 0.5f;
                float py = 0.5f - (row + (corner >= 2 ? 1f : 0)) / sprite.getHeight();
                Vector3f position = camera(matrix.transformPosition(new Vector3f(px, py, 0)).add(ANCHOR));
                double factor = focal / Math.max(0.05, -position.z);
                polygon.addPoint((int) Math.round(ox + position.x * factor), (int) Math.round(oy - position.y * factor));
            }
            graphics.setColor(new Color(argb, true));
            graphics.fillPolygon(polygon);
        }
    }

    private static void movie(List<Part> parts, InspectAnimation anim, float scale, File file) throws Exception {
        if (!Boolean.parseBoolean(System.getProperty("mccases.filmstrip.movies", "true"))) return;
        var writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (var stream = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(stream);
            writer.prepareWriteSequence(null);
            for (int tick = -8; tick <= anim.duration() + 8; tick++) {
                int t = Math.max(0, Math.min(anim.duration(), tick));
                BufferedImage img = new BufferedImage(480, 270, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(0x30343c));
                g.fillRect(0, 0, 480, 270);
                drawFrame(parts, anim, t, scale, g, 0, 0, 480, 270);
                g.dispose();
                var metadata = writer.getDefaultImageMetadata(new javax.imageio.ImageTypeSpecifier(img), writer.getDefaultWriteParam());
                String format = metadata.getNativeMetadataFormatName();
                var tree = (javax.imageio.metadata.IIOMetadataNode) metadata.getAsTree(format);
                var control = (javax.imageio.metadata.IIOMetadataNode) tree.getElementsByTagName("GraphicControlExtension").item(0);
                control.setAttribute("delayTime", "5");
                control.setAttribute("disposalMethod", "none");
                if (tick == -8) {
                    var extensions = new javax.imageio.metadata.IIOMetadataNode("ApplicationExtensions");
                    var loop = new javax.imageio.metadata.IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID", "NETSCAPE");
                    loop.setAttribute("authenticationCode", "2.0");
                    loop.setUserObject(new byte[]{1, 0, 0});
                    extensions.appendChild(loop);
                    tree.appendChild(extensions);
                }
                metadata.setFromTree(format, tree);
                writer.writeToSequence(new javax.imageio.IIOImage(img, null, metadata), writer.getDefaultWriteParam());
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    private static final int[][] FACES = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};

    /** Anchor of the knife relative to the eye (inspect.yml anchor: right, up, forward). */
    static final Vector3f ANCHOR = new Vector3f(0.28f, -0.24f, -0.6f);
    /** Vertical field of view of the default game camera. */
    static final double FOV = Math.toRadians(70);

    private static void cube(Matrix4f m, Color base, int ox, int oy, int height, List<Face> out) {
        Vector3f[] v = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            v[i] = m.transformPosition(new Vector3f(i & 1, (i >> 1) & 1, (i >> 2) & 1));
        }
        double focal = (height / 2.0) / Math.tan(FOV / 2);
        for (int[] f : FACES) {
            Polygon poly = new Polygon();
            double depth = 0;
            for (int idx : f) {
                Vector3f p = camera(new Vector3f(v[idx]).add(ANCHOR));
                double k = focal / Math.max(0.05, -p.z);
                poly.addPoint((int) Math.round(ox + p.x * k), (int) Math.round(oy - p.y * k));
                depth += p.z + ANCHOR.z;
            }
            Vector3f n = new Vector3f(v[f[1]]).sub(v[f[0]]).cross(new Vector3f(v[f[3]]).sub(v[f[0]])).normalize();
            float light = 0.55f + 0.45f * Math.abs(n.z);
            Color c = new Color(Math.min(255, (int) (base.getRed() * light)), Math.min(255, (int) (base.getGreen() * light)),
                    Math.min(255, (int) (base.getBlue() * light)));
            out.add(new Face(poly, depth / 4, c));
        }
    }

    private static Matrix4f pose(InspectAnimation animation, String group, int tick) {
        Matrix4f pose = animation.groupMatrix(group, tick);
        Matrix4f body = animation.groupMatrix("body", tick);
        float damp = handView ? 0.9f : 0.5f;
        pose.m30(pose.m30() - body.m30() * damp).m31(pose.m31() - body.m31() * damp).m32(pose.m32() - body.m32() * damp);
        return pose;
    }

    /** F5-front camera for the body-hand preview; normal previews use the eye camera. */
    private static Vector3f camera(Vector3f position) {
        if (handView) position.set(-position.x, position.y + 0.62f, -(position.z + 3.5f));
        return position;
    }

    /** Geometric avatar reference, not a client screenshot or the player's downloaded skin. */
    private static void avatar(Graphics2D graphics, int ox, int oy, int height) {
        var faces = new ArrayList<Face>();
        float[][] pieces = {
                {0, 1.55f, 0, .5f, .5f, .5f, 0xcea67f},
                {0, 1.025f, 0, .5f, .65f, .25f, 0x507344},
                {-.34f, 1.025f, 0, .18f, .65f, .25f, 0xcea67f},
                {.34f, 1.025f, 0, .18f, .65f, .25f, 0xcea67f},
                {-.125f, .35f, 0, .25f, .7f, .25f, 0x6d4c36},
                {.125f, .35f, 0, .25f, .7f, .25f, 0x6d4c36}
        };
        for (float[] piece : pieces) {
            Matrix4f matrix = new Matrix4f().translate(piece[0] - ANCHOR.x, piece[1] - 1.62f - ANCHOR.y, piece[2] - ANCHOR.z)
                    .scale(piece[3], piece[4], piece[5]).translate(-.5f, -.5f, -.5f);
            cube(matrix, new Color((int) piece[6]), ox, oy, height, faces);
        }
        faces.sort(Comparator.comparingDouble(Face::depth));
        for (Face face : faces) {
            graphics.setColor(face.color()); graphics.fillPolygon(face.poly());
            graphics.setColor(face.color().darker()); graphics.drawPolygon(face.poly());
        }
    }

    private static Color color(String material) {
        return switch (material) {
            case "$primary" -> new Color(0xc45ab0);
            case "$secondary" -> new Color(0x7a4ad0);
            case "$accent" -> new Color(0xe4ae39);
            case "$handle", "$dark" -> new Color(0x2a2a30);
            case "$edge" -> new Color(0xf0f0f0);
            default -> new Color(0xb0b4ba);
        };
    }

    private static List<Part> parts(ConfigurationSection ps) {
        List<Part> list = new ArrayList<>();
        for (String id : ps.getKeys(false)) {
            ConfigurationSection p = ps.getConfigurationSection(id);
            list.add(new Part(p.getString("group", "body"), p.getString("material", "$primary"), vec(p, "position", 0),
                    vec(p, "size", 0.1f), vec(p, "rotation", 0), !p.getString("type", "block").equalsIgnoreCase("item")));
        }
        return list;
    }

    static InspectAnimation animation(String id, ConfigurationSection a) {
        Map<String, InspectAnimation.Group> groups = new HashMap<>();
        ConfigurationSection gs = a.getConfigurationSection("groups");
        for (String gid : gs.getKeys(false)) {
            ConfigurationSection g = gs.getConfigurationSection(gid);
            List<InspectAnimation.Keyframe> frames = new ArrayList<>();
            for (Map<?, ?> raw : g.getMapList("keyframes")) {
                YamlConfiguration k = new YamlConfiguration();
                raw.forEach((key, value) -> k.set(String.valueOf(key), value));
                Vector3f scale = null;
                if (k.contains("scale")) {
                    float s = (float) k.getDouble("scale", 1);
                    scale = new Vector3f(s, s, s);
                }
                frames.add(new InspectAnimation.Keyframe(k.getInt("ticks", 0), k.contains("move") ? vec(k, "move", 0) : null,
                        k.contains("rotate") ? vec(k, "rotate", 0) : null, scale,
                        InspectAnimation.Ease.valueOf(k.getString("ease", "inout").toUpperCase(Locale.ROOT))));
            }
            groups.put(gid, new InspectAnimation.Group(gid, g.getString("parent"), vec(g, "pivot", 0), frames));
        }
        return new InspectAnimation(id, groups, Map.of(), a.getInt("substeps", 3));
    }

    private static Vector3f vec(ConfigurationSection s, String key, float def) {
        List<Double> v = s.getDoubleList(key);
        return v.size() == 3 ? new Vector3f(v.get(0).floatValue(), v.get(1).floatValue(), v.get(2).floatValue())
                : new Vector3f(def, def, def);
    }
}
