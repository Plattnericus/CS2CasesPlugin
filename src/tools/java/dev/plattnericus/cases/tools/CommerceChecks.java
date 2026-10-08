package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.commerce.TradeSession;
import dev.plattnericus.cases.config.PluginSettings;
import dev.plattnericus.cases.skin.PatternInfo;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.storage.*;
import dev.plattnericus.cases.storage.CommerceRepository.Failure;
import dev.plattnericus.cases.storage.CommerceRepository.Transfer;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Real SQLite checks for the durable item protocol, transactions, failures and migration. */
public final class CommerceChecks {
    private CommerceChecks() { }
    public static void run() throws Exception {
        sessionChecks();
        var directory = Files.createTempDirectory("mccases-commerce-");
        var settings = new PluginSettings.Storage("sqlite", "test.db", "", 0, "", "", "", "test_");
        UUID seller = UUID.randomUUID(), buyer = UUID.randomUUID(), competitor = UUID.randomUUID();
        var original = skin(seller, SkinInstance.Origin.CASE);
        UUID pendingPayment, pendingDelivery;
        try {
            try (var db = new Database(settings, directory.toFile(), java.util.logging.Logger.getLogger("CommerceChecks"))) {
                db.open(); var skins = new SkinRepository(db); var market = new CommerceRepository(db); var emeralds = market.emeralds();
                skins.insert(original).join(); skins.setEquipped(seller, "knife", original.id()).join();
                for (long price : new long[]{0, -1, Long.MIN_VALUE, Long.MAX_VALUE, 10001}) expect(market.list(seller, "Seller", original.id(), price, 10000, 2), Failure.INVALID);
                expect(market.list(buyer, "Buyer", original.id(), 40, 10000, 2), Failure.UNAVAILABLE);
                var listing = market.list(seller, "Seller", original.id(), 400, 10000, 2).join();
                require(skins.equippedAll(seller).join().isEmpty(), "listing must unequip");
                require(skins.loadActive(seller).join().getFirst().status() == SkinInstance.Status.LISTED, "listing reservation");
                expect(market.list(seller, "Seller", original.id(), 400, 10000, 2), Failure.UNAVAILABLE);
                expect(market.cancel(buyer, listing.id()), Failure.UNAVAILABLE);
                expect(emeralds.prepare(UUID.randomUUID(), seller, listing.id(), 400), Failure.UNAVAILABLE);
                expect(emeralds.prepare(UUID.randomUUID(), buyer, listing.id(), 401), Failure.UNAVAILABLE);
                UUID tx = UUID.randomUUID();
                var won = emeralds.prepare(tx, buyer, listing.id(), 400);
                var lost = emeralds.prepare(UUID.randomUUID(), competitor, listing.id(), 400);
                won.join(); expect(lost, Failure.UNAVAILABLE);
                require(emeralds.prepare(tx, buyer, listing.id(), 400).join().id().equals(tx), "preparation must be idempotent");
                expect(emeralds.prepare(tx, competitor, listing.id(), 400), Failure.INVALID);
                expect(market.cancel(seller, listing.id()), Failure.UNAVAILABLE);
                require(skins.loadActive(buyer).join().isEmpty() && emeralds.claims(seller).join().isEmpty(), "prepare transferred ownership or paid seller");
                var delivered = emeralds.completePayment(tx, buyer).join();
                require(emeralds.completePayment(tx, buyer).join().id().equals(delivered.id()), "completion not idempotent");
                require(delivered.owner().equals(buyer) && delivered.id().equals(original.id()), "purchase identity/owner");
                require(delivered.floatValue() == original.floatValue() && delivered.pattern() == original.pattern()
                        && delivered.wearSeed() == original.wearSeed() && delivered.kills() == 42 && delivered.statTrak()
                        && delivered.patternInfo().equals(original.patternInfo()) && delivered.origin() == original.origin()
                        && delivered.createdAt() == original.createdAt() && !delivered.favorite(), "purchase changed roll values");
                require(emeralds.claims(seller).join().size() == 1 && emeralds.claims(seller).join().getFirst().remaining() == 400, "sale did not produce exact single item claim");
                expect(emeralds.prepare(UUID.randomUUID(), competitor, listing.id(), 400), Failure.UNAVAILABLE);
                require(skins.removeOwned(seller, original.id()).join() == 0 && skins.updateRoll(original).join() == 0, "stale seller changed buyer's skin");
                UUID delivery = UUID.randomUUID();
                var partial = emeralds.prepareDelivery(delivery, seller, tx, 64).join();
                require(partial.amount() == 64 && emeralds.claims(seller).join().getFirst().remaining() == 400, "delivery prematurely debited claim");
                expect(emeralds.prepareDelivery(UUID.randomUUID(), seller, tx, 64), Failure.UNAVAILABLE);
                expect(emeralds.completeDelivery(delivery, buyer), Failure.INVALID);
                emeralds.completeDelivery(delivery, seller).join(); emeralds.completeDelivery(delivery, seller).join();
                require(emeralds.claims(seller).join().getFirst().remaining() == 336, "repeated delivery duplicated debit");
                UUID cancelled = UUID.randomUUID(); emeralds.prepareDelivery(cancelled, seller, tx, 100).join();
                emeralds.cancelDelivery(cancelled, seller).join(); emeralds.cancelDelivery(cancelled, seller).join();
                require(emeralds.claims(seller).join().getFirst().remaining() == 336, "cancel lost emeralds");
                expect(emeralds.completeDelivery(cancelled, seller), Failure.UNAVAILABLE);
                expect(emeralds.prepareDelivery(UUID.randomUUID(), seller, tx, Long.MAX_VALUE), Failure.INVALID);
                expect(emeralds.prepareDelivery(UUID.randomUUID(), seller, tx, 0), Failure.INVALID);
                pendingDelivery = UUID.randomUUID(); emeralds.prepareDelivery(pendingDelivery, seller, tx, 2304).join();
                var second = skin(seller, SkinInstance.Origin.CASE); skins.insert(second).join();
                var offer = market.list(seller, "Seller", second.id(), 20, 10000, 2).join();
                UUID cancelledPayment = UUID.randomUUID(); emeralds.prepare(cancelledPayment, buyer, offer.id(), 20).join();
                emeralds.cancelPayment(cancelledPayment, buyer).join();
                expect(emeralds.completePayment(cancelledPayment, buyer), Failure.UNAVAILABLE);
                pendingPayment = UUID.randomUUID(); emeralds.prepare(pendingPayment, buyer, offer.id(), 20).join();
                var third = skin(seller, SkinInstance.Origin.CASE); skins.insert(third).join();
                expect(market.trade(seller, buyer, List.of(new Transfer(second.id(), seller, buyer))), Failure.UNAVAILABLE);
                skins.setEquipped(buyer, "knife", original.id()).join();
                expect(market.trade(buyer, seller, List.of(new Transfer(original.id(), buyer, seller), new Transfer(UUID.randomUUID(), seller, buyer))), Failure.UNAVAILABLE);
                require(skins.loadActive(buyer).join().stream().anyMatch(s -> s.id().equals(original.id())) && skins.equipped(buyer, "knife").join().orElseThrow().equals(original.id()), "partial trade was not rolled back");
                expect(market.trade(seller, buyer, List.of(new Transfer(third.id(), seller, buyer), new Transfer(third.id(), seller, buyer))), Failure.INVALID);
                var exchanged = market.trade(seller, buyer, List.of(new Transfer(third.id(), seller, buyer), new Transfer(original.id(), buyer, seller))).join();
                require(exchanged.size() == 2 && skins.equippedAll(buyer).join().isEmpty(), "trade did not transfer both sides");
                db.run(c -> { try (var statement = c.createStatement()) {
                    statement.executeUpdate("INSERT INTO test_wallets (owner, balance) VALUES ('" + buyer + "', 12345)");
                    try (var rs = statement.executeQuery("SELECT COUNT(*) FROM test_commerce_log WHERE kind='BUY_EMERALD'")) { require(rs.next() && rs.getInt(1) == 1, "duplicate purchase audit"); }
                } return null; }).join();
                require(market.legacyBalance(buyer).join() == 12345, "archive was changed");
                injectedFailures(db, skins, market, emeralds);
            }
            try (var db = new Database(settings, directory.toFile(), java.util.logging.Logger.getLogger("CommerceChecks"))) {
                db.open(); var market = new CommerceRepository(db); var emeralds = market.emeralds();
                require(emeralds.payments(buyer).join().stream().anyMatch(p -> p.id().equals(pendingPayment) && p.state().equals("PREPARED")), "prepared purchase lost on restart");
                emeralds.completePayment(pendingPayment, buyer).join();
                emeralds.completeDelivery(pendingDelivery, seller).join(); emeralds.completeDelivery(pendingDelivery, seller).join();
                require(emeralds.claims(seller).join().stream().mapToLong(EmeraldRepository.Claim::remaining).sum() == 20, "restart delivery duplicated or lost items");
                require(market.legacyBalance(buyer).join() == 12345, "Coin archive modified by emerald purchase");
                // Simulate a genuine v2 Coin listing before upgrade.
                var legacySkin = skin(seller, SkinInstance.Origin.CASE); new SkinRepository(db).insert(legacySkin).join();
                market.list(seller, "Seller", legacySkin.id(), 500, 10000, 20).join();
                db.run(c -> { try (var statement = c.createStatement()) { statement.executeUpdate("UPDATE test_schema SET version=2"); } return null; }).join();
            }
            try (var db = new Database(settings, directory.toFile(), java.util.logging.Logger.getLogger("CommerceChecks"))) {
                db.open(); var market = new CommerceRepository(db);
                require(market.listings().join().isEmpty(), "Coin prices were reinterpreted as emeralds");
                require(market.legacyBalance(buyer).join() == 12345, "upgrade lost legacy coins");
                require(new SkinRepository(db).loadActive(seller).join().stream().noneMatch(s -> s.status() == SkinInstance.Status.LISTED), "upgrade lost listed skins");
                db.run(c -> { try (var statement = c.createStatement(); var rs = statement.executeQuery("SELECT COUNT(*) FROM test_legacy_market_listings")) { require(rs.next() && rs.getInt(1) == 1, "legacy listing not archived"); } return null; }).join();
            }
            v1Migration(directory);
            System.out.println("PASS: price bounds, item-payment prepare/complete/cancel, concurrent buyers, exact identity transfer, partial/repeated claims, injected SQL rollback/retry, reservations, restart recovery, v1/v2 legacy migration.");
        } finally {
            try (var files = Files.walk(directory)) { for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file); }
        }
    }
    private static void injectedFailures(Database db, SkinRepository skins, CommerceRepository market, EmeraldRepository emeralds) {
        UUID seller = UUID.randomUUID(), buyer = UUID.randomUUID(), tx = UUID.randomUUID();
        var original = skin(seller, SkinInstance.Origin.CASE); skins.insert(original).join();
        var listing = market.list(seller, "FaultSeller", original.id(), 9, 10000, 20).join();
        emeralds.prepare(tx, buyer, listing.id(), 9).join();
        db.run(c -> { try (var s = c.createStatement()) { s.executeUpdate("CREATE TRIGGER test_fail_claim BEFORE INSERT ON test_emerald_claims BEGIN SELECT RAISE(ABORT, 'injected claim failure'); END"); } return null; }).join();
        sqlFails(emeralds.completePayment(tx, buyer));
        require(market.listings().join().stream().anyMatch(l -> l.id().equals(listing.id()))
                && skins.loadActive(buyer).join().isEmpty() && emeralds.claims(seller).join().isEmpty(), "SQL failure partially transferred payment");
        require(emeralds.payments(buyer).join().getFirst().state().equals("PREPARED"), "failed payment lost recovery state");
        db.run(c -> { try (var s = c.createStatement()) { s.executeUpdate("DROP TRIGGER test_fail_claim"); } return null; }).join();
        emeralds.completePayment(tx, buyer).join();
        UUID delivery = UUID.randomUUID(); emeralds.prepareDelivery(delivery, seller, tx, 9).join();
        db.run(c -> { try (var s = c.createStatement()) { s.executeUpdate("CREATE TRIGGER test_fail_delivery BEFORE UPDATE ON test_emerald_claims BEGIN SELECT RAISE(ABORT, 'injected delivery failure'); END"); } return null; }).join();
        sqlFails(emeralds.completeDelivery(delivery, seller));
        require(emeralds.claims(seller).join().getFirst().remaining() == 9 && emeralds.deliveries(seller).join().getFirst().state().equals("PREPARED"), "SQL failure partially acknowledged delivery");
        db.run(c -> { try (var s = c.createStatement()) { s.executeUpdate("DROP TRIGGER test_fail_delivery"); } return null; }).join();
        emeralds.completeDelivery(delivery, seller).join(); emeralds.completeDelivery(delivery, seller).join();
        require(emeralds.claims(seller).join().isEmpty(), "retry did not finish exact delivery");
    }
    private static void sqlFails(CompletableFuture<?> operation) {
        try { operation.join(); throw new AssertionError("Injected SQL failure was ignored"); }
        catch (java.util.concurrent.CompletionException error) { require(error.getCause() instanceof java.sql.SQLException, "Unexpected SQL failure: " + error); }
    }
    private static void v1Migration(java.nio.file.Path directory) throws Exception {
        var settings = new PluginSettings.Storage("sqlite", "v1.db", "", 0, "", "", "", "test_");
        var original = skin(UUID.randomUUID(), SkinInstance.Origin.CASE);
        try (var db = new Database(settings, directory.toFile(), java.util.logging.Logger.getLogger("CommerceChecks"))) {
            db.open(); new SkinRepository(db).insert(original).join();
            db.run(c -> { try (var s = c.createStatement()) {
                for (String table : List.of("market_listings", "wallets", "commerce_log", "legacy_market_listings", "market_payments", "market_reservations", "emerald_claims", "emerald_deliveries", "trade_contracts")) s.executeUpdate("DROP TABLE test_" + table);
                s.executeUpdate("UPDATE test_schema SET version=1");
            } return null; }).join();
        }
        try (var db = new Database(settings, directory.toFile(), java.util.logging.Logger.getLogger("CommerceChecks"))) {
            db.open(); var migrated = new SkinRepository(db).loadActive(original.owner()).join().getFirst();
            require(migrated.id().equals(original.id()) && migrated.floatValue() == original.floatValue() && migrated.patternInfo().equals(original.patternInfo()) && migrated.kills() == original.kills(), "v1 migration modified skin data");
            require(new CommerceRepository(db).listings().join().isEmpty(), "v1 created listings");
            db.run(c -> { try (var s = c.createStatement(); var rs = s.executeQuery("SELECT version FROM test_schema")) { require(rs.next() && rs.getInt(1) == 3, "v1 did not migrate to v3"); } return null; }).join();
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
