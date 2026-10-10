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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

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

    /** Upgrade generated assets while retaining installed server fonts/artwork. */
    public void extract(dev.plattnericus.cases.catalog.Catalog catalog,
                        dev.plattnericus.cases.render.SkinRenderer renderer) throws IOException {
        try (InputStream in = plugin.getResource(BUNDLED)) {
            if (in == null) throw new IOException("The plugin JAR has no bundled Fusion resource pack: " + BUNDLED);
            byte[] bundled = in.readAllBytes();
            if (FusionPack.customNeedsRefresh(bundled, file.toPath())) {
                var result = export(catalog, renderer);
                if (!result.failures().isEmpty()) throw new IOException("Fusion upgrade has " + result.failures().size() + " incomplete skin exports; previous pack retained");
            } else FusionPack.install(bundled, file.toPath(), settings.get().resourcePack().namespace(), plugin.getPluginMeta().getVersion());
        }
    }

    /** Runtime export follows the same Fusion recipe as Gradle; incomplete exports keep the previous pack. */
    public PackExporter.Result export(dev.plattnericus.cases.catalog.Catalog catalog,
                                      dev.plattnericus.cases.render.SkinRenderer renderer) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        Path generated = Files.createTempFile(file.getParentFile().toPath(), ".mccases-generated-", ".zip");
        Path overlay = Files.createTempFile(file.getParentFile().toPath(), ".mccases-overlay-", ".zip");
        Path combined = Files.createTempFile(file.getParentFile().toPath(), ".mccases-export-", ".zip");
        try {
            var result = PackExporter.export(catalog, renderer, settings.get().resourcePack().namespace(), "Plattnericus", generated.toFile());
            if (!result.failures().isEmpty()) return result;
            try (InputStream in = plugin.getResource(FusionPack.OVERLAY_RESOURCE)) {
                if (in == null) throw new IOException("The plugin JAR has no Fusion overlay; refusing a standard-only export");
                Files.copy(in, overlay, StandardCopyOption.REPLACE_EXISTING);
            }
            FusionPack.merge(generated, overlay, combined, settings.get().resourcePack().namespace(), plugin.getPluginMeta().getVersion());
            // Include server edits made after installation as well as the bundled overlay.
            if (file.isFile()) FusionPack.merge(combined, file.toPath(), overlay, settings.get().resourcePack().namespace(), plugin.getPluginMeta().getVersion());
            else Files.copy(combined, overlay, StandardCopyOption.REPLACE_EXISTING);
            try (InputStream in = plugin.getResource(BUNDLED)) {
                if (in == null) throw new IOException("The plugin JAR has no bundled Fusion pack");
                FusionPack.saveExport(in.readAllBytes(), overlay, file.toPath(), plugin.getPluginMeta().getVersion());
            }
            return result;
        } finally {
            Files.deleteIfExists(generated); Files.deleteIfExists(overlay); Files.deleteIfExists(combined);
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
