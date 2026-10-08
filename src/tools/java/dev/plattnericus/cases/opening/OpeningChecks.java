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
        var catalog = new CatalogLoader(new File(root, "catalog"), new TextureStore(new File(root, "textures"))).load(1).catalog();
        var def = catalog.caseDefinition("kilowatt_case");
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var registry = new OpeningSessions(); var sessions = new java.util.ArrayList<OpeningSession>();
        for (int i = 0; i < 8; i++) {
            var session = new OpeningSession(a, def, catalog, false, false);
            require(registry.add(session, 8, 10), "independent opening rejected"); sessions.add(session);
        }
        require(registry.forPlayer(a).size() == 8 && sessions.stream().map(s -> s.openingId).distinct().count() == 8 && sessions.stream().map(s -> s.lane).distinct().count() == 8, "opening IDs or lanes overwritten");
        require(!registry.add(new OpeningSession(a, def, catalog, false, false), 8, 10), "per-player capacity exceeded");
        for (int i = 0; i < 2; i++) require(registry.add(new OpeningSession(b, def, catalog, false, false), 8, 10), "other player blocked");
        require(!registry.add(new OpeningSession(UUID.randomUUID(), def, catalog, false, false), 8, 10), "global capacity exceeded");
        registry.remove(sessions.getFirst().openingId, sessions.get(1)); require(registry.size() == 10, "wrong session removed another result");
        registry.remove(sessions.getFirst().openingId, sessions.getFirst()); require(registry.size() == 9, "cleanup failed");
        var replacement = new OpeningSession(a, def, catalog, false, false); require(registry.add(replacement, 8, 10) && replacement.lane == 0, "released lane was not reusable");
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
