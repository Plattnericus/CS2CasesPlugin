package dev.plattnericus.cases.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.plattnericus.cases.config.PluginSettings;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.zip.ZipInputStream;

/**
 * The resource pack that ships inside the plugin jar, and (optional, off by default) a tiny HTTP
 * server that hands it to players on join. The server answers exactly one path with the pack file;
 * everything else is a 404.
 */
public final class PackDistribution implements Listener {

    public static final String FILE_NAME = "MCCases-ResourcePack.zip";
    private static final String BUNDLED = "pack/" + FILE_NAME;
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("mccases:resourcepack".getBytes(StandardCharsets.UTF_8));

    private final JavaPlugin plugin;
    private final Supplier<PluginSettings> settings;
    private final File file;
    private HttpServer server;
    private ExecutorService executor;
    private volatile byte[] bytes;
    private volatile String sha1;

    public PackDistribution(JavaPlugin plugin, Supplier<PluginSettings> settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.file = new File(plugin.getDataFolder(), "resourcepack/" + FILE_NAME);
    }

    public File file() {
        return file;
    }

    /** Updates an unchanged bundled pack by content, while keeping exported or edited packs. */
    public void extract() throws IOException {
        File marker = new File(file.getParentFile(), ".bundled-version");
        File contentMarker = new File(file.getParentFile(), ".bundled-sha256");
        String version = plugin.getPluginMeta().getVersion();
        byte[] bundled;
        try (InputStream in = plugin.getResource(BUNDLED)) {
            if (in == null) {
                plugin.getLogger().warning("The plugin jar contains no resource pack (" + BUNDLED + ").");
                return;
            }
            bundled = in.readAllBytes();
        }
        String bundledHash = digest(bundled);
        boolean replace = true;
        if (file.isFile()) {
            byte[] installed = Files.readAllBytes(file.toPath());
            String installedHash = digest(installed);
            if (installedHash.equals(bundledHash)) replace = false;
            else {
                String previousHash = contentMarker.isFile()
                        ? Files.readString(contentMarker.toPath(), StandardCharsets.UTF_8).trim() : null;
                boolean unchanged = previousHash != null ? installedHash.equals(previousHash)
                        : marker.isFile() && isLegacyBundle(installed, bundled);
                if (!unchanged) return;
            }
        }
        Files.createDirectories(file.getParentFile().toPath());
        if (replace) replaceFile(file.toPath(), bundled);
        Files.writeString(marker.toPath(), version, StandardCharsets.UTF_8);
        Files.writeString(contentMarker.toPath(), bundledHash, StandardCharsets.UTF_8);
    }

    private static String digest(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Old installations only recorded a version. Their unchanged pack can be identified by its
     * entries before the trade highlights were added; ZIP timestamps do not affect that comparison.
     * Earlier exports could use a different bundled knife sprite as their logo. Other edits are
     * treated as an intentional export and kept.
     */
    private static boolean isLegacyBundle(byte[] installed, byte[] bundled) throws IOException {
        Map<String, String> oldEntries = entries(installed), newEntries = entries(bundled);
        if (oldEntries.isEmpty() || !oldEntries.containsKey("pack.mcmeta")) return false;
        String oldLogo = oldEntries.get("pack.png"), newLogo = newEntries.get("pack.png");
        if (oldLogo != null || newLogo != null) {
            if (oldLogo == null || newLogo == null || !isSkinSprite(oldEntries, oldLogo)
                    || !isSkinSprite(newEntries, newLogo)) return false;
        }
        oldEntries.remove("pack.png");
        newEntries.remove("pack.png");
        newEntries.keySet().removeIf(name -> name.contains("/trade/selected/"));
        return oldEntries.equals(newEntries);
    }

    private static boolean isSkinSprite(Map<String, String> entries, String hash) {
        return entries.entrySet().stream().anyMatch(entry -> entry.getKey()
                .matches("assets/[^/]+/textures/item/skin/[^/]+\\.png") && entry.getValue().equals(hash));
    }

    private static Map<String, String> entries(byte[] data) throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.isDirectory()) {
                    if (entries.put(entry.getName(), digest(zip.readAllBytes())) != null) return Map.of();
                }
            }
        }
        return entries;
    }

    private static void replaceFile(Path target, byte[] data) throws IOException {
        Path pending = Files.createTempFile(target.getParent(), ".mccases-pack-", ".tmp");
        try {
            Files.write(pending, data);
            try {
                Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(pending);
        }
    }

    /** Starts or stops the web server according to the current settings. */
    public void apply() {
        stop();
        PluginSettings.Distribution cfg = settings.get().resourcePack().distribution();
        if (!cfg.enabled()) {
            return;
        }
        try {
            reloadFile();
            server = HttpServer.create(new InetSocketAddress(cfg.bindAddress(), cfg.port()), 16);
            executor = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "MCCases-PackHost");
                t.setDaemon(true);
                return t;
            });
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
            plugin.getLogger().info("Serving the resource pack on port " + cfg.port() + " as " + url());
        } catch (IOException | IllegalArgumentException e) {
            plugin.getLogger().warning("Resource pack web server could not start on " + cfg.bindAddress() + ":" + cfg.port()
                    + " (" + e.getMessage() + "). Distribution is disabled until the next reload.");
            stop();
        }
    }

    /** Re-reads the pack file (after an export) and recomputes its hash. */
    public void reloadFile() throws IOException {
        byte[] data = Files.readAllBytes(file.toPath());
        try {
            sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        bytes = data;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            byte[] data = bytes;
            boolean match = exchange.getRequestURI().getPath().equals("/" + FILE_NAME);
            if (!match || data == null || !exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(data);
            }
        }
    }

    public boolean running() {
        return server != null;
    }

    /** Public URL including the hash, so clients never use a stale cached copy. */
    public String url() {
        String base = settings.get().resourcePack().distribution().publicUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + FILE_NAME + "?v=" + sha1;
    }

    public void send(Player player) {
        if (!running() || sha1 == null) {
            return;
        }
        PluginSettings.Distribution cfg = settings.get().resourcePack().distribution();
        try {
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(url()), sha1);
            ResourcePackRequest.Builder request = ResourcePackRequest.resourcePackRequest()
                    .packs(info)
                    .required(cfg.required())
                    .replace(false);
            if (!cfg.prompt().isBlank()) {
                request.prompt(Text.mm(cfg.prompt()));
            }
            player.sendResourcePacks(request.build());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("resource-pack.distribution.public-url is not a valid URL: " + cfg.publicUrl());
        }
    }

    public void sendAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            send(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        send(event.getPlayer());
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
