package dev.plattnericus.cases.config;

import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * All player-facing text (chat, GUI titles, item lore) from messages_<language>.yml in MiniMessage format.
 * Missing keys fall back to the bundled default file, so updates never show raw keys.
 */
public final class Messages {

    private final YamlConfiguration file;
    private final YamlConfiguration fallback;
    private final YamlConfiguration english;
    private final TagResolver prefix;
    private Map<String, Messages> languages = Map.of();
    private String language = "en";
    private boolean clientLanguage;

    /**
     * @param path            the server's (editable) language file
     * @param bundledLanguage the bundled default of the same language (fills keys added by updates)
     * @param bundledEnglish  last fallback for languages that miss a key
     */
    public Messages(File path, InputStream bundledLanguage, InputStream bundledEnglish) {
        this.file = YamlConfiguration.loadConfiguration(path);
        this.fallback = load(bundledLanguage);
        this.english = load(bundledEnglish);
        this.prefix = Placeholder.parsed("prefix", raw("prefix"));
    }

    /** Loads once on enable/reload; locale lookups never read files on the server thread. */
    public static Messages load(JavaPlugin plugin, YamlConfiguration config, Consumer<String> warn) {
        return load(plugin.getDataFolder(), plugin::getResource, config, warn);
    }

    public static Messages load(File folder, java.util.function.Function<String, InputStream> resource,
                                YamlConfiguration config, Consumer<String> warn) {
        Map<String, Messages> available = new LinkedHashMap<>();
        File[] files = folder.listFiles((dir, name) -> name.matches("messages_[a-z]{2,8}(?:_[a-z0-9]{2,8})*\\.yml"));
        if (files != null) {
            for (File file : files) {
                String code = file.getName().substring(9, file.getName().length() - 4);
                Messages m = new Messages(file, resource.apply("defaults/" + file.getName()),
                        resource.apply("defaults/messages_en.yml"));
                m.language = code;
                available.put(code, m);
            }
        }
        available.computeIfAbsent("en", code -> new Messages(new File(folder, "messages_en.yml"),
                resource.apply("defaults/messages_en.yml"), resource.apply("defaults/messages_en.yml")));
        String requested = normalize(config.getString("language", "en"));
        if (!available.containsKey(requested)) {
            warn.accept("config.yml: no messages_" + requested + ".yml - using English");
            requested = "en";
        }
        boolean useClient = config.getBoolean("client-language", false);
        Map<String, Messages> registry = Map.copyOf(available);
        for (Messages m : available.values()) {
            m.languages = registry;
            m.clientLanguage = useClient;
        }
        return available.get(requested);
    }

    private static String normalize(String code) {
        return code == null ? "" : code.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    public String language() {
        return language;
    }

    public Messages forAudience(CommandSender audience) {
        return clientLanguage && audience instanceof Player player ? forLocale(player.locale().toString()) : this;
    }

    /** Exact regional locale, then base language, then the configured server language. */
    public Messages forLocale(String locale) {
        String code = normalize(locale);
        Messages exact = languages.get(code);
        if (exact != null) {
            return exact;
        }
        int separator = code.indexOf('_');
        return languages.getOrDefault(separator < 0 ? code : code.substring(0, separator), this);
    }

    /** Catalog labels may be customized; unknown IDs retain their configured name. */
    public String label(String key, String defaultValue) {
        String value = file.getString(key, fallback.getString(key, english.getString(key)));
        return value == null ? defaultValue : value;
    }

    private static YamlConfiguration load(InputStream in) {
        if (in == null) return new YamlConfiguration();
        try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public String raw(String key) {
        String v = file.getString(key);
        if (v == null) {
            v = fallback.getString(key);
        }
        if (v == null) {
            v = english.getString(key);
        }
        return v == null ? "<red>" + key : v;
    }

    public List<String> rawList(String key) {
        List<String> v = file.isList(key) ? file.getStringList(key)
                : fallback.isList(key) ? fallback.getStringList(key) : english.getStringList(key);
        return v == null ? List.of() : v;
    }

    private TagResolver[] withPrefix(TagResolver... resolvers) {
        TagResolver[] all = new TagResolver[resolvers.length + 1];
        all[0] = prefix;
        System.arraycopy(resolvers, 0, all, 1, resolvers.length);
        return all;
    }

    public Component get(String key, TagResolver... resolvers) {
        return Text.mm(raw(key), withPrefix(resolvers));
    }

    /** Non-italic variant for item names and lore. */
    public Component item(String key, TagResolver... resolvers) {
        return Text.item(raw(key), withPrefix(resolvers));
    }

    public List<Component> itemList(String key, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>();
        for (String line : rawList(key)) {
            out.add(Text.item(line, withPrefix(resolvers)));
        }
        return out;
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        Messages localized = forAudience(to);
        String raw = localized.raw(key);
        if (!raw.isEmpty()) {
            to.sendMessage(Text.mm(raw, localized.withPrefix(resolvers)));
        }
    }
}
