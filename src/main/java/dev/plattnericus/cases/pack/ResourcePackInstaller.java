package dev.plattnericus.cases.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/** Update generated MCCases assets as a unit and preserve the server's other assets exactly. */
public final class ResourcePackInstaller {
    public static final String MANIFEST = "MCCASES-MANIFEST.json";
    private static final Set<String> RETIRED_ASSETS = Set.of(
            "assets/minecraft/textures/font/custom/prosse.png",
            "assets/minecraft/textures/font/custom/kirbis.png",
            "assets/minecraft/textures/font/custom/hirsch.png",
            "FUSION-MANIFEST.json");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private ResourcePackInstaller() { }

    public static void merge(Path generated, Path customPack, Path target, String namespace, String version) throws IOException {
        if (target.toAbsolutePath().normalize().equals(generated.toAbsolutePath().normalize())
                || target.toAbsolutePath().normalize().equals(customPack.toAbsolutePath().normalize()))
            throw new IOException("Resource pack output must not overwrite either input pack");
        String managed = "assets/" + namespace + "/";
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path pending = Files.createTempFile(target.toAbsolutePath().getParent(), ".mccases-resourcepack-", ".zip");
        try (var current = new ZipFile(generated.toFile()); var base = new ZipFile(customPack.toFile())) {
            var currentEntries = entries(current); var baseEntries = entries(base);
            if (!currentEntries.containsKey("pack.mcmeta")) throw new IOException("Generated pack has no pack.mcmeta");
            var preserved = new TreeMap<String, String>();
            var names = new TreeSet<>(currentEntries.keySet());
            names.removeIf(ResourcePackInstaller::retiredAsset);
            for (String name : baseEntries.keySet()) if (!retiredAsset(name) && !name.startsWith(managed) && !managedRoot(name)) names.add(name);
            names.add(MANIFEST);
            try (var out = new ZipOutputStream(Files.newOutputStream(pending))) {
                out.setLevel(6);
                for (String name : names) {
                    byte[] data;
                    if (name.equals(MANIFEST) || retiredAsset(name)) continue;
                    boolean custom = baseEntries.containsKey(name) && !name.startsWith(managed) && !managedRoot(name);
                    data = read(custom ? base : current, name);
                    if (name.matches("assets/[^/]+/font/.*\\.json")) data = removeRetiredGlyphs(data, name);
                    if (custom) preserved.put(name, hash(data));
                    if (name.equals("pack.mcmeta")) {
                        var metadata = parse(data, name);
                        metadata.getAsJsonObject("pack").addProperty("description", "MCCases · " + version);
                        data = (JSON.toJson(metadata) + "\n").getBytes(StandardCharsets.UTF_8);
                    } else if (name.equals("README.txt")) {
                        data = ("MCCases resource pack · " + version + "\n"
                                + "Catalog-rendered skins and articulated inspect models in assets/" + namespace + "/.\n"
                                + "Case artwork source and checksums are documented in the plugin source.\n"
                                + "Server-owned assets outside assets/" + namespace + "/ are retained during updates.\n"
                                + "Load this ZIP in Minecraft or enable automatic resource-pack distribution.\n"
                                + "Use /cases open <case> 9 for nine simultaneous case openings.\n"
                                + "The MCCases namespace and Vanilla 26.3 item shaders are managed together.\n"
                                + "MCCASES-MANIFEST.json records preserved files and build provenance.\n"
                                + "See LEGAL-NOTICE.txt for project and asset-use information.\n").getBytes(StandardCharsets.UTF_8);
                    }
                    write(out, name, data);
                }
                var manifest = new JsonObject();
                manifest.addProperty("version", version); manifest.addProperty("namespace", namespace);
                manifest.addProperty("generatedSha256", hash(generated));
                manifest.add("preservedAssets", JSON.toJsonTree(preserved));
                write(out, MANIFEST, (JSON.toJson(manifest) + "\n").getBytes(StandardCharsets.UTF_8));
            }
            validateFonts(pending);
            replace(pending, target);
        } catch (IllegalStateException | IllegalArgumentException error) {
            throw new IOException("Invalid resource pack: " + error.getMessage(), error);
        } finally { Files.deleteIfExists(pending); }
    }

