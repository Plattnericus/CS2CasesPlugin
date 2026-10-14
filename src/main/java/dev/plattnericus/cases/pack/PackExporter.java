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
import org.joml.Quaternionf;
import org.joml.Vector3f;
import dev.plattnericus.cases.inspect.InspectRig;

/**
 * Builds generated skin sprites, inspect meshes and Vanilla 26.3 item shading.
 */
public final class PackExporter {

    public static final int MIN_FORMAT = 97;
    public static final int MAX_FORMAT = 99;
    private static final int SPRITE = 64;
    private static final int SKIN_SPRITE = 128;
    private static final int RIG_INSET = 4;
    private static final int RIG_CONTENT = SKIN_SPRITE - 2 * RIG_INSET;
    private static final double TEXELS_PER_UNIT = SKIN_SPRITE / 16.0;

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
            for (String shader : List.of("item.vsh", "item.fsh")) {
                try (var input = PackExporter.class.getResourceAsStream("/pack-shaders/" + shader)) {
                    if (input == null) throw new IOException("Missing Vanilla 26.3 shader: " + shader);
                    text(zip, "assets/minecraft/shaders/core/" + shader, new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            text(zip, "README.txt", """
                    MCCases resource pack (optional)
                    -----------------------------------
                    Models and textures live in assets/%1$s/.
                    Skin textures are rendered from the active MCCases catalog and bundled weapon maps.
                    First-person and inspect views use the Minecraft player's own arm and skin.
                    Case artwork source and hashes are included in case-art/SOURCE.md.
                    The two assets/minecraft/shaders/core/item.* programs add metal shading
                    for marked faces and require Vanilla Minecraft 26.3.
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
                try { rig(zip, namespace, skin, renderer); }
                catch (TextureException e) { throw new IOException("inspect rig " + skin.id(), e); }
                heldItem(zip, namespace, skin);
                skins++;
            }
            for (CaseDefinition c : catalog.cases().stream().sorted(Comparator.comparing(CaseDefinition::id)).toList()) {
                String path = "case/" + c.id();
                pngBytes(zip, "assets/" + namespace + "/textures/item/" + path + ".png", caseTexture(c.id()));
                model(zip, namespace, path, "minecraft:item/generated");
            }
            for (KeyDefinition k : catalog.keys().stream().sorted(Comparator.comparing(KeyDefinition::id)).toList()) {
                String path = "key/" + k.id();
                png(zip, "assets/" + namespace + "/textures/item/" + path + ".png", keySprite(k.color()));
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
                    faces.append("\"").append(face).append("\":{\"uv\":[").append(u1).append(',').append(v1).append(',').append(u2).append(',').append(v2).append("],\"texture\":\"#skin\"")
                            .append(metal ? ",\"tintindex\":0" : "").append('}');
                }
                elements.add("{\"from\":[" + x1 + ',' + (16-y2) + ',' + (8-thickness/2) + "],\"to\":[" + x2 + ',' + (16-y1) + ',' + (8+thickness/2) + "],\"faces\":{" + faces + "}}");
            }
            text(zip, "assets/" + ns + "/items/" + path + ".json", "{\"model\":" + metalModel(ns, path) + "}");
            text(zip, "assets/" + ns + "/models/item/" + path + ".json", "{\"ambientocclusion\":false," + heldDisplay(skin) + "\"textures\":{\"skin\":\"" + ns + ":item/" + path + "\",\"particle\":\"#skin\"},\"elements\":[" + String.join(",", elements) + "]}");
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
        return "{\"type\":\"minecraft:model\",\"model\":\"" + ns + ":item/" + path + "\",\"tints\":[{\"type\":\"minecraft:constant\",\"value\":16711164}]}";
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
