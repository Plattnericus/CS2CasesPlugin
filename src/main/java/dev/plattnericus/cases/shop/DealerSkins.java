package dev.plattnericus.cases.shop;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * Dealer skins given as player names are looked up once at Mojang and stored in
 * dealer-skins.yml as texture data. Later starts use the stored skin, so a new skin on that
 * account does not change the dealer and no lookup is repeated.
 */
public final class DealerSkins {

    private static final String UUID_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private final Plugin plugin;
    private final File file;
    private final Set<String> pending = new HashSet<>();
    private YamlConfiguration stored = new YamlConfiguration();

    public DealerSkins(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "dealer-skins.yml");
        if (file.isFile()) stored = YamlConfiguration.loadConfiguration(file);
    }

    /** Stored skin of that name, or null when it has not been looked up yet. */
    public ResolvableProfile stored(String name) {
        String texture = stored.getString(key(name) + ".texture");
        return texture == null ? null : textured(texture, stored.getString(key(name) + ".signature"));
    }

    public static ResolvableProfile textured(String texture, String signature) {
        return ResolvableProfile.resolvableProfile()
                .addProperty(new ProfileProperty("textures", texture, signature)).build();
    }

    /** Looks the name up once in the background and runs {@code done} on the main thread after saving. */
    public void fetch(String name, Runnable done) {
        if (!pending.add(key(name))) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String[] skin = null;
            try {
                skin = download(name);
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().warning("shop.yml: skin lookup for " + name + " failed: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String[] result = skin;
            Bukkit.getScheduler().runTask(plugin, () -> {
                pending.remove(key(name));
                if (result != null) save(name, result[0], result[1], done);
            });
        });
    }

    /** Signed texture value and signature of the account, or null when the name has no account. */
    private String[] download(String name) throws IOException, InterruptedException {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        JsonObject account = json(http, UUID_URL + name);
        if (account == null || !account.has("id")) {
            plugin.getLogger().warning("shop.yml: no player named " + name);
            return null;
        }
        JsonObject profile = json(http, PROFILE_URL + account.get("id").getAsString() + "?unsigned=false");
        if (profile == null || !profile.has("properties")) return null;
        for (JsonElement element : profile.getAsJsonArray("properties")) {
            JsonObject property = element.getAsJsonObject();
            if (property.get("name").getAsString().equals("textures")) {
                return new String[]{property.get("value").getAsString(),
                        property.has("signature") ? property.get("signature").getAsString() : null};
            }
        }
        return null;
    }

    private static JsonObject json(HttpClient http, String url) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8)).header("Accept", "application/json").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        // 204 and 404 mean that no such account exists; anything else (429 = rate limit) is retried on the next start
        if (response.statusCode() == 204 || response.statusCode() == 404) return null;
        if (response.statusCode() != 200 || response.body().isBlank()) throw new IOException("HTTP " + response.statusCode());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private void save(String name, String texture, String signature, Runnable done) {
        stored.set(key(name) + ".texture", texture);
        stored.set(key(name) + ".signature", signature);
        try {
            stored.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save dealer-skins.yml", e);
        }
        done.run();
    }

    private static String key(String name) {
        return "skins." + name.toLowerCase(Locale.ROOT);
    }
}
