package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.config.Messages;
import dev.plattnericus.cases.opening.Easing;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.skin.PatternInfo;
import org.bukkit.configuration.file.YamlConfiguration;
import org.joml.Matrix4f;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Regression checks that run without a Minecraft server or a test framework. */
public final class FeatureChecks {
    private FeatureChecks() { }

    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        InspectRigChecks.run(root);
        CommerceChecks.run();
        SkinPresentationChecks.run(root);
        dev.plattnericus.cases.opening.OpeningChecks.run(root);
        TradeInChecks.run(root);
        InventoryInputChecks.run();
        checkCatalogAndJournal(root);
        var english = new YamlConfiguration();
        english.load(new File(root, "messages_en.yml"));
        int languages = 0;
        for (File file : root.listFiles((dir, name) -> name.startsWith("messages_") && name.endsWith(".yml"))) {
            var language = new YamlConfiguration();
            language.load(file); // Unlike loadConfiguration, this fails on invalid YAML.
            for (String key : english.getKeys(true)) {
                if (english.isConfigurationSection(key)) continue;
                boolean commerceFallback = ((key.startsWith("gui.skins.tab.gloves.") || key.startsWith("gui.preview.open-nine.") || key.startsWith("gui.preview.queue-") || key.startsWith("opening.queue-") || key.equals("opening.missing-amount") || key.equals("opening.missing-nine") || key.startsWith("case-guide.") || key.startsWith("trade.") || key.startsWith("market.") || key.startsWith("commerce.") || key.startsWith("browser.") || key.startsWith("input.") || key.startsWith("tradein.") || key.startsWith("opening.active-") || key.startsWith("opening.states.") || key.equals("opening.limit"))
                        && !file.getName().equals("messages_de.yml")) || key.equals("inventory.usage");
                require(language.contains(key) || commerceFallback, file.getName() + " missing " + key);
                var source = language.contains(key) ? language : english;
                require(source.isList(key) == english.isList(key), file.getName() + " wrong type " + key);
                List<String> values = source.isList(key) ? source.getStringList(key) : List.of(source.getString(key));
                for (String value : values) {
                    require(!value.contains("__PH"), file.getName() + " contains translation marker at " + key);
                    dev.plattnericus.cases.util.Text.mm(value); // Checks MiniMessage syntax.
                }
            }
            languages++;
        }
        var config = new YamlConfiguration();
        config.set("language", "de");
        config.set("client-language", true);
        List<String> warnings = new ArrayList<>();
        Messages messages = Messages.load(root, path -> {
            try { return Files.newInputStream(new File(root, path.substring("defaults/".length())).toPath()); }
            catch (java.io.IOException ignored) { return null; }
        }, config, warnings::add);
        require(warnings.isEmpty(), "unexpected locale warnings " + warnings);
        require(messages.forLocale("de_DE").language().equals("de"), "German region fallback");
        require(messages.forLocale("ES-mx").language().equals("es"), "case/hyphen normalization");
        require(messages.forLocale("zh_TW").language().equals("zh_tw"), "Traditional Chinese locale");
        require(messages.forLocale("pt_BR").language().equals("pt_br"), "Brazilian Portuguese locale");
        require(messages.forLocale("unknown_foo") == messages, "configured fallback");
        require(messages.forLocale("../../en") == messages, "locale must never access paths");
        require(messages.forLocale("fr_FR").raw("gui.close.name").contains("Fermer"), "French lookup");
        require(messages.forLocale("fr_FR").raw("market.title").equals("Skin marketplace"), "new commerce keys use English fallback");
        require(messages.forLocale("fr_FR").raw("gui.skins.tab.gloves.name").equals("<white>Gloves"), "new glove tab uses English fallback");
        var received = new java.util.concurrent.atomic.AtomicReference<net.kyori.adventure.text.Component>();
        org.bukkit.entity.Player french = (org.bukkit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(
                FeatureChecks.class.getClassLoader(), new Class<?>[]{org.bukkit.entity.Player.class}, (proxy, method, parameters) -> {
                    if (method.getName().equals("locale")) return java.util.Locale.FRANCE;
                    if (method.getName().equals("sendMessage") && parameters != null)
                        for (Object parameter : parameters) if (parameter instanceof net.kyori.adventure.text.Component component) received.set(component);
                    return null;
                });
        messages.send(french, "gui.close.name");
        require(dev.plattnericus.cases.util.Text.plain(received.get()).equals("Fermer"), "send must use recipient locale");
        config.set("client-language", false);
        Messages fixed = Messages.load(root, path -> null, config, warnings::add);
        require(fixed.forAudience(french) == fixed, "server language override");
        File edited = Files.createTempFile("mccases-locale-check", ".yml").toFile();
        try {
            Files.writeString(edited.toPath(), "gui:\n  close:\n    name: Custom close\n");
            Messages partial = new Messages(edited, Files.newInputStream(new File(root, "messages_fr.yml").toPath()),
                    Files.newInputStream(new File(root, "messages_en.yml").toPath()));
            require(partial.raw("gui.close.name").equals("Custom close"), "admin edits must survive");
            require(partial.raw("gui.back.name").equals(messages.forLocale("fr").raw("gui.back.name")), "new keys must use bundled locale fallback");
        } finally { Files.deleteIfExists(edited.toPath()); }

        var inspect = new YamlConfiguration();
        inspect.load(new File(root, "inspect.yml"));
        int animations = 0;
        for (String id : inspect.getConfigurationSection("animations").getKeys(false)) {
            var animation = InspectFilmstrip.animation(id, inspect.getConfigurationSection("animations." + id));
            require(animation.duration() > 0, "empty animation " + id);
            var samples = animation.samples();
            require(samples.getFirst() == 0 && samples.getLast() == animation.duration(), "animation endpoints " + id);
            for (int i = 1; i < samples.size(); i++) {
                require(samples.get(i) > samples.get(i - 1), "duplicate samples " + id);
                require(samples.get(i) - samples.get(i - 1) <= 2, "sparse interpolation " + id);
            }
            for (String group : inspect.getConfigurationSection("animations." + id + ".groups").getKeys(false)) {
                for (int tick = 0; tick <= animation.duration(); tick++) {
                    Matrix4f matrix = animation.groupMatrix(group, tick);
                    require(matrix.isFinite(), "nonfinite transform " + id + " " + group + " " + tick);
                }
            }
            if (!id.equals("reveal")) require(animation.groupMatrix("body", 0).equals(animation.groupMatrix("body", animation.duration()), 0.00002f),
                    "inspect does not return to held pose: " + id);
            animations++;
        }
        for (String pool : inspect.getConfigurationSection("animation-pools").getKeys(false)) {
            List<String> choices = inspect.getStringList("animation-pools." + pool);
            require(choices.stream().distinct().count() >= 2, "no variation " + pool);
            for (String choice : choices) require(inspect.contains("animations." + choice), "unknown animation in " + pool);
            String previousChoice = null;
            java.util.Set<String> seen = new java.util.HashSet<>();
            var random = new java.util.Random(42);
            for (int repeat = 0; repeat < 300; repeat++) {
                String chosen = dev.plattnericus.cases.inspect.AnimationSelector.choose(choices, previousChoice, random);
                require(!chosen.equals(previousChoice), "immediate repeat in " + pool);
                seen.add(chosen);
                previousChoice = chosen;
            }
            require(seen.containsAll(choices), "unreachable variant in " + pool);
        }
        require(dev.plattnericus.cases.inspect.AnimationSelector.choose(List.of("only"), "only", new java.util.Random(1)).equals("only"), "custom single-variant pool");
        double previous = 0;
        require(Easing.CINEMATIC.apply(0) == 0 && Easing.CINEMATIC.apply(1) == 1, "reel endpoints");
        for (int i = 0; i <= 10000; i++) {
            double position = Easing.CINEMATIC.apply(i / 10000.0);
            require(position >= previous - 1e-12 && position <= 1, "reel reverses or overshoots");
            previous = position;
        }
        System.out.println("Verified " + languages + " language schemas, German/English commerce texts and English fallbacks, regional fallbacks, " + animations + " animation timelines and reel easing.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkCatalogAndJournal(File root) {
        var textures = new dev.plattnericus.cases.render.TextureStore(new File(root, "textures"));
        var loaded = new dev.plattnericus.cases.catalog.CatalogLoader(new File(root, "catalog"), textures).load(1);
        require(loaded.problems().isEmpty(), "catalog problems: " + loaded.problems());
        var catalog = loaded.catalog();
        require(!catalog.skins().isEmpty() && !catalog.cases().isEmpty(), "empty catalog");
        var tiers = catalog.wear().tiers();
        require(tiers.getFirst().min() == 0 && tiers.getLast().max() >= 1, "wear coverage");
        for (int i = 0; i < tiers.size(); i++) {
            var tier = tiers.get(i);
            require(catalog.wear().of(tier.min()).equals(tier), "wear lower boundary " + tier.id());
            require(catalog.wear().of(Math.nextDown(tier.max())).equals(tier), "wear upper boundary " + tier.id());
            if (i > 0) require(tiers.get(i - 1).max() == tier.min(), "gap in wear tiers");
        }
        require(catalog.wear().of(1).equals(tiers.getLast()), "float 1 exterior");
        for (var skin : catalog.skins()) {
            require(catalog.weapon(skin.weapon().id()) == skin.weapon(), "unknown weapon " + skin.id());
            require(Double.isFinite(skin.minFloat()) && Double.isFinite(skin.maxFloat())
                    && skin.minFloat() >= 0 && skin.maxFloat() <= 1 && skin.minFloat() <= skin.maxFloat(), "float limits " + skin.id());
            require(!skin.statTrakEligible() || skin.weapon().statTrak(), "ineligible StatTrak " + skin.id());
        }
        var roller = new dev.plattnericus.cases.reward.RewardRoller();
        int rolls = 0;
        for (var def : catalog.cases()) {
            require(catalog.key(def.keyId()) != null && def.size() > 0, "empty/unkeyed case " + def.id());
            double sum = 0;
            for (var rarity : catalog.raritiesOrdered()) {
                double chance = dev.plattnericus.cases.reward.RewardRoller.chance(def, catalog, rarity);
                require(chance >= 0 && chance <= 1, "invalid odds " + def.id());
                if (chance > 0) {
                    double draw = sum + chance / 2;
                    var fixed = new java.util.Random(0) { @Override public double nextDouble() { return draw; } };
                    require(dev.plattnericus.cases.reward.RewardRoller.rollRarity(def, catalog, fixed).equals(rarity), "weighted interval " + def.id() + "/" + rarity.id());
                }
                sum += chance;
            }
            require(Math.abs(sum - 1) < 1e-12, "odds do not sum to 1: " + def.id());
            for (int i = 0; i < 250; i++) {
                var reward = roller.roll(def, catalog);
                require(def.skins(reward.skin().rarity()).contains(reward.skin()), "reward outside case pool");
                require(reward.floatValue() >= reward.skin().minFloat() && reward.floatValue() <= reward.skin().maxFloat(), "reward float");
                require(reward.pattern() >= catalog.patterns().seedMin() && reward.pattern() <= catalog.patterns().seedMax(), "pattern seed");
                require(!reward.statTrak() || reward.skin().statTrakEligible(), "ineligible reward StatTrak");
                var reel = dev.plattnericus.cases.opening.ReelBuilder.build(def, catalog, reward.skin(), 60, i);
                require(reel.size() == 60 && reel.get(60 - dev.plattnericus.cases.opening.ReelBuilder.TAIL) == reward.skin(), "reel winner moved");
                require(reel.stream().allMatch(s -> def.skins(s.rarity()).contains(s)), "reel outside case pool");
                rolls++;
            }
        }
        var sample = new dev.plattnericus.cases.skin.SkinInstance(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                catalog.skins().iterator().next().id(), 0.123456, 42, Long.MIN_VALUE, true, 0,
                new dev.plattnericus.cases.skin.PatternInfo("phase2", "Phase 2", "Ruby", 1, 0xff0044, 98.5),
                catalog.cases().getFirst().id(), dev.plattnericus.cases.skin.SkinInstance.Origin.TEST, 12345, false,
                dev.plattnericus.cases.skin.SkinInstance.Status.PENDING);
        String encoded = dev.plattnericus.cases.skin.InstanceCodec.encode(sample);
        var restored = dev.plattnericus.cases.skin.InstanceCodec.decode(encoded);
        require(restored != null && restored.id().equals(sample.id()) && restored.owner().equals(sample.owner())
                && restored.skinId().equals(sample.skinId()) && restored.floatValue() == sample.floatValue()
                && restored.pattern() == sample.pattern() && restored.wearSeed() == sample.wearSeed()
                && restored.statTrak() && restored.patternInfo().equals(sample.patternInfo())
                && restored.origin() == sample.origin() && restored.status() == sample.status(), "journal round trip");
        for (String bad : new String[]{"null", "{}", "garbage", encoded.replace("\"origin\":\"TEST\",", ""),
                encoded.replace("\"v\":1", "\"v\":9"), encoded.replace("\"fl\":0.123456", "\"fl\":2"),
                encoded.replace("\"pattern\":42", "\"pattern\":-1"), encoded.replace(sample.id().toString(), "invalid")}) {
            require(dev.plattnericus.cases.skin.InstanceCodec.decode(bad) == null, "malformed journal accepted: " + bad);
        }
        var profile = new dev.plattnericus.cases.profile.PlayerProfile(sample.owner());
        var definitions = catalog.skins().stream().sorted(java.util.Comparator.comparing(dev.plattnericus.cases.catalog.SkinDefinition::id)).toList();
        for (int i = 0; i < 70; i++) {
            var skin = definitions.get(i * (definitions.size() - 1) / 69);
            profile.put(new dev.plattnericus.cases.skin.SkinInstance(java.util.UUID.randomUUID(), profile.owner(), skin.id(),
                    skin.minFloat() + (skin.maxFloat() - skin.minFloat()) * i / 70, i, i, i % 2 == 0, i,
                    dev.plattnericus.cases.skin.PatternInfo.NONE, null, dev.plattnericus.cases.skin.SkinInstance.Origin.TEST,
                    i, i % 3 == 0, dev.plattnericus.cases.skin.SkinInstance.Status.OWNED));
        }
        var state = new dev.plattnericus.cases.gui.MenuStates.State();
        for (var category : dev.plattnericus.cases.gui.MenuStates.Category.values()) {
            state.category = category;
            var entries = dev.plattnericus.cases.gui.SkinQuery.run(catalog, profile.owned(), state);
            require(entries.stream().allMatch(e -> switch (category) {
                case ALL, RECENT -> true;
                case WEAPONS -> !e.definition().weapon().category().isStarItem();
                case KNIVES -> e.definition().isKnife();
                case GLOVES -> e.definition().weapon().category() == dev.plattnericus.cases.catalog.WeaponCategory.GLOVE;
                case STATTRAK -> e.instance().statTrak();
                case FAVORITES -> e.instance().favorite();
            }), "inventory category " + category);
            if (category == dev.plattnericus.cases.gui.MenuStates.Category.RECENT)
                require(entries.size() == 36 && entries.getFirst().instance().createdAt() == 69, "recent inventory limit/order");
        }
        state.category = dev.plattnericus.cases.gui.MenuStates.Category.ALL;
        state.statTrakOnly = true;
        state.rarities.add(catalog.skin(sample.skinId()).rarity().id());
        var filtered = dev.plattnericus.cases.gui.SkinQuery.run(catalog, profile.owned(), state);
        require(filtered.stream().allMatch(e -> e.instance().statTrak() && state.rarities.contains(e.definition().rarity().id())), "combined inventory filters");
        state.statTrakOnly = false; state.rarities.clear();
        for (var sort : dev.plattnericus.cases.gui.MenuStates.Sort.values()) {
            state.sort = sort;
            var entries = dev.plattnericus.cases.gui.SkinQuery.run(catalog, profile.owned(), state);
            require(entries.size() == 70, "sorting lost skins");
            for (int i = 1; i < entries.size(); i++) {
                var a = entries.get(i - 1); var b = entries.get(i);
                require(switch (sort) {
                    case RARITY -> a.definition().rarity().order() >= b.definition().rarity().order();
                    case FLOAT -> a.instance().floatValue() <= b.instance().floatValue();
                    case NAME -> a.definition().displayName().compareTo(b.definition().displayName()) <= 0;
                    case WEAR -> catalog.wear().tiers().indexOf(catalog.wear().of(a.instance().floatValue())) <= catalog.wear().tiers().indexOf(catalog.wear().of(b.instance().floatValue()));
                    case PATTERN -> a.instance().pattern() <= b.instance().pattern();
                    case NEWEST -> a.instance().createdAt() >= b.instance().createdAt();
                    case OLDEST -> a.instance().createdAt() <= b.instance().createdAt();
                }, "inventory sorting " + sort);
            }
        }
        profile.put(sample);
        require(profile.owned().size() == 70 && profile.find(sample.shortId()) == null, "pending skin visible in inventory");
        var equipped = profile.owned().getFirst();
        profile.setEquipped(dev.plattnericus.cases.profile.EquipSlot.BOW, equipped.id()); profile.remove(equipped.id());
        require(profile.equipped(dev.plattnericus.cases.profile.EquipSlot.BOW) == null, "removing skin left equipped reference");
        var collisions = new dev.plattnericus.cases.profile.PlayerProfile(sample.owner());
        for (String id : List.of("aaaaaaaa-0000-0000-0000-000000000001", "aaaaaaaa-0000-0000-0000-000000000002")) {
            collisions.put(new SkinInstance(UUID.fromString(id), sample.owner(), sample.skinId(), .03, 1, 1,
                    false, 0, PatternInfo.NONE, "admin", SkinInstance.Origin.ADMIN, 1, false, SkinInstance.Status.OWNED));
        }
        require(collisions.find("a") == null && collisions.find("aaaaaaaa") == null,
                "unsafe or ambiguous admin ID selected an arbitrary skin");
        require(collisions.find("AAAAAAAA-0000-0000-0000-000000000001") != null,
                "full UUID failed to disambiguate the skin");
        System.out.println("Verified " + catalog.skins().size() + " skins, " + catalog.cases().size() + " cases, " + rolls
                + " rewards/reels, weighted odds, wear boundaries, inventory queries and journal recovery codec.");
    }
}
