package dev.plattnericus.cases.catalog;

import dev.plattnericus.cases.pattern.PatternConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of all content. A reload builds a new snapshot; running sessions keep the
 * snapshot they started with, so a reload can never corrupt an opening in progress.
 */
public final class Catalog {

    private final long version;
    private final Map<String, Rarity> rarities;
    private final List<Rarity> raritiesOrdered;
    private final WearScale wear;
    private final Map<String, WeaponType> weapons;
    private final Map<String, Finish> finishes;
    private final Map<String, SkinDefinition> skins;
    private final Map<String, CaseDefinition> cases;
    private final Map<String, List<CaseDefinition>> casesBySkin;
    private final Map<String, KeyDefinition> keys;
    private final PatternConfig patterns;

    public Catalog(long version, Map<String, Rarity> rarities, WearScale wear, Map<String, WeaponType> weapons,
                   Map<String, Finish> finishes, Map<String, SkinDefinition> skins,
                   Map<String, CaseDefinition> cases, Map<String, KeyDefinition> keys, PatternConfig patterns) {
        this.version = version;
        this.rarities = Map.copyOf(rarities);
        List<Rarity> ordered = new ArrayList<>(rarities.values());
        ordered.sort(Comparator.comparingInt(Rarity::order));
        this.raritiesOrdered = List.copyOf(ordered);
        this.wear = wear;
        this.weapons = Map.copyOf(weapons);
        this.finishes = Map.copyOf(finishes);
        this.skins = Map.copyOf(skins);
        this.cases = Map.copyOf(cases);
        Map<String, List<CaseDefinition>> sources = new java.util.HashMap<>();
        for (var source : cases.values()) for (var pool : source.pool().values()) for (var skin : pool)
            sources.computeIfAbsent(skin.id(), id -> new ArrayList<>()).add(source);
        sources.replaceAll((id, entries) -> entries.stream().distinct().sorted(Comparator.comparing(CaseDefinition::id)).toList());
        this.casesBySkin = Map.copyOf(sources);
        this.keys = Map.copyOf(keys);
        this.patterns = patterns;
    }

    public long version() {
        return version;
    }

    public Rarity rarity(String id) {
        return rarities.get(id);
    }

    /** Lowest tier first. */
    public List<Rarity> raritiesOrdered() {
        return raritiesOrdered;
    }

    public WearScale wear() {
        return wear;
    }

    public WeaponType weapon(String id) {
        return weapons.get(id);
    }

    public Collection<WeaponType> weapons() {
        return weapons.values();
    }

    public Finish finish(String id) {
        return finishes.get(id);
    }

    public SkinDefinition skin(String id) {
        return skins.get(id);
    }

    public Collection<SkinDefinition> skins() {
        return skins.values();
    }

    public CaseDefinition caseDefinition(String id) {
        return cases.get(id);
    }

    /** Indexed once per immutable catalog; contract browsing never scans all case contents. */
    public List<CaseDefinition> casesForSkin(String id) { return casesBySkin.getOrDefault(id, List.of()); }

    /** Cases sorted by display name. */
    public List<CaseDefinition> cases() {
        List<CaseDefinition> list = new ArrayList<>(cases.values());
        list.sort(Comparator.comparing(CaseDefinition::name));
        return list;
    }

    public KeyDefinition key(String id) {
        return keys.get(id);
    }

    public Collection<KeyDefinition> keys() {
        return keys.values();
    }

    public PatternConfig patterns() {
        return patterns;
    }
}
