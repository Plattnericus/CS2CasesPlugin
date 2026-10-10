package dev.plattnericus.cases.opening;

import dev.plattnericus.cases.catalog.*;
import dev.plattnericus.cases.config.PluginSettings;
import dev.plattnericus.cases.render.TextureStore;
import dev.plattnericus.cases.reward.RewardRoller;
import dev.plattnericus.cases.skin.*;
import dev.plattnericus.cases.storage.*;
import dev.plattnericus.cases.tradein.TradeInRules;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/** Registry, durable opening recovery and actual catalog contract validation. */
public final class OpeningChecks {
    private OpeningChecks() { }
    public static void run(File root) throws Exception {
        var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new File(root, "config.yml"));
        require(config.getString("language").equals("en") && !config.getBoolean("client-language"), "default interface must stay English for all clients");
        require(config.getInt("opening.max-active-per-player") == 9, "default nine-opening capacity");
        require(config.getDouble("opening.world.scene-scale") == 3, "default whole-wheel size");
        var catalog = new CatalogLoader(new File(root, "catalog"), new TextureStore(new File(root, "textures"))).load(1).catalog();
        var def = catalog.caseDefinition("kilowatt_case");
        presentation(catalog, def);
        // Official Perfect World case table, including equal item probability within each tier.
        var expectedOdds = java.util.Map.of("mil_spec", .79923, "restricted", .15985, "classified", .03197, "covert", .00639, "rare_special", .00256);
        for (var caseDef : catalog.cases()) {
            require(Math.abs(caseDef.statTrakChance() - .1) < 1e-12, "CS2 StatTrak probability changed: " + caseDef.id());
            double total = 0;
            for (var rarity : catalog.raritiesOrdered()) {
                double chance = RewardRoller.chance(caseDef, catalog, rarity);
                require(Math.abs(chance - expectedOdds.get(rarity.id())) < 1e-12, "CS2 rarity probability changed: " + caseDef.id() + "/" + rarity.id());
                require(!caseDef.skins(rarity).isEmpty(), "missing rarity pool"); total += chance;
            }
            require(Math.abs(total - 1) < 1e-12, "case probability total");
            if (java.util.Set.of("glove_case", "clutch_case", "snakebite_case").contains(caseDef.id())) {
                var gloves = caseDef.skins(catalog.raritiesOrdered().getLast());
                require(gloves.size() == 24 && gloves.stream().allMatch(s -> s.weapon().category() == dev.plattnericus.cases.catalog.WeaponCategory.GLOVE && !s.statTrakEligible()), "glove gold pool incomplete or StatTrak eligible");
                var covert = caseDef.skins(catalog.raritiesOrdered().get(3)).getFirst();
                var input = new SkinInstance(UUID.randomUUID(), UUID.randomUUID(), covert.id(), .2, 0, 0, true, 0, PatternInfo.NONE, caseDef.id(), SkinInstance.Origin.CASE, 0, false, SkinInstance.Status.OWNED);
                require(TradeInRules.pool(catalog, input, catalog.raritiesOrdered().getLast()).isEmpty(), "StatTrak contract could output gloves");
            }
        }
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var registry = new OpeningSessions(); var sessions = new java.util.ArrayList<OpeningSession>();
        for (int i = 0; i < 9; i++) {
            var session = new OpeningSession(a, def, catalog, false, false);
            sessions.add(session);
        }
        require(registry.addAll(sessions, 9, 11), "nine-case request rejected");
        require(registry.forPlayer(a).size() == 9 && sessions.stream().map(s -> s.openingId).distinct().count() == 9 && sessions.stream().map(s -> s.lane).distinct().count() == 9, "opening IDs or lanes overwritten");
        require(registry.reserved(a, def.id(), false) == 9 && registry.reserved(a, def.keyId(), true) == 9, "pending item reservations missing");
        sessions.get(1).state = OpeningSession.State.PERSISTING;
        require(registry.reserved(a, def.id(), false) == 8 && registry.reserved(a, def.keyId(), true) == 8, "consumed items reserved twice");
        require(!registry.add(new OpeningSession(a, def, catalog, false, false), 9, 11), "per-player capacity exceeded");
        var tooLarge = java.util.stream.IntStream.range(0, 3).mapToObj(i -> new OpeningSession(b, def, catalog, false, false)).toList();
        require(!registry.addAll(tooLarge, 9, 11) && registry.forPlayer(b).isEmpty(), "rejected batch partly admitted");
        require(!registry.addAll(List.of(tooLarge.getFirst(), tooLarge.getFirst()), 9, 11), "duplicate batch accepted");
        require(!registry.addAll(List.of(tooLarge.getFirst(), new OpeningSession(a, def, catalog, false, false)), 9, 11), "mixed owners admitted");
        for (int i = 0; i < 2; i++) require(registry.add(new OpeningSession(b, def, catalog, false, false), 9, 11), "other player blocked");
        require(!registry.add(new OpeningSession(UUID.randomUUID(), def, catalog, false, false), 9, 11), "global capacity exceeded");
        registry.remove(sessions.getFirst().openingId, sessions.get(1)); require(registry.size() == 11, "wrong session removed another result");
        registry.remove(sessions.getFirst().openingId, sessions.getFirst()); require(registry.size() == 10, "cleanup failed");
        var replacement = new OpeningSession(a, def, catalog, false, false); require(registry.add(replacement, 9, 11) && replacement.lane == 0, "released lane was not reusable");
        layout();
        var directory = Files.createTempDirectory("mccases-openings-");
        var settings = new PluginSettings.Storage("sqlite", "opening.db", "", 0, "", "", "", "test_");
        var roller = new RewardRoller(); var outcomes = new java.util.ArrayList<SkinInstance>();
        try {
            try (var db = new Database(settings, directory.toFile(), java.util.logging.Logger.getLogger("OpeningChecks"))) {
                db.open(); var repo = new SkinRepository(db);
                var work = new java.util.ArrayList<java.util.concurrent.CompletableFuture<Void>>();
                for (int i = 0; i < 20; i++) {
                    var roll = roller.roll(def, catalog);
                    var reward = new SkinInstance(UUID.randomUUID(), i < 10 ? a : b, roll.skin().id(), roll.floatValue(), roll.pattern(), roll.wearSeed(), roll.statTrak(), 0, PatternInfo.NONE, def.id(), SkinInstance.Origin.CASE, System.currentTimeMillis(), false, SkinInstance.Status.PENDING);
                    var record = new OpeningRecord(UUID.randomUUID(), reward.owner(), "Test", def.id(), reward.id(), reward.skinId(), roll.skin().rarity().id(), reward.floatValue(), reward.pattern(), reward.statTrak(), false, reward.createdAt());
                    outcomes.add(reward); work.add(repo.persistOpening(reward, record)); work.add(repo.persistOpening(reward, record));
                }
                java.util.concurrent.CompletableFuture.allOf(work.toArray(java.util.concurrent.CompletableFuture[]::new)).join();
                require(repo.loadActive(a).join().size() == 10 && repo.loadActive(b).join().size() == 10 && repo.history(a, 30).join().size() == 10, "parallel results overwritten or duplicated");
            }
            try (var db = new Database(settings, directory.toFile(), java.util.logging.Logger.getLogger("OpeningChecks"))) {
                db.open(); var repo = new SkinRepository(db);
                require(repo.finalizePending(a).join() == 10 && repo.finalizePending(a).join() == 0, "recovery not idempotent");
                require(repo.loadActive(a).join().stream().allMatch(s -> s.status() == SkinInstance.Status.OWNED), "pending openings not recovered");
                for (var expected : outcomes) require(repo.loadActive(expected.owner()).join().stream().anyMatch(actual -> actual.id().equals(expected.id()) && actual.floatValue() == expected.floatValue() && actual.pattern() == expected.pattern()), "recovery changed result");
                contracts(catalog, def, a, db, repo);
            }
        } finally { try (var paths = Files.walk(directory)) { for (var p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p); } }
        System.out.println("PASS: independent opening IDs/lanes, player/global limits, 20 concurrent persisted outcomes, idempotent interrupted-opening recovery, normal/gold contract rules and atomic SQL consumption.");
    }
    private static void layout() {
        for (double distance : new double[]{1.2, 3, 6}) for (int count = 1; count <= 9; count++) {
            int size = count;
            var cells = java.util.stream.IntStream.range(0, count).mapToObj(i -> OpeningLayout.cell(i, size, 4.54, 1.15, distance)).toList();
            double minY = cells.stream().mapToDouble(c -> c.up() - .575 * c.scale()).min().orElseThrow();
            double maxY = cells.stream().mapToDouble(c -> c.up() + .575 * c.scale()).max().orElseThrow();
            require(Math.abs(minY + maxY) < 1e-9, "partial reel rows not vertically centered: " + count);
            require(Math.abs(cells.stream().mapToDouble(OpeningLayout.Cell::right).sum()) < 1e-9, "partial reel row not horizontally centered");
            for (var cell : cells) {
                require(cell.scale() > 0 && cell.scale() <= 1 && Math.abs(cell.right()) + 2.27 * cell.scale() < distance * .78,
                        "reel clipped horizontally: " + count);
                require(Math.abs(cell.up()) + .575 * cell.scale() < distance * .52, "reel clipped vertically: " + count);
            }
            for (int i = 0; i < count; i++) {
                var large = OpeningLayout.cell(i, count, 4.54, 1.15, distance, 3);
                require(Math.abs(large.scale() - OpeningLayout.cell(0, 9, 4.54, 1.15, distance, 3).scale()) < 1e-9,
                        "wheel changed size when the grid changed: " + count);
                require(Math.abs(large.right()) + 2.27 * large.scale() < distance * .78
                        && Math.abs(large.up()) + .575 * large.scale() < distance * .52,
                        "complete grid left the camera bounds: " + count);
                for (int j = 0; j < i; j++) {
                    var other = OpeningLayout.cell(j, count, 4.54, 1.15, distance, 3);
                    require(Math.abs(large.right() - other.right()) > 4.54 * large.scale()
                            || Math.abs(large.up() - other.up()) > 1.15 * large.scale(), "enlarged wheels overlap");
                }
            }
            for (int i = 0; i < cells.size(); i++) for (int j = i + 1; j < cells.size(); j++) {
                var a = cells.get(i); var b = cells.get(j);
                require(Math.abs(a.right() - b.right()) > 4.54 * a.scale() || Math.abs(a.up() - b.up()) > 1.15 * a.scale(), "reels overlap");
            }
        }
        var queue = new OpeningQueue(); UUID owner = UUID.randomUUID();
        require(!queue.add(owner, "a", "key", 0) && !queue.add(owner, "a", "key", 1001), "invalid amount accepted");
        require(queue.add(owner, "a", "key", 100), "100-case queue rejected");
        require(queue.add(owner, "b", "key", 9), "mixed case queue rejected");
        require(queue.reserved(owner, "key", true) == 109 && queue.reserved(owner, "a", false) == 100, "shared-key reservations wrong");
        int opened = 0, waves = 0;
        while (opened < 100) {
            var next = queue.take(owner, 9);
            require(next.caseId().equals("a") && next.amount() == Math.min(9, 100 - opened), "queue order or wave size wrong");
            opened += next.amount(); waves++;
        }
        require(waves == 12 && queue.count(owner) == 9, "100-case wave accounting wrong");
        require(queue.take(owner, 0) == null && queue.count(owner) == 9, "zero capacity consumed waiting request");
        require(queue.clear(owner) == 9 && queue.count(owner) == 0 && queue.owners().isEmpty(), "cancel leaked reservations");
        require(queue.add(owner, "a", "key", 1000) && !queue.add(owner, "a", "key", 1), "queue capacity exceeded");
        queue.clear();
        require(queue.count(owner) == 0, "shutdown queue not cleared");
        for (Easing easing : Easing.values()) {
            double previous = -1;
            for (int tick = 0; tick <= 400; tick++) {
                double position = easing.apply(tick / 400.0);
                require(Double.isFinite(position) && position >= previous && position >= 0 && position <= 1, "reel reversed or overshot");
                previous = position;
            }
            require(easing.apply(0) == 0 && easing.apply(1) == 1, "reel missed winner");
        }
        require(Easing.CINEMATIC.apply(.001) < .00002 && 1 - Easing.CINEMATIC.apply(.999) < .00002, "cinematic snap at endpoint");
    }
    private static void presentation(Catalog catalog, CaseDefinition def) {
        UUID owner = UUID.randomUUID(); var registry = new OpeningSessions();
        var sessions = java.util.stream.IntStream.range(0, 9).mapToObj(i -> new OpeningSession(owner, def, catalog, false, false)).toList();
        require(registry.addAll(sessions, 9, 9), "nine presentation fixtures rejected");
        for (int i = 8; i > 0; i--) sessions.get(i).state = OpeningSession.State.READY;
        require(registry.readyForPresentation(owner, true).isEmpty(), "async persistence reordered requests");
        sessions.getFirst().state = OpeningSession.State.READY;
        require(registry.readyForPresentation(owner, true).equals(sessions), "nine durable world sessions were serialized");
        require(registry.readyForPresentation(owner, false).equals(List.of(sessions.getFirst())), "GUI fallback started multiple menus");
        sessions.getFirst().state = OpeningSession.State.ANIMATING;
        sessions.getFirst().view = new OpeningView() {
            public void open() { } public void frame(double center) { } public void reveal() { } public void result() { } public void close() { }
        };
        require(registry.readyForPresentation(owner, true).size() == 8 && registry.readyForPresentation(owner, false).isEmpty(), "live first view blocked parallel reels");
        sessions.get(3).state = OpeningSession.State.PERSISTING;
        require(registry.readyForPresentation(owner, true).equals(sessions.subList(1, 3)), "uncommitted result displayed");
        System.out.println("PASS: nine simultaneous durable world presentations, out-of-order commit barrier, sequential GUI fallback; centered, non-overlapping 1–9 grids at 1.2/3/6 blocks.");
    }
    private static void contracts(Catalog catalog, CaseDefinition source, UUID owner, Database db, SkinRepository repo) {
        var special = catalog.raritiesOrdered().stream().filter(Rarity::rareSpecial).findFirst().orElseThrow();
        var covert = catalog.raritiesOrdered().stream().filter(r -> r.order() < special.order()).reduce((a,b) -> b).orElseThrow();
        var inputDef = source.skins(covert).getFirst();
        var inputs = java.util.stream.IntStream.range(0, 5).mapToObj(i -> new SkinInstance(UUID.randomUUID(), owner, inputDef.id(), 0.2, i, i, false, 0, PatternInfo.NONE, source.id(), SkinInstance.Origin.CASE, 1000+i, false, SkinInstance.Status.OWNED)).toList();
        require(TradeInRules.validate(catalog, inputs, true).rareSpecial(), "five Covert inputs did not target gold");
        var winner = TradeInRules.choose(catalog, inputs, special, new java.util.Random(7)); require(winner.rarity().rareSpecial(), "gold picked by name instead of classification");
        require(Math.abs(TradeInRules.outputFloat(inputs, winner) - (winner.minFloat() + 0.2 * (winner.maxFloat() - winner.minFloat()))) < 1e-9, "output float incorrect");
        expectInvalid(() -> TradeInRules.validate(catalog, inputs.subList(0, 4), true));
        var duplicate = new java.util.ArrayList<>(inputs); duplicate.set(1, inputs.getFirst()); expectInvalid(() -> TradeInRules.validate(catalog, duplicate, true));
        inputs.forEach(s -> repo.insert(s).join());
        var reward = new SkinInstance(UUID.randomUUID(), owner, winner.id(), 0.2, 77, 77, false, 0, PatternInfo.NONE, source.id(), SkinInstance.Origin.TRADE_IN, 9999, false, SkinInstance.Status.OWNED);
        var contracts = new ContractRepository(db); UUID contract = UUID.randomUUID();
        repo.setStatus(inputs.getFirst().id(), SkinInstance.Status.LISTED).join();
        try { contracts.commit(contract, owner, inputs, reward).join(); throw new AssertionError("reserved input consumed"); } catch (java.util.concurrent.CompletionException expected) { }
        require(repo.loadActive(owner).join().stream().noneMatch(s -> s.id().equals(reward.id())), "failed contract inserted reward");
        repo.setStatus(inputs.getFirst().id(), SkinInstance.Status.OWNED).join();
        contracts.commit(contract, owner, inputs, reward).join(); contracts.commit(contract, owner, inputs, reward).join();
        var active = repo.loadActive(owner).join(); require(inputs.stream().noneMatch(input -> active.stream().anyMatch(s -> s.id().equals(input.id()))) && active.stream().filter(s -> s.id().equals(reward.id())).count() == 1, "contract duplicated inputs or output");
        require(contracts.claimAnnouncement(contract).join() && !contracts.claimAnnouncement(contract).join(), "gold announced twice");
        var admin = inputs.stream().map(s -> new SkinInstance(s.id(), owner, s.skinId(), s.floatValue(), s.pattern(), s.wearSeed(), s.statTrak(), 0, s.patternInfo(), s.sourceCase(), SkinInstance.Origin.ADMIN, s.createdAt(), false, SkinInstance.Status.OWNED)).toList();
        expectInvalid(() -> TradeInRules.validate(catalog, admin, false));
        require(TradeInRules.validate(catalog, admin, true).rareSpecial(), "configured admin input rejected");
        var lower = catalog.raritiesOrdered().stream().filter(r -> r.order() < covert.order() && !source.skins(r).isEmpty()).findFirst().orElseThrow();
        var normalDef = source.skins(lower).getFirst();
        var normal = java.util.stream.IntStream.range(0, 10).mapToObj(i -> new SkinInstance(UUID.randomUUID(), owner, normalDef.id(), 0.5, i, i, false, 0, PatternInfo.NONE, source.id(), SkinInstance.Origin.CASE, i, false, SkinInstance.Status.OWNED)).toList();
        require(!TradeInRules.validate(catalog, normal, true).rareSpecial(), "ordinary contract targeted gold");
    }
    private static void expectInvalid(Runnable work) { try { work.run(); throw new AssertionError("invalid contract accepted"); } catch (IllegalArgumentException expected) { } }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
