package dev.plattnericus.cases.tools;

import com.google.gson.JsonParser;
import dev.plattnericus.cases.pack.ResourcePackInstaller;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Verifies generated pack exports, legacy Fusion cleanup and preservation of server-owned assets. */
public final class ResourcePackInstallerChecks {
    private static final List<String> RETIRED = List.of(
            "assets/minecraft/textures/font/custom/prosse.png",
            "assets/minecraft/textures/font/custom/kirbis.png",
            "assets/minecraft/textures/font/custom/hirsch.png");

    private ResourcePackInstallerChecks() { }

    public static void main(String[] args) throws Exception {
        Path generated = Path.of(args[0]);
        String version = args[1];
        byte[] bundled = Files.readAllBytes(generated);
        Path temp = Files.createTempDirectory("mccases-resourcepack-checks-");
        try {
            Path legacy = temp.resolve("legacy-fusion.zip");
            writeLegacyPack(legacy);
            require(ResourcePackInstaller.customNeedsRefresh(bundled, legacy), "legacy pack with retired glyphs was not scheduled for upgrade");

            Path merged = temp.resolve("merged.zip");
            ResourcePackInstaller.merge(generated, legacy, merged, "mccases", version);
            ResourcePackInstaller.validateFonts(merged);
            try (var zip = new ZipFile(merged.toFile()); var source = new ZipFile(generated.toFile())) {
                var files = ResourcePackInstaller.entries(zip);
                var generatedFiles = ResourcePackInstaller.entries(source);
                for (String retired : RETIRED) require(!files.containsKey(retired), "retired glyph image survived: " + retired);
                require(!files.containsKey("FUSION-MANIFEST.json"), "legacy Fusion manifest survived");
                for (String managed : generatedFiles.keySet()) if (managed.startsWith("assets/mccases/")) {
                    require(java.util.Arrays.equals(bytes(source, managed), bytes(zip, managed)), "generated catalog asset changed: " + managed);
                }
                require(text(zip, "assets/server/textures/custom.txt").equals("server-owned artwork"), "server texture was lost");
                require(java.util.Arrays.equals(bytes(zip, "assets/minecraft/textures/font/custom/server.png"), serverGlyph()), "unrelated server font was lost");
                var font = JsonParser.parseString(text(zip, "assets/minecraft/font/default.json")).getAsJsonObject();
                var providers = font.getAsJsonArray("providers");
                require(providers.size() == 1 && providers.get(0).getAsJsonObject().get("file").getAsString().equals("minecraft:font/custom/server.png"),
                        "legacy provider cleanup removed the wrong fonts or retained a retired glyph");
                var metadata = JsonParser.parseString(text(zip, "pack.mcmeta")).getAsJsonObject().getAsJsonObject("pack");
                require(metadata.get("description").getAsString().equals("MCCases · " + version), "pack version description stale");
                var manifest = JsonParser.parseString(text(zip, ResourcePackInstaller.MANIFEST)).getAsJsonObject();
                require(manifest.get("version").getAsString().equals(version), "manifest version stale");
                require(manifest.get("generatedSha256").getAsString().equals(ResourcePackInstaller.hash(generated)), "generated pack provenance mismatch");
                for (String key : List.of("min_format", "max_format")) require(metadata.has(key), "pack format missing: " + key);
            }
            require(ResourcePackInstaller.customNeedsRefresh(bundled, legacy), "legacy source was incorrectly marked clean");

            Path repeated = temp.resolve("repeated.zip");
            ResourcePackInstaller.merge(generated, legacy, repeated, "mccases", version);
            require(ResourcePackInstaller.hash(repeated).equals(ResourcePackInstaller.hash(merged)), "resource pack merge is not deterministic");

            Path installed = temp.resolve("installed.zip");
            ResourcePackInstaller.install(bundled, installed, "mccases", version);
            require(java.util.Arrays.equals(bundled, Files.readAllBytes(installed)), "first install differs from generated pack");
            String first = ResourcePackInstaller.hash(installed);
            ResourcePackInstaller.install(bundled, installed, "mccases", version);
            require(ResourcePackInstaller.hash(installed).equals(first), "unchanged install rewrote the pack");

            Path exported = temp.resolve("exported.zip");
            ResourcePackInstaller.merge(generated, legacy, exported, "mccases", version);
            ResourcePackInstaller.saveExport(bundled, exported, installed, version);
            require(!ResourcePackInstaller.customNeedsRefresh(bundled, installed), "completed live export was marked dirty");
            ResourcePackInstaller.install(bundled, installed, "mccases", version);
            try (var zip = new ZipFile(installed.toFile())) {
                require(zip.getEntry("assets/server/textures/custom.txt") != null, "custom live export overwritten on restart");
                for (String retired : RETIRED) require(zip.getEntry(retired) == null, "retired glyph returned after restart");
            }

            Path malformed = temp.resolve("malformed-font.zip");
            writeMalformedFontPack(malformed);
            String safe = ResourcePackInstaller.hash(installed);
            try {
                ResourcePackInstaller.merge(generated, malformed, installed, "mccases", version);
                throw new AssertionError("pack with missing font image accepted");
            } catch (IOException expected) {
                require(expected.getMessage().contains("Missing font texture"), "missing font produced an unclear error");
            }
            require(ResourcePackInstaller.hash(installed).equals(safe), "failed merge overwrote the previous working pack");
            try {
                ResourcePackInstaller.merge(generated, legacy, legacy, "mccases", version);
                throw new AssertionError("merge overwrote its input");
            } catch (IOException expected) { require(expected.getMessage().contains("input"), "input overwrite produced unclear error"); }
        } finally {
            try (var paths = Files.walk(temp)) { for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
        System.out.println("PASS: generated pack provenance, deterministic merge, all managed assets, retired Fusion glyph and provider removal, unrelated server asset preservation, atomic failure handling and repeat installation.");
    }

    private static void writeLegacyPack(Path target) throws IOException {
        try (var zip = new ZipOutputStream(Files.newOutputStream(target))) {
            entry(zip, "pack.mcmeta", "{\"pack\":{\"min_format\":[97,1],\"max_format\":99}}".getBytes(StandardCharsets.UTF_8));
            entry(zip, "assets/server/textures/custom.txt", "server-owned artwork".getBytes(StandardCharsets.UTF_8));
            entry(zip, "assets/minecraft/textures/font/custom/server.png", serverGlyph());
            for (String name : RETIRED) entry(zip, name, new byte[]{1, 2, 3});
            entry(zip, "FUSION-MANIFEST.json", "{}".getBytes(StandardCharsets.UTF_8));
            entry(zip, "assets/minecraft/font/default.json", ("{\"providers\":["
                    + "{\"type\":\"bitmap\",\"file\":\"minecraft:font/custom/server.png\",\"ascent\":8,\"height\":8,\"chars\":[\"X\"]},"
                    + "{\"type\":\"bitmap\",\"file\":\"minecraft:font/custom/prosse.png\",\"ascent\":8,\"height\":8,\"chars\":[\"P\"]},"
                    + "{\"type\":\"bitmap\",\"file\":\"minecraft:font/custom/kirbis.png\",\"ascent\":8,\"height\":8,\"chars\":[\"K\"]},"
                    + "{\"type\":\"bitmap\",\"file\":\"minecraft:font/custom/hirsch.png\",\"ascent\":8,\"height\":8,\"chars\":[\"H\"]}]}" ).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void writeMalformedFontPack(Path target) throws IOException {
        try (var zip = new ZipOutputStream(Files.newOutputStream(target))) {
            entry(zip, "pack.mcmeta", "{\"pack\":{\"min_format\":[97,1],\"max_format\":99}}".getBytes(StandardCharsets.UTF_8));
            entry(zip, "assets/minecraft/font/default.json", ("{\"providers\":[{\"type\":\"bitmap\","
                    + "\"file\":\"minecraft:font/custom/missing.png\",\"ascent\":8,\"height\":8,\"chars\":[\"X\"]}]}" ).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void entry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(data); zip.closeEntry();
    }
    private static byte[] serverGlyph() throws IOException {
        var image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) if (x == y || x + y == 7) image.setRGB(x, y, 0xffffffff);
        var bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes); return bytes.toByteArray();
    }
    private static byte[] bytes(ZipFile zip, String name) throws IOException {
        var item = zip.getEntry(name); require(item != null, "missing asset: " + name);
        try (var input = zip.getInputStream(item)) { return input.readAllBytes(); }
    }
    private static String text(ZipFile zip, String name) throws IOException { return new String(bytes(zip, name), StandardCharsets.UTF_8); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
