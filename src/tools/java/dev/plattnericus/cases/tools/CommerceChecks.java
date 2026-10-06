package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.commerce.TradeSession;
import dev.plattnericus.cases.config.PluginSettings;
import dev.plattnericus.cases.skin.PatternInfo;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.storage.CommerceRepository;
import dev.plattnericus.cases.storage.CommerceRepository.Failure;
import dev.plattnericus.cases.storage.CommerceRepository.Transfer;
import dev.plattnericus.cases.storage.Database;
import dev.plattnericus.cases.storage.SkinRepository;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Real SQLite regression tests for payments, ownership, rollback and reload. */
public final class CommerceChecks {
    private CommerceChecks() { }
    public static void run() throws Exception {
        sessionChecks();
        var directory = Files.createTempDirectory("mccases-commerce-");
        var settings = new PluginSettings.Storage("sqlite", "test.db", "", 0, "", "", "", "test_");
        UUID seller = UUID.randomUUID(), buyer = UUID.randomUUID(), competitor = UUID.randomUUID();
        var original = skin(seller, SkinInstance.Origin.CASE);
        UUID persistedListing;
        try {
            try (var db = new Database(settings, directory.toFile(), java.util.logging.Logger.getLogger("CommerceChecks"))) {
                db.open(); var skins = new SkinRepository(db); var market = new CommerceRepository(db);
                skins.insert(original).join(); skins.setEquipped(seller, "knife", original.id()).join();
                expect(market.list(seller, "Seller", original.id(), 0, 10000, 2), Failure.INVALID);
                expect(market.list(buyer, "Buyer", original.id(), 40, 10000, 2), Failure.UNAVAILABLE);
                var listing = market.list(seller, "Seller", original.id(), 400, 10000, 2).join();
                require(skins.equippedAll(seller).join().isEmpty(), "listing must unequip the skin");
                require(skins.loadActive(seller).join().getFirst().status() == SkinInstance.Status.LISTED, "listing reservation");
                expect(market.list(seller, "Seller", original.id(), 400, 10000, 2), Failure.UNAVAILABLE);
                expect(market.cancel(buyer, listing.id()), Failure.UNAVAILABLE);
                expect(market.buy(seller, listing.id(), 400, 1000), Failure.UNAVAILABLE);
                expect(market.buy(buyer, listing.id(), 401, 1000), Failure.UNAVAILABLE);
                var costly = skin(seller, SkinInstance.Origin.ADMIN); skins.insert(costly).join();
                var expensive = market.list(seller, "Seller", costly.id(), 2000, 10000, 2).join();
                expect(market.buy(buyer, expensive.id(), 2000, 1000), Failure.FUNDS);
                require(market.balance(buyer, 1000).join() == 1000, "insufficient funds changed balance");
                var third = skin(seller, SkinInstance.Origin.CASE); skins.insert(third).join();
                expect(market.list(seller, "Seller", third.id(), 1, 10000, 2), Failure.LIMIT);
                var won = market.buy(buyer, listing.id(), 400, 1000);
                var lost = market.buy(competitor, listing.id(), 400, 1000);
                var delivered = won.join(); expect(lost, Failure.UNAVAILABLE);
                require(delivered.owner().equals(buyer) && delivered.id().equals(original.id()), "purchase identity/owner");
                require(delivered.floatValue() == original.floatValue() && delivered.pattern() == original.pattern()
                        && delivered.wearSeed() == original.wearSeed() && delivered.kills() == 42 && delivered.statTrak()
                        && delivered.patternInfo().equals(original.patternInfo()) && delivered.origin() == original.origin()
                        && delivered.createdAt() == original.createdAt() && !delivered.favorite(), "purchase changed roll values");
                require(market.balance(buyer, 1000).join() == 600 && market.balance(seller, 1000).join() == 1400, "atomic payment");
                require(skins.loadActive(competitor).join().isEmpty(), "double purchase duplicated a skin");
                require(skins.removeOwned(seller, original.id()).join() == 0, "stale seller deleted buyer's skin");
                require(skins.updateRoll(original).join() == 0, "stale seller changed buyer's roll");
                var cancelled = market.cancel(seller, expensive.id()).join();
                require(cancelled.status() == SkinInstance.Status.OWNED, "cancel did not restore skin");
                expect(market.buy(buyer, expensive.id(), 2000, 1000), Failure.UNAVAILABLE);
                var testSkin = skin(seller, SkinInstance.Origin.TEST); skins.insert(testSkin).join();
                expect(market.list(seller, "Seller", testSkin.id(), 1, 10000, 20), Failure.INVALID);
                expect(market.trade(seller, buyer, List.of(new Transfer(testSkin.id(), seller, buyer))), Failure.INVALID);
                skins.setEquipped(buyer, "knife", original.id()).join();
                expect(market.trade(buyer, seller, List.of(new Transfer(original.id(), buyer, seller),
                        new Transfer(UUID.randomUUID(), seller, buyer))), Failure.UNAVAILABLE);
                require(skins.loadActive(buyer).join().stream().anyMatch(s -> s.id().equals(original.id()))
                        && skins.equipped(buyer, "knife").join().orElseThrow().equals(original.id()), "partial trade was not rolled back");
                expect(market.trade(seller, buyer, List.of(new Transfer(third.id(), seller, buyer), new Transfer(third.id(), seller, buyer))), Failure.INVALID);
                expect(market.trade(seller, buyer, List.of()), Failure.INVALID);
                var exchanged = market.trade(seller, buyer, List.of(new Transfer(third.id(), seller, buyer), new Transfer(original.id(), buyer, seller))).join();
                require(exchanged.size() == 2 && skins.equippedAll(buyer).join().isEmpty(), "trade did not transfer both sides and unequip");
                expect(market.trade(seller, buyer, List.of(new Transfer(third.id(), seller, buyer))), Failure.UNAVAILABLE);
                market.trade(seller, buyer, List.of(new Transfer(costly.id(), seller, buyer))).join();
                require(skins.loadActive(buyer).join().stream().anyMatch(s -> s.id().equals(costly.id())), "gift failed");
                expect(market.credit(seller, buyer, -1, 1000), Failure.INVALID);
                expect(market.credit(seller, buyer, Long.MAX_VALUE, 1000), Failure.INVALID);
                require(market.credit(seller, buyer, 50, 1000).join() == 650, "admin credit failed");
                persistedListing = market.list(seller, "Seller", original.id(), 100, 10000, 20).join().id();
                require(market.balance(buyer, 99999).join() == 650, "starting balance must be granted only once");
                db.run(c -> {
                    try (var s = c.createStatement(); var rs = s.executeQuery("SELECT COUNT(*) FROM test_commerce_log WHERE kind='BUY'")) {
                        require(rs.next() && rs.getInt(1) == 1, "failed purchase leaked an audit row");
                    }
                    return null;
                }).join();
            }
            try (var reopened = new Database(settings, directory.toFile(), java.util.logging.Logger.getLogger("CommerceChecks"))) {
                reopened.open(); var market = new CommerceRepository(reopened);
                require(market.listings().join().stream().anyMatch(l -> l.id().equals(persistedListing)), "listing lost on restart");
                require(market.balance(buyer, 1000).join() == 650, "balance lost on restart");
                market.buy(buyer, persistedListing, 100, 1000).join();
                reopened.run(c -> {
                    try (var s = c.createStatement()) {
                        s.executeUpdate("DROP TABLE test_market_listings"); s.executeUpdate("DROP TABLE test_wallets");
                        s.executeUpdate("DROP TABLE test_commerce_log"); s.executeUpdate("UPDATE test_schema SET version=1");
                    } return null;
                }).join();
            }
            try (var migrated = new Database(settings, directory.toFile(), java.util.logging.Logger.getLogger("CommerceChecks"))) {
                migrated.open(); require(!new SkinRepository(migrated).loadActive(buyer).join().isEmpty(), "v1 migration lost existing skins");
                require(new CommerceRepository(migrated).balance(buyer, 1000).join() == 1000, "v1 wallet migration failed");
            }
            System.out.println("Verified trade confirmation revisions, gifts, limits; real SQLite marketplace payment, double purchase, rollback, reservations, stale owners, audit, migration and restart persistence.");
        } finally {
            try (var files = Files.walk(directory)) { for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file); }
        }
    }
    private static void sessionChecks() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(); var session = new TradeSession(a, b);
        require(!session.confirm(a, Long.MAX_VALUE), "empty trade confirmed");
        UUID first = UUID.randomUUID(); require(session.toggle(a, first), "add failed");
        int firstRevision = session.revision();
        require(session.reviewSeconds(System.currentTimeMillis()) > 0 && session.reviewSeconds(Long.MAX_VALUE) == 0, "review countdown incorrect");
        require(!session.confirm(a, System.currentTimeMillis()), "confirmation cooldown skipped");
        require(session.confirm(a, Long.MAX_VALUE) && !session.ready(), "one player completed the trade");
        require(session.toggle(b, UUID.randomUUID()) && !session.confirmed(a), "changed counteroffer retained confirmation");
        require(!session.confirm(a, firstRevision, Long.MAX_VALUE) && !session.confirmed(a), "stale offer was confirmed");
        session.confirm(a, Long.MAX_VALUE); session.confirm(b, Long.MAX_VALUE); require(session.ready(), "both confirmed but not ready");
        require(session.unconfirm(a) && !session.confirmed(a) && session.confirmed(b) && !session.ready(), "withdrawing acceptance did not clear only the owner's confirmation");
        require(session.confirm(a, Long.MAX_VALUE) && session.ready(), "accepting again failed");
        require(session.toggle(a, first) && !session.ready(), "removing an item retained confirmation");
        for (int i = 0; i < TradeSession.MAX_ITEMS; i++) require(session.toggle(a, UUID.randomUUID()), "allowed offer rejected");
        require(!session.toggle(a, UUID.randomUUID()), "offer exceeded 12 skins");
        try { session.items(UUID.randomUUID()); throw new IllegalStateException("outsider accessed offer"); } catch (IllegalArgumentException expected) { }
        session.confirm(a, Long.MAX_VALUE); session.confirm(b, Long.MAX_VALUE); session.beginCommit();
        require(!session.toggle(a, first) && !session.confirm(b, Long.MAX_VALUE) && !session.unconfirm(a), "committing offer changed");
    }
    private static SkinInstance skin(UUID owner, SkinInstance.Origin origin) {
        return new SkinInstance(UUID.randomUUID(), owner, "karambit_doppler", 0.012345, 271, 88123, true, 42,
                new PatternInfo("phase2", "Phase 2", "Pink Galaxy", 2, 0xff1234, 99.5), "chroma_case", origin,
                System.currentTimeMillis(), true, SkinInstance.Status.OWNED);
    }
    private static void expect(CompletableFuture<?> future, Failure expected) {
        try { future.join(); throw new IllegalStateException("Expected rejection: " + expected); }
        catch (java.util.concurrent.CompletionException error) {
            Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause();
            require(cause instanceof CommerceRepository.Rejected rejection && rejection.reason() == expected, "Wrong rejection: " + cause);
        }
    }
    private static void require(boolean success, String message) { if (!success) throw new IllegalStateException(message); }
}
