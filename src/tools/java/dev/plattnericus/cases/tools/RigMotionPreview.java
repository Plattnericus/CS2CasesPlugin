package dev.plattnericus.cases.tools;

import com.google.gson.*;
import dev.plattnericus.cases.catalog.*;
import dev.plattnericus.cases.inspect.*;
import dev.plattnericus.cases.render.TextureStore;
import org.joml.Vector3f;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.zip.ZipFile;

/** Software filmstrips of the exported articulated models; these are not client screenshots. */
public final class RigMotionPreview {
    private RigMotionPreview() { }
    private record Mesh(ModelPart part, BufferedImage texture, JsonArray elements) { }
    private record Face(Polygon shape, double depth, BufferedImage texture, double[] uv, int[] x, int[] y) { }
    public static void main(String[] args) throws Exception {
        File root = new File(args[0]), output = new File(args[2]); output.mkdirs();
        var catalog = new CatalogLoader(new File(root, "catalog"), new TextureStore(new File(root, "textures"))).load(1).catalog();
        var models = new InspectModels(new File(root, "inspect.yml"), warning -> { throw new AssertionError(warning); });
        try (var zip = new ZipFile(args[1])) {
            for (var weapon : catalog.weapons().stream().sorted(Comparator.comparing(WeaponType::id)).toList()) {
                var skin = catalog.skins().stream().filter(s -> s.weapon().id().equals(weapon.id()))
                        .sorted(Comparator.comparing(SkinDefinition::id)).findFirst().orElseThrow();
                var meshes = new ArrayList<Mesh>();
                for (var part : InspectRig.packParts(weapon, models.packModelScale())) {
                    String path = "inspect/" + skin.id() + "/" + part.material().substring(5);
                    try (var png = zip.getInputStream(zip.getEntry("assets/mccases/textures/item/" + path + ".png"));
                         var json = zip.getInputStream(zip.getEntry("assets/mccases/models/item/" + path + ".json"))) {
                        meshes.add(new Mesh(part, ImageIO.read(png), JsonParser.parseReader(new InputStreamReader(json, java.nio.charset.StandardCharsets.UTF_8))
                                .getAsJsonObject().getAsJsonArray("elements")));
                    }
                }
                int width = 176, height = 132, header = 24;
                var image = new BufferedImage(width * 8, (height + header) * 4, BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics(); graphics.setColor(new Color(0x20242b)); graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                int row = 0;
                for (String id : models.pool(weapon.id())) {
                    var animation = models.animation(id);
                    graphics.setColor(Color.WHITE); graphics.drawString(id + " · software model preview", 8, row * (height + header) + 17);
                    for (int column = 0; column < 8; column++) {
                        int tick = Math.round(animation.duration() * column / 7f), x = column * width, y = row * (height + header) + header;
                        var clip = graphics.getClip(); graphics.setClip(x, y, width, height);
                        draw(graphics, meshes, animation, tick, models, InspectRig.paired(weapon), x, y, width, height);
                        graphics.setClip(clip);
                    }
                    row++;
                }
                graphics.dispose(); ImageIO.write(image, "png", new File(output, weapon.id() + ".png"));
            }
        }
        System.out.println("Rendered all 63 articulated inspect filmstrips / 252 variants from the exported pack into " + output + ". Software evidence, not Minecraft captures.");
    }
    private static void draw(Graphics2D graphics, List<Mesh> meshes, InspectAnimation animation, int tick,
                             InspectModels models, boolean paired, int ox, int oy, int width, int height) {
        var faces = new ArrayList<Face>();
        double focal = height / 2.0 / Math.tan(Math.toRadians(70) / 2);
        for (var mesh : meshes) {
            var matrix = InspectTransform.at(animation, mesh.part(), tick, models.modelScale(), false, false);
            for (var raw : mesh.elements()) {
                var element = raw.getAsJsonObject(); var from = vec(element.getAsJsonArray("from")); var to = vec(element.getAsJsonArray("to"));
                for (var face : element.getAsJsonObject("faces").entrySet()) {
                    var corners = corners(face.getKey(), from, to); int[] x = new int[4], y = new int[4]; double depth = 0;
                    for (int i = 0; i < 4; i++) {
                        var point = matrix.transformPosition(corners[i].div(16).sub(.5f,.5f,.5f));
                        double z = models.anchor().forward() + point.z;
                        x[i] = (int)Math.round(ox + width / 2.0 + ((paired ? 0 : models.anchor().right()) - point.x) * focal / z);
                        y[i] = (int)Math.round(oy + height / 2.0 - (models.anchor().up() + point.y) * focal / z); depth += z;
                    }
                    var uv = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                    faces.add(new Face(new Polygon(x,y,4), depth / 4, mesh.texture(), new double[]{uv.get(0).getAsDouble()*4,
                            uv.get(1).getAsDouble()*4, uv.get(2).getAsDouble()*4, uv.get(3).getAsDouble()*4}, x, y));
                }
            }
        }
        faces.sort(Comparator.comparingDouble(Face::depth).reversed());
        for (var face : faces) {
            var uv = face.uv(); double du = uv[2]-uv[0], dv = uv[3]-uv[1];
            if (du == 0 || dv == 0) {
                graphics.setColor(new Color(face.texture().getRGB((int)((uv[0]+uv[2])/2),(int)((uv[1]+uv[3])/2)),true)); graphics.fillPolygon(face.shape());
            } else {
                double a = (face.x()[3]-face.x()[0])/du, b = (face.x()[1]-face.x()[0])/dv;
                double c = (face.y()[3]-face.y()[0])/du, d = (face.y()[1]-face.y()[0])/dv;
                var transform = new AffineTransform(a,c,b,d,face.x()[0]-a*uv[0]-b*uv[1],face.y()[0]-c*uv[0]-d*uv[1]);
                var clip = graphics.getClip(); graphics.clip(face.shape()); graphics.drawImage(face.texture(),transform,null); graphics.setClip(clip);
            }
        }
    }
    private static Vector3f vec(JsonArray a) { return new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()); }
    private static Vector3f[] corners(String face, Vector3f a, Vector3f b) {
        return switch (face) {
            case "north" -> new Vector3f[]{new Vector3f(b.x,b.y,a.z),new Vector3f(b.x,a.y,a.z),new Vector3f(a.x,a.y,a.z),new Vector3f(a.x,b.y,a.z)};
            case "south" -> new Vector3f[]{new Vector3f(a.x,b.y,b.z),new Vector3f(a.x,a.y,b.z),new Vector3f(b.x,a.y,b.z),new Vector3f(b.x,b.y,b.z)};
            case "east" -> new Vector3f[]{new Vector3f(b.x,b.y,b.z),new Vector3f(b.x,a.y,b.z),new Vector3f(b.x,a.y,a.z),new Vector3f(b.x,b.y,a.z)};
            case "west" -> new Vector3f[]{new Vector3f(a.x,b.y,a.z),new Vector3f(a.x,a.y,a.z),new Vector3f(a.x,a.y,b.z),new Vector3f(a.x,b.y,b.z)};
            case "up" -> new Vector3f[]{new Vector3f(a.x,b.y,a.z),new Vector3f(a.x,b.y,b.z),new Vector3f(b.x,b.y,b.z),new Vector3f(b.x,b.y,a.z)};
            case "down" -> new Vector3f[]{new Vector3f(a.x,a.y,b.z),new Vector3f(a.x,a.y,a.z),new Vector3f(b.x,a.y,a.z),new Vector3f(b.x,a.y,b.z)};
            default -> throw new IllegalArgumentException(face);
        };
    }
}
