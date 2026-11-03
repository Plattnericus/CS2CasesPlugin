package dev.plattnericus.cases.config;

import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * All player-facing text (chat, GUI titles, item lore) from messages_en.yml in MiniMessage format.
 * Missing keys fall back to the bundled default file, so updates never show raw keys.
 */
public final class Messages {

    private final YamlConfiguration file;
    private final YamlConfiguration fallback;
    private final YamlConfiguration english;
    private final TagResolver prefix;

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

    /** Loads the English message file once on enable/reload. */
    public static Messages load(JavaPlugin plugin, YamlConfiguration config, Consumer<String> warn) {
        return load(plugin.getDataFolder(), plugin::getResource, config, warn);
    }

    public static Messages load(File folder, java.util.function.Function<String, InputStream> resource,
                                YamlConfiguration config, Consumer<String> warn) {
        return new Messages(new File(folder, "messages_en.yml"),
                resource.apply("defaults/messages_en.yml"), resource.apply("defaults/messages_en.yml"));
    }

    public String language() {
        return "en";
    }

    /** All text is English; kept so callers can stay audience-based. */
    public Messages forAudience(CommandSender audience) {
        return this;
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
