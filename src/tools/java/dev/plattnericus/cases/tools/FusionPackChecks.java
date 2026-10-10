package dev.plattnericus.cases.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.plattnericus.cases.pack.FusionPack;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Checks the real overlay, complete resource references, and installation/export failure recovery. */
public final class FusionPackChecks {
    private FusionPackChecks() { }
    public static void main(String[] args) throws Exception {
        Path generated = Path.of(args[0]), overlay = Path.of(args[1]), combined = Path.of(args[2]); String version = args[3];
        int managed = 0, preserved = 0, references = 0;
        try (var current = new ZipFile(generated.toFile()); var base = new ZipFile(overlay.toFile()); var fusion = new ZipFile(combined.toFile())) {
            var files = FusionPack.entries(fusion);
            for (String name : FusionPack.entries(current).keySet()) if (name.startsWith("assets/mccases/")) {
                require(Arrays.equals(bytes(current, name), bytes(fusion, name)), "stale generated asset: " + name); managed++;
            }
            for (String shader : new String[]{"item.vsh", "item.fsh"}) require(Arrays.equals(
                    bytes(current,"assets/minecraft/shaders/core/" + shader), bytes(fusion,"assets/minecraft/shaders/core/" + shader)), "stale item shader " + shader);
            for (String name : FusionPack.entries(base).keySet()) {
                require(Arrays.equals(bytes(base, name), bytes(fusion, name)), "changed custom asset: " + name); preserved++;
            }
            var source = JsonParser.parseString(text(base, "SOURCE.json")).getAsJsonObject().getAsJsonObject("assets");
            for (var entry : source.entrySet()) {
                byte[] data = bytes(base, entry.getKey());
                String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
                require(hash.equals(entry.getValue().getAsJsonObject().get("sha256").getAsString()), "source asset checksum changed: " + entry.getKey());
            }
            var manifest = JsonParser.parseString(text(fusion, FusionPack.MANIFEST)).getAsJsonObject();
            require(manifest.get("version").getAsString().equals(version), "stale Fusion version");
            require(manifest.get("generatedSha256").getAsString().equals(FusionPack.hash(generated)), "Fusion build provenance");
            var metadata = JsonParser.parseString(text(fusion, "pack.mcmeta")).getAsJsonObject();
            var standard = JsonParser.parseString(text(current, "pack.mcmeta")).getAsJsonObject();
            for (String key : new String[]{"min_format", "max_format"}) require(metadata.getAsJsonObject("pack").get(key).equals(standard.getAsJsonObject("pack").get(key)), "obsolete pack format");
            for (String name : files.keySet()) if (name.endsWith(".json")) references += references(JsonParser.parseString(text(fusion, name)), fusion);
        }
        FusionPack.validateFonts(combined);
        var temp = Files.createTempDirectory("mccases-fusion-checks-");
        try {
            Path target = temp.resolve("installed.zip"); byte[] bundled = Files.readAllBytes(combined);
            FusionPack.install(bundled, target, "mccases", version);
            require(Arrays.equals(bundled, Files.readAllBytes(target)), "first install differs from bundle");
            String first = FusionPack.hash(target); FusionPack.install(bundled, target, "mccases", version);
            require(FusionPack.hash(target).equals(first), "unchanged restart rewrote pack");

            Path extra = temp.resolve("server-edited.zip");
            copy(combined, extra, null, "assets/server/textures/custom.txt", "server-owned artwork".getBytes(StandardCharsets.UTF_8));
            Files.copy(extra, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            require(FusionPack.customNeedsRefresh(bundled, target), "server edits were not detected");
            FusionPack.install(bundled, target, "mccases", version);
            try (var z = new ZipFile(target.toFile())) { require(text(z, "assets/server/textures/custom.txt").equals("server-owned artwork"), "server artwork lost on upgrade"); }
            String custom = FusionPack.hash(target); FusionPack.install(bundled, target, "mccases", version);
            require(FusionPack.hash(target).equals(custom), "custom pack changed on repeat restart");
            Files.writeString(target.resolveSibling(".fusion-state.json"), "{\"bundled\": [], \"installed\": false}");
            FusionPack.install(bundled, target, "mccases", version);
            try (var z = new ZipFile(target.toFile())) { require(z.getEntry("assets/server/textures/custom.txt") != null, "malformed receipt lost artwork"); }

            // A server export may include a custom catalog: its receipt must preserve those models on restart.
            Path serverExport = temp.resolve("export.zip");
            copy(target, serverExport, null, "assets/mccases/models/item/server_skin.json", "{\"parent\":\"minecraft:item/generated\"}".getBytes(StandardCharsets.UTF_8));
            FusionPack.saveExport(bundled, serverExport, target, version);
            require(!FusionPack.customNeedsRefresh(bundled, target), "valid exported catalog unnecessarily rebuilt");
            FusionPack.install(bundled, target, "mccases", version);
            try (var z = new ZipFile(target.toFile())) { require(z.getEntry("assets/mccases/models/item/server_skin.json") != null, "exported custom catalog overwritten by stock catalog"); }

            Path bad = temp.resolve("missing-font.zip");
            copy(overlay, bad, "assets/minecraft/textures/font/custom/logo.png", null, null);
            String safe = FusionPack.hash(target);
            try { FusionPack.merge(generated, bad, target, "mccases", version); throw new AssertionError("missing font texture accepted"); }
            catch (IOException expected) { require(expected.getMessage().contains("Missing font texture"), "missing asset has no useful error"); }
            require(FusionPack.hash(target).equals(safe), "failed merge overwrote working pack");
            try { FusionPack.merge(generated, overlay, overlay, "mccases", version); throw new AssertionError("input overwritten"); }
            catch (IOException expected) { require(expected.getMessage().contains("input"), "input protection error"); }
            Path stale = temp.resolve("stale.zip"), refreshed = temp.resolve("refreshed.zip");
            copy(overlay, stale, null, "assets/mccases/models/item/obsolete.json", "{}".getBytes(StandardCharsets.UTF_8));
            FusionPack.merge(generated, stale, refreshed, "mccases", version);
            try (var z = new ZipFile(refreshed.toFile())) { require(z.getEntry("assets/mccases/models/item/obsolete.json") == null, "obsolete managed model survives merge"); }
            Path repeat = temp.resolve("repeat.zip"); FusionPack.merge(generated, overlay, repeat, "mccases", version);
            require(FusionPack.hash(repeat).equals(FusionPack.hash(combined)), "Fusion merge is not deterministic");
            Path oldShader = temp.resolve("old-shader.zip"), shaderRefresh = temp.resolve("shader-refresh.zip");
            String shaderPath = "assets/minecraft/shaders/core/item.fsh";
            copy(overlay,oldShader,null,shaderPath,"obsolete shader".getBytes(StandardCharsets.UTF_8));
            FusionPack.merge(generated,oldShader,shaderRefresh,"mccases",version);
            try (var source = new ZipFile(generated.toFile()); var refreshedShader = new ZipFile(shaderRefresh.toFile())) {
                require(Arrays.equals(bytes(source,shaderPath),bytes(refreshedShader,shaderPath)), "old server shader survived model upgrade");
            }
        } finally { try (var paths = Files.walk(temp)) { for (var p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); } }
        System.out.println("PASS FUSION: " + managed + " current managed assets, " + preserved + " exact overlay files, " + references
                + " resolved resource references; seven bitmap fonts, deterministic output, first/old/edited installs, repeated restart, custom catalog receipts, missing-font failure and atomic preservation.");
    }
    private static int references(JsonElement element, ZipFile zip) {
        int count = 0;
        if (element.isJsonArray()) for (var child : element.getAsJsonArray()) count += references(child, zip);
        else if (element.isJsonObject()) for (var entry : element.getAsJsonObject().entrySet()) {
            var value = entry.getValue();
            if ((entry.getKey().equals("model") || entry.getKey().equals("parent")) && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String id = value.getAsString();
                if (id.startsWith("mccases:")) { require(zip.getEntry("assets/mccases/models/" + id.substring(8) + ".json") != null, "missing model reference " + id); count++; }
            } else if (entry.getKey().equals("textures") && value.isJsonObject()) for (var texture : value.getAsJsonObject().entrySet()) {
                String id = texture.getValue().getAsString();
                if (id.startsWith("mccases:")) { require(zip.getEntry("assets/mccases/textures/" + id.substring(8) + ".png") != null, "missing texture reference " + id); count++; }
            }
            count += references(value, zip);
        }
        return count;
    }
    private static String text(ZipFile zip, String name) throws IOException {
        return new String(bytes(zip, name), StandardCharsets.UTF_8);
    }
    private static byte[] bytes(ZipFile zip, String name) throws IOException {
        var entry = zip.getEntry(name); require(entry != null, "missing asset " + name);
        try (var input = zip.getInputStream(entry)) { return input.readAllBytes(); }
    }
    private static void copy(Path source, Path target, String remove, String add, byte[] bytes) throws IOException {
        try (var input = new ZipFile(source.toFile()); var out = new ZipOutputStream(Files.newOutputStream(target))) {
            for (String name : FusionPack.entries(input).keySet()) if (!name.equals(remove) && !name.equals(add)) {
                out.putNextEntry(new ZipEntry(name));
                try (var data = input.getInputStream(input.getEntry(name))) { data.transferTo(out); }
                out.closeEntry();
            }
            if (add != null) { out.putNextEntry(new ZipEntry(add)); out.write(bytes); out.closeEntry(); }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
