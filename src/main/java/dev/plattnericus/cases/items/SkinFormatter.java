package dev.plattnericus.cases.items;

import dev.plattnericus.cases.catalog.CaseDefinition;
import dev.plattnericus.cases.catalog.Catalog;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.catalog.WearTier;
import dev.plattnericus.cases.config.Messages;
import dev.plattnericus.cases.skin.PatternInfo;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns definitions and instances into names, lore lines and compact action-bar text.
 * Only lines that apply to the given skin are produced.
 */
public final class SkinFormatter {

    public static final int STATTRAK_COLOR = 0xCF6A32;

    private final Messages m;
    private final Catalog catalog;
    private final int floatDecimals;
    private final int inspectDecimals;
    private final DateTimeFormatter dateFormat;
    private final java.util.Map<Messages, SkinFormatter> localized = new java.util.concurrent.ConcurrentHashMap<>();

    public SkinFormatter(Messages messages, Catalog catalog, int floatDecimals, int inspectDecimals, String datePattern) {
        this.m = messages;
        this.catalog = catalog;
        this.floatDecimals = floatDecimals;
        this.inspectDecimals = inspectDecimals;
        DateTimeFormatter f;
        try {
            f = DateTimeFormatter.ofPattern(datePattern).withZone(ZoneId.systemDefault());
        } catch (IllegalArgumentException e) {
            f = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
        }
        this.dateFormat = f;
    }

    private SkinFormatter(SkinFormatter source, Messages messages) {
        this.m = messages;
        this.catalog = source.catalog;
        this.floatDecimals = source.floatDecimals;
        this.inspectDecimals = source.inspectDecimals;
        this.dateFormat = source.dateFormat;
    }

    public SkinFormatter forAudience(org.bukkit.command.CommandSender viewer) {
        Messages messages = m.forAudience(viewer);
        return messages == m ? this : localized.computeIfAbsent(messages, language -> new SkinFormatter(this, language));
    }

    public String wearName(WearTier tier) {
        return m.label("wear." + tier.id(), tier.name());
    }

    public WearTier wear(SkinInstance inst) {
        return catalog.wear().of(inst.floatValue());
    }

    public String floatText(double value, boolean precise) {
        return Text.formatFloat(value, precise ? inspectDecimals : floatDecimals);
    }

    /** "StatTrak™ AK-47 | Redline" in rarity color. */
    public Component name(SkinDefinition def, SkinInstance inst) {
        String name = def.displayName();
        if (inst != null && inst.statTrak()) {
            name = (def.weapon().category().isStarItem() ? "★ StatTrak™ " + name.substring(2) : "StatTrak™ " + name);
        }
        return Text.item("<rarity>" + Text.escape(name), Text.color("rarity", def.rarity().color()));
    }

    /** Name with exterior suffix, e.g. "AK-47 | Redline (Field-Tested)". */
    public Component fullName(SkinDefinition def, SkinInstance inst) {
        Component base = name(def, inst);
        if (inst == null || !def.hasWear()) {
            return base;
        }
        return base.append(Text.item(" <gray>(" + Text.escape(wearName(wear(inst))) + ")"));
    }