    /** Refresh legacy/edited packs without losing server assets; avoid remerging unchanged files. */
    public static void install(byte[] bundled, Path target, String namespace, String version) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path stateFile = target.resolveSibling(".resourcepack-state.json");
        Path legacyHash = target.resolveSibling(".resourcepack-bundled-sha256");
        String bundleHash = hash(bundled), installedHash = Files.isRegularFile(target) ? hash(target) : null;
        JsonObject state = null;
        if (Files.isRegularFile(stateFile)) {
            try { state = JsonParser.parseString(Files.readString(stateFile)).getAsJsonObject(); }
            catch (RuntimeException malformed) { state = null; }
        }
        boolean unchanged = matches(state, bundleHash, installedHash);
        if (unchanged) return;
        String previous = Files.isRegularFile(legacyHash) ? Files.readString(legacyHash).trim() : null;
        if (installedHash == null || installedHash.equals(bundleHash) || installedHash.equals(previous)) {
            if (!bundleHash.equals(installedHash)) atomicWrite(target, bundled);
        } else {
            Path baseline = Files.createTempFile(target.toAbsolutePath().getParent(), ".mccases-bundle-", ".zip");
            Path merged = Files.createTempFile(target.toAbsolutePath().getParent(), ".mccases-install-", ".zip");
            try {
                Files.write(baseline, bundled); merge(baseline, target, merged, namespace, version); replace(merged, target);
            } finally { Files.deleteIfExists(baseline); Files.deleteIfExists(merged); }
        }
        state = new JsonObject(); state.addProperty("bundled", bundleHash); state.addProperty("installed", hash(target));
        atomicWrite(stateFile, (JSON.toJson(state) + "\n").getBytes(StandardCharsets.UTF_8));
        atomicWrite(legacyHash, bundleHash.getBytes(StandardCharsets.UTF_8));
        atomicWrite(target.resolveSibling(".resourcepack-bundled-version"), version.getBytes(StandardCharsets.UTF_8));
        clearLegacyReceipts(target);
    }

    /** Edited/exported packs are regenerated from the actual server catalog, not the bundled catalog. */
    public static boolean customNeedsRefresh(byte[] bundled, Path target) throws IOException {
        if (!Files.isRegularFile(target)) return false;
        if (containsRetiredAssets(target)) return true;
        String bundleHash = hash(bundled), installed = hash(target);
        if (bundleHash.equals(installed) || matches(state(target), bundleHash, installed)) return false;
        Path previous = target.resolveSibling(".resourcepack-bundled-sha256");
        return !Files.isRegularFile(previous) || !Files.readString(previous).trim().equals(installed);
    }

    /** Save a complete server export and tie its receipt to the actual bundled version. */
    public static void saveExport(byte[] bundled, Path complete, Path target, String version) throws IOException {
        var receipt = new JsonObject();
        receipt.addProperty("bundled", hash(bundled)); receipt.addProperty("installed", hash(complete));
        replace(complete, target);
        atomicWrite(target.resolveSibling(".resourcepack-state.json"), (JSON.toJson(receipt) + "\n").getBytes(StandardCharsets.UTF_8));
        atomicWrite(target.resolveSibling(".resourcepack-bundled-sha256"), hash(bundled).getBytes(StandardCharsets.UTF_8));
        atomicWrite(target.resolveSibling(".resourcepack-bundled-version"), version.getBytes(StandardCharsets.UTF_8));
        clearLegacyReceipts(target);
    }

    private static JsonObject state(Path target) throws IOException {
        Path path = target.resolveSibling(".resourcepack-state.json");
        if (!Files.isRegularFile(path)) return null;
        try { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
        catch (RuntimeException malformed) { return null; }
    }
    private static boolean matches(JsonObject state, String bundled, String installed) {
        try { return state != null && state.get("bundled").getAsString().equals(bundled)
                && state.get("installed").getAsString().equals(installed); }
        catch (RuntimeException malformed) { return false; }
    }

    public static Map<String, ZipEntry> entries(ZipFile zip) throws IOException {
        var result = new TreeMap<String, ZipEntry>();
        var iterator = zip.entries();
        while (iterator.hasMoreElements()) {
            var entry = iterator.nextElement(); String name = entry.getName();
            if (name.startsWith("/") || name.contains("\\") || name.contains("\0")
                    || java.util.Arrays.asList(name.split("/")).contains("..")) throw new IOException("Unsafe pack path: " + name);
            if (!entry.isDirectory() && result.putIfAbsent(name, entry) != null) throw new IOException("Duplicate pack asset: " + name);
        }
        return result;
    }

    /** Font images must exist and decode; otherwise report the error before publishing a broken pack. */
    public static void validateFonts(Path pack) throws IOException {
        try (var zip = new ZipFile(pack.toFile())) {
            for (String name : entries(zip).keySet()) if (name.matches("assets/[^/]+/font/.*\\.json")) {
                var font = parse(read(zip, name), name);
                if (!font.has("providers")) throw new IOException("Font has no providers: " + name);
                for (var provider : font.getAsJsonArray("providers")) {
                    var object = provider.getAsJsonObject();
                    if (!object.get("type").getAsString().equals("bitmap")) continue;
                    String id = object.get("file").getAsString();
                    if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\.png") || id.contains("..")) throw new IOException("Invalid bitmap font path: " + id);
                    String path = "assets/" + id.replace(":", "/textures/");
                    if (zip.getEntry(path) == null) throw new IOException("Missing font texture: " + path);
                    try (var input = zip.getInputStream(zip.getEntry(path))) {
                        var image = ImageIO.read(input); var chars = object.getAsJsonArray("chars");
                        if (image == null || chars.isEmpty()) throw new IOException("Invalid font texture/grid: " + path);
                        int columns = chars.get(0).getAsString().codePointCount(0, chars.get(0).getAsString().length());
                        if (columns == 0 || image.getWidth() % columns != 0 || image.getHeight() % chars.size() != 0)
                            throw new IOException("Font image does not match its glyph grid: " + path);
                        for (var row : chars) if (row.getAsString().codePointCount(0, row.getAsString().length()) != columns)
                            throw new IOException("Inconsistent font glyph rows: " + name);
                        if (object.has("height") && object.get("height").getAsInt() <= 0
                                || object.get("ascent").getAsInt() > (object.has("height") ? object.get("height").getAsInt() : 8))
                            throw new IOException("Invalid font height/ascent: " + name);
                    }
                }
            }
        } catch (RuntimeException invalid) { throw new IOException("Invalid font configuration: " + invalid.getMessage(), invalid); }
    }

    private static boolean retiredAsset(String name) { return RETIRED_ASSETS.contains(name); }

    private static boolean containsRetiredAssets(Path pack) throws IOException {
        try (var zip = new ZipFile(pack.toFile())) {
            for (String name : entries(zip).keySet()) if (retiredAsset(name)) return true;
            for (String name : entries(zip).keySet()) if (name.matches("assets/[^/]+/font/.*\\.json")) {
                var font = zip.getEntry(name);
                byte[] data;
                try (var input = zip.getInputStream(font)) { data = input.readAllBytes(); }
                if (!java.util.Arrays.equals(data, removeRetiredGlyphs(data, name))) return true;
            }
            return false;
        }
    }

    private static byte[] removeRetiredGlyphs(byte[] data, String name) throws IOException {
        var font = parse(data, name);
        if (!font.has("providers") || !font.get("providers").isJsonArray()) return data;
        JsonArray filtered = new JsonArray();
        for (var provider : font.getAsJsonArray("providers")) {
            var object = provider.getAsJsonObject();
            boolean retired = object.has("type") && object.get("type").getAsString().equals("bitmap")
                    && object.has("file") && RETIRED_ASSETS.stream().filter(path -> path.startsWith("assets/minecraft/textures/font/custom/"))
                    .anyMatch(path -> object.get("file").getAsString().equals("minecraft:font/custom/" + path.substring(path.lastIndexOf('/') + 1)));
            if (!retired) filtered.add(provider);
        }
        if (filtered.size() == font.getAsJsonArray("providers").size()) return data;
        font.add("providers", filtered);
        return (JSON.toJson(font) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static void clearLegacyReceipts(Path target) throws IOException {
        Files.deleteIfExists(target.resolveSibling(".fusion-state.json"));
        Files.deleteIfExists(target.resolveSibling(".bundled-sha256"));
        Files.deleteIfExists(target.resolveSibling(".bundled-version"));
    }

    private static boolean managedRoot(String name) {
        return name.equals("pack.mcmeta") || name.equals("README.txt") || name.equals("BASE-PACK-README.txt")
                || name.equals("LEGAL-NOTICE.txt") || name.equals(MANIFEST)
                || name.equals("assets/minecraft/shaders/core/item.vsh") || name.equals("assets/minecraft/shaders/core/item.fsh");
    }
    private static JsonObject parse(byte[] data, String name) throws IOException {
        try { return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException invalid) { throw new IOException("Invalid JSON: " + name, invalid); }
    }
    private static byte[] read(ZipFile zip, String name) throws IOException {
        var entry = zip.getEntry(name);
        if (entry == null) throw new IOException("Missing pack asset: " + name);
        try (var input = zip.getInputStream(entry)) {
            byte[] data = input.readAllBytes();
            var crc = new java.util.zip.CRC32(); crc.update(data);
            if (entry.getCrc() != -1 && crc.getValue() != entry.getCrc()) throw new IOException("Corrupt pack asset: " + name);
            return data;
        }
    }
    private static void write(ZipOutputStream out, String name, byte[] data) throws IOException {
        var entry = new ZipEntry(name); entry.setTime(0); out.putNextEntry(entry); out.write(data); out.closeEntry();
    }
    public static String hash(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            var digest = digest(); var buffer = new byte[65536];
            for (int read; (read = input.read(buffer)) != -1;) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        }
    }
    private static String hash(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void atomicWrite(Path target, byte[] data) throws IOException {
        Path pending = Files.createTempFile(target.toAbsolutePath().getParent(), ".mccases-state-", ".tmp");
        try { Files.write(pending, data); replace(pending, target); }
        finally { Files.deleteIfExists(pending); }
    }
    private static void replace(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
