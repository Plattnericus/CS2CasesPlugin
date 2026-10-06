package dev.plattnericus.cases.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Locale;

/** MiniMessage helpers. Item names and lore are always rendered without the default italics. */
public final class Text {

    public static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component mm(String text, TagResolver... resolvers) {
        return MM.deserialize(text == null ? "" : text, resolvers);
    }

    /** Non-italic component for item names and lore. */
    public static Component item(String text, TagResolver... resolvers) {
        return mm(text, resolvers).decoration(TextDecoration.ITALIC, false);
    }

    public static Component plainItem(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    /** Placeholder whose value is inserted literally (no tag parsing of user content). */
    public static TagResolver unparsed(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    public static TagResolver component(String key, Component value) {
        return Placeholder.component(key, value);
    }

    /** {@code <rarity>} style color tag for a packed RGB color. */
    public static TagResolver color(String key, int rgb) {
        return Placeholder.styling(key, net.kyori.adventure.text.format.TextColor.color(rgb));
    }

    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** Literal English material label, so the client cannot translate plugin text into another language. */
    public static Component materialName(org.bukkit.Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        for (int i = 0; i < words.length; i++) words[i] = Character.toUpperCase(words[i].charAt(0)) + words[i].substring(1);
        return Component.text(String.join(" ", words));
    }

    public static String formatFloat(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    public static String escape(String s) {
        return MM.escapeTags(s);
    }
}