    private TagResolver[] resolvers(SkinDefinition def, SkinInstance inst, boolean precise) {
        PatternInfo p = inst == null ? PatternInfo.NONE : inst.patternInfo();
        CaseDefinition source = inst == null || inst.sourceCase() == null ? null : catalog.caseDefinition(inst.sourceCase());
        return new TagResolver[]{
                Text.color("rarity_color", def.rarity().color()),
                Text.unparsed("rarity", m.label("rarity." + def.rarity().id(), def.rarity().name())),
                Text.unparsed("weapon", def.weapon().name()),
                Text.unparsed("finish", def.finish().isVanilla() ? "Vanilla" : def.finish().name()),
                Text.unparsed("skin", def.displayName()),
                Text.unparsed("wear", inst == null ? "" : wearName(wear(inst))),
                Text.unparsed("wear_short", inst == null ? "" : wear(inst).shortName()),
                Text.unparsed("float", inst == null ? "" : floatText(inst.floatValue(), precise)),
                Text.unparsed("pattern", inst == null ? "" : String.valueOf(inst.pattern())),
                Text.unparsed("variant", p.variantName() == null ? "" : p.variantName()),
                Text.unparsed("classification", p.classification() == null ? "" : p.classification()),
                Text.color("class_color", p.color() == 0 ? 0xFFFFFF : p.color()),
                Text.unparsed("fade", p.fadePercent() == null ? "" : Text.formatFloat(p.fadePercent(), 1)),
                Text.unparsed("kills", inst == null ? "0" : String.valueOf(inst.kills())),
                Text.unparsed("case", source == null ? (inst == null || inst.sourceCase() == null ? "-" : inst.sourceCase()) : source.name()),
                Text.unparsed("date", inst == null ? "" : dateFormat.format(Instant.ofEpochMilli(inst.createdAt()))),
                Text.unparsed("id", inst == null ? "" : inst.shortId()),
                Text.unparsed("min_float", Text.formatFloat(def.minFloat(), 2)),
                Text.unparsed("max_float", Text.formatFloat(def.maxFloat(), 2)),
                Text.unparsed("category", m.raw("category." + def.weapon().category().name().toLowerCase(java.util.Locale.ROOT)))
        };
    }

    /** Lore for an owned instance; {@code precise} uses the long float format (inspect view). */
    public List<Component> lore(SkinDefinition def, SkinInstance inst, boolean precise) {
        TagResolver[] r = resolvers(def, inst, precise);
        PatternInfo p = inst.patternInfo();
        List<Component> lore = new ArrayList<>();
        lore.add(m.item("skin.lore.rarity", r));
        if (def.hasWear()) {
            lore.add(m.item("skin.lore.exterior", r));
            lore.add(m.item("skin.lore.float", r));
        }
        if (def.finish().patterned()) {
            lore.add(m.item("skin.lore.pattern", r));
        }
        if (p.variantName() != null) {
            lore.add(m.item("skin.lore.variant", r));
        }
        if (p.hasClassification()) {
            lore.add(m.item("skin.lore.classification", r));
        }
        if (p.fadePercent() != null) {
            lore.add(m.item("skin.lore.fade", r));
        }
        if (inst.statTrak()) {
            lore.add(m.item("skin.lore.stattrak", r));
        }
        if (precise) {
            lore.add(m.item("skin.lore.source", r));
            lore.add(m.item("skin.lore.date", r));
            lore.add(m.item("skin.lore.id", r));
        }
        if (inst.origin() == SkinInstance.Origin.TEST) {
            lore.add(m.item("items.test-marker"));
        }
        return lore;
    }

    /** Lore for a definition shown in a case preview (no rolled values). */
    public List<Component> previewLore(SkinDefinition def) {
        TagResolver[] r = resolvers(def, null, false);
        List<Component> lore = new ArrayList<>();
        lore.add(m.item("skin.lore.rarity", r));
        lore.add(m.item("skin.lore.category", r));
        if (def.hasWear()) {
            lore.add(m.item("skin.lore.float-range", r));
        }
        if (def.finish().hasVariants()) {
            lore.add(m.item("skin.lore.has-variants", r));
        }
        return lore;
    }

    /** One-line summary for the action bar during inspect. */
    public Component actionBar(SkinDefinition def, SkinInstance inst) {
        TagResolver[] r = resolvers(def, inst, false);
        StringBuilder sb = new StringBuilder(m.raw("actionbar.name"));
        if (def.hasWear()) {
            sb.append(m.raw("actionbar.wear"));
        }
        if (def.finish().patterned()) {
            sb.append(m.raw("actionbar.pattern"));
        }
        PatternInfo p = inst.patternInfo();
        if (p.variantName() != null) {
            sb.append(m.raw("actionbar.variant"));
        }
        if (p.hasClassification()) {
            sb.append(m.raw("actionbar.classification"));
        } else if (p.fadePercent() != null) {
            sb.append(m.raw("actionbar.fade"));
        }
        return Text.mm(sb.toString(), r);
    }

    public Component line(String key, SkinDefinition def, SkinInstance inst) {
        return m.get(key, resolvers(def, inst, false));
    }

    public TagResolver[] placeholders(SkinDefinition def, SkinInstance inst) {
        return resolvers(def, inst, false);
    }
}
