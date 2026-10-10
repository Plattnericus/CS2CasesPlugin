package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.catalog.*;
import dev.plattnericus.cases.config.*;
import dev.plattnericus.cases.items.SkinFormatter;
import dev.plattnericus.cases.render.*;
import dev.plattnericus.cases.skin.*;
import dev.plattnericus.cases.storage.*;
import dev.plattnericus.cases.util.Text;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

/** Fractional alpha coverage and durable provenance across actual trades and v3 upgrades. */
public final class SkinPresentationChecks {
    private SkinPresentationChecks() { }
    public static void run(File root) throws Exception {
        // A fractional reduction must account for half a texel instead of discarding it.
        var filtered = new ArgbImage(3, 1, new int[]{0xffff0000, 0x000000ff, 0xffff0000}).scaledTo(2, 1);
        require(filtered.get(0, 0) == 0xaaff0000 && filtered.get(1, 0) == 0xaaff0000, "fractional coverage/dark alpha fringe");
        var catalog = new CatalogLoader(new File(root, "catalog"), new TextureStore(new File(root, "textures"))).load(1).catalog();
        var def = catalog.skins().stream().filter(SkinDefinition::isKnife).findFirst().orElseThrow();
        var directory = Files.createTempDirectory("mccases-provenance-");
        var settings = new PluginSettings.Storage("sqlite", "test.db", "", 0, "", "", "", "test_");
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        List<SkinInstance> originals = new ArrayList<>();
        for (var origin : List.of(SkinInstance.Origin.CASE, SkinInstance.Origin.TRADE_IN, SkinInstance.Origin.ADMIN, SkinInstance.Origin.ADMIN_TRADE_IN))
            originals.add(new SkinInstance(UUID.randomUUID(), first, def.id(), .12, 42, 123, true, 7, PatternInfo.NONE,
                    "chroma_case", origin, 12345, true, SkinInstance.Status.OWNED));
        var untouched = new SkinInstance(first, first, def.id(), .2, 1, 456, false, 0, PatternInfo.NONE, "chroma_case",
                SkinInstance.Origin.CASE, 67890, false, SkinInstance.Status.OWNED);
        try {
            try (var db = new Database(settings, directory.toFile(), java.util.logging.Logger.getLogger("SkinPresentationChecks"))) {
                db.open(); var skins = new SkinRepository(db); var commerce = new CommerceRepository(db);
                skins.insert(untouched).join();
                for (var original : originals) {
                    skins.insert(original).join();
                    var moved = commerce.trade(first, second, List.of(new CommerceRepository.Transfer(original.id(), first, second))).join().getFirst();
                    require(moved.traded() && moved.origin() == original.origin() && moved.sourceCase().equals(original.sourceCase()), "trade erased original/admin provenance");
                    require(moved.id().equals(original.id()) && moved.floatValue() == original.floatValue() && moved.kills() == 7 && !moved.favorite(), "trade altered identity or roll");
                    var journal = InstanceCodec.decode(InstanceCodec.encode(moved));
                    require(journal != null && journal.traded() && journal.origin() == moved.origin(), "journal lost trade provenance");
                    require(!InstanceCodec.decode(InstanceCodec.encode(original).replace(",\"traded\":false", "")).traded(), "legacy journal no longer compatible");
                    var listing = commerce.list(second, "Second", moved.id(), 1, 10000, 20).join();
                    require(listing.skin().traded() && commerce.cancel(second, listing.id()).join().traded(), "listing/cancel lost trade provenance");
                    var back = commerce.trade(first, second, List.of(new CommerceRepository.Transfer(moved.id(), second, first))).join().getFirst();
                    require(back.traded() && back.origin() == original.origin(), "repeat trade nested or lost provenance");
                    for (String language : List.of("en", "de", "it")) {
                        var path = new File(root, "messages_" + language + ".yml");
                        var messages = new Messages(path, Files.newInputStream(path.toPath()), Files.newInputStream(new File(root, "messages_en.yml").toPath()));
                        var formatter = new SkinFormatter(messages, catalog, 4, 8, "dd.MM.yyyy");
                        String source = original.origin().tradeIn() ? "TRADE IN" : catalog.caseDefinition("chroma_case").name();
                        require(formatter.lore(def, back, false).stream().map(Text::plain).anyMatch(line -> line.contains("(" + source + ")")), "normal lore hides original source after trade " + language);
                        require(Text.plain(formatter.line("skin.lore.source", def, back)).contains("(" + source + ")"), "precise source missing " + language);
                    }
                }
                // Recreate a genuine schema-v3 table without the added column. The audit must backfill skins only.
                db.run(c -> { try (var s = c.createStatement()) {
                    s.executeUpdate("ALTER TABLE test_skins DROP COLUMN traded");
                    s.executeUpdate("UPDATE test_schema SET version=3");
                } return null; }).join();
            }
            for (int restart = 0; restart < 2; restart++) try (var db = new Database(settings, directory.toFile(), java.util.logging.Logger.getLogger("SkinPresentationChecks"))) {
                db.open(); var loaded = new SkinRepository(db).loadActive(first).join();
                require(loaded.size() == 5 && loaded.stream().filter(s -> !s.id().equals(untouched.id())).allMatch(SkinInstance::traded), "v3 backfill/restart lost trade marker");
                require(!loaded.stream().filter(s -> s.id().equals(untouched.id())).findFirst().orElseThrow().traded(), "actor UUID mistaken for a traded skin");
                for (var original : originals) require(loaded.stream().anyMatch(s -> s.id().equals(original.id()) && s.origin() == original.origin() && s.sourceCase().equals(original.sourceCase())), "v3 upgrade changed original provenance");
            }
        } finally { try (var files = Files.walk(directory)) { for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file); } }
        System.out.println("PASS: fractional alpha filtering, CASE/TRADE_IN/ADMIN/ADMIN_TRADE_IN source lore in en/de/it, direct/repeat trade, listing/cancel, old/new journal, real v3 SQL migration/audit backfill and repeated restart.");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
