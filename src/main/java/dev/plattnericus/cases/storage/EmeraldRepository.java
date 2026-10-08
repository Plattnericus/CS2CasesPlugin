package dev.plattnericus.cases.storage;

import dev.plattnericus.cases.skin.SkinInstance;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static dev.plattnericus.cases.storage.CommerceRepository.*;

/** SQL half of the item-payment protocol. Player-file receipts provide the other half. */
public final class EmeraldRepository {
    public record Payment(UUID id, UUID listing, UUID instance, UUID buyer, UUID seller, long amount, String state) { }
    public record Claim(UUID id, UUID owner, long remaining, boolean reserved) { }
    public record Delivery(UUID id, UUID claim, UUID owner, long amount, String state) { }
    private final Database db;
    private final CommerceRepository commerce;
    EmeraldRepository(Database db, CommerceRepository commerce) { this.db = db; this.commerce = commerce; }

    public CompletableFuture<Payment> prepare(UUID tx, UUID buyer, UUID listing, long expectedPrice) {
        return db.transaction(c -> {
            Payment prior = payment(c, tx);
            if (prior != null) {
                if (!prior.buyer().equals(buyer) || !prior.listing().equals(listing) || prior.amount() != expectedPrice) throw new Rejected(Failure.INVALID);
                return prior;
            }
            Listing offer = commerce.listing(c, listing);
            if (buyer.equals(offer.skin().owner()) || expectedPrice != offer.price() || expectedPrice < 1 || expectedPrice > MAX_PRICE) throw new Rejected(Failure.UNAVAILABLE);
            try (var ps = c.prepareStatement("SELECT tx_id FROM " + db.table("market_reservations") + " WHERE listing_id=?")) {
                bind(ps, listing); try (var rs = ps.executeQuery()) { if (rs.next()) throw new Rejected(Failure.UNAVAILABLE); }
            }
            execute(c, "INSERT INTO " + db.table("market_reservations") + " (listing_id, tx_id) VALUES (?, ?)", listing, tx);
            execute(c, "INSERT INTO " + db.table("market_payments") + " (tx_id, listing_id, instance_id, buyer, seller, amount, state, created_at) VALUES (?, ?, ?, ?, ?, ?, 'PREPARED', ?)",
                    tx, listing, offer.skin().id(), buyer, offer.skin().owner(), expectedPrice, System.currentTimeMillis());
            return payment(c, tx);
        });
    }

    /** Only invoked after the matching debit receipt and inventory have been saved together. */
    public CompletableFuture<SkinInstance> completePayment(UUID tx, UUID buyer) {
        return db.transaction(c -> {
            Payment p = payment(c, tx);
            if (p == null || !p.buyer().equals(buyer)) throw new Rejected(Failure.INVALID);
            if (p.state().equals("DONE")) return commerce.skin(c, p.instance(), buyer, "OWNED");
            if (!p.state().equals("PREPARED")) throw new Rejected(Failure.UNAVAILABLE);
            Listing listing = commerce.listing(c, p.listing());
            if (!listing.skin().id().equals(p.instance()) || !listing.skin().owner().equals(p.seller()) || listing.price() != p.amount()) throw new Rejected(Failure.UNAVAILABLE);
            commerce.move(c, new Transfer(p.instance(), p.seller(), buyer), "LISTED");
            requireUpdate(c, "DELETE FROM " + db.table("market_listings") + " WHERE listing_id=?", p.listing());
            execute(c, "INSERT INTO " + db.table("emerald_claims") + " (claim_id, owner, amount, remaining, created_at) VALUES (?, ?, ?, ?, ?)",
                    tx, p.seller(), p.amount(), p.amount(), System.currentTimeMillis());
            requireUpdate(c, "UPDATE " + db.table("market_payments") + " SET state='DONE' WHERE tx_id=? AND state='PREPARED'", tx);
            execute(c, "DELETE FROM " + db.table("market_reservations") + " WHERE tx_id=?", tx);
            commerce.audit(c, "BUY_EMERALD", buyer, p.seller(), p.amount(), tx + ":" + p.instance());
            return listing.skin().transferTo(buyer);
        });
    }

    /** Safe only when the loaded player file has no debit receipt. */
    public CompletableFuture<Void> cancelPayment(UUID tx, UUID buyer) {
        return db.transaction(c -> {
            Payment p = payment(c, tx);
            if (p == null || !p.buyer().equals(buyer)) throw new Rejected(Failure.INVALID);
            if (p.state().equals("PREPARED")) {
                execute(c, "UPDATE " + db.table("market_payments") + " SET state='CANCELLED' WHERE tx_id=?", tx);
                execute(c, "DELETE FROM " + db.table("market_reservations") + " WHERE tx_id=?", tx);
            }
            return null;
        });
    }
    public CompletableFuture<List<Payment>> payments(UUID buyer) { return payments(buyer, List.of()); }
    public CompletableFuture<List<Payment>> payments(UUID buyer, List<UUID> receipts) {
        var ids = List.copyOf(receipts);
        return db.run(c -> {
            List<Payment> out = new ArrayList<>();
            try (var ps = c.prepareStatement("SELECT * FROM " + db.table("market_payments") + " WHERE buyer=? AND (state='PREPARED'" + receiptCondition("tx_id", ids) + ")")) {
                bindReceipts(ps, buyer, ids); try (var rs = ps.executeQuery()) { while (rs.next()) out.add(readPayment(rs)); }
            }
            return List.copyOf(out);
        });
    }
    public CompletableFuture<List<Claim>> claims(UUID owner) {
        return db.run(c -> {
            List<Claim> out = new ArrayList<>();
            try (var ps = c.prepareStatement("SELECT * FROM " + db.table("emerald_claims") + " WHERE owner=? AND remaining>0 ORDER BY created_at, claim_id")) {
                bind(ps, owner); try (var rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new Claim(UUID.fromString(rs.getString("claim_id")), owner, rs.getLong("remaining"), rs.getString("reserved_delivery") != null));
                }
            }
            return List.copyOf(out);
        });
    }
    public CompletableFuture<Delivery> prepareDelivery(UUID id, UUID owner, UUID claim, long capacity) {
        return db.transaction(c -> {
            Delivery prior = delivery(c, id);
            if (prior != null) {
                if (!prior.owner().equals(owner) || !prior.claim().equals(claim)) throw new Rejected(Failure.INVALID);
                return prior;
            }
            if (capacity <= 0 || capacity > 2304) throw new Rejected(Failure.INVALID);
            long remaining;
            try (var ps = c.prepareStatement("SELECT remaining, reserved_delivery FROM " + db.table("emerald_claims") + " WHERE claim_id=? AND owner=?")) {
                bind(ps, claim, owner); try (var rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getLong(1) <= 0 || rs.getString(2) != null) throw new Rejected(Failure.UNAVAILABLE);
                    remaining = rs.getLong(1);
                }
            }
            long amount = Math.min(remaining, capacity);
            requireUpdate(c, "UPDATE " + db.table("emerald_claims") + " SET reserved_delivery=? WHERE claim_id=? AND owner=? AND reserved_delivery IS NULL", id, claim, owner);
            execute(c, "INSERT INTO " + db.table("emerald_deliveries") + " (delivery_id, claim_id, owner, amount, state, created_at) VALUES (?, ?, ?, ?, 'PREPARED', ?)", id, claim, owner, amount, System.currentTimeMillis());
            return delivery(c, id);
        });
    }
    public CompletableFuture<Void> completeDelivery(UUID id, UUID owner) {
        return db.transaction(c -> {
            Delivery d = delivery(c, id);
            if (d == null || !d.owner().equals(owner)) throw new Rejected(Failure.INVALID);
            if (d.state().equals("DONE")) return null;
            if (!d.state().equals("PREPARED")) throw new Rejected(Failure.UNAVAILABLE);
            requireUpdate(c, "UPDATE " + db.table("emerald_claims") + " SET remaining=remaining-?, reserved_delivery=NULL WHERE claim_id=? AND owner=? AND reserved_delivery=? AND remaining>=?", d.amount(), d.claim(), owner, id, d.amount());
            requireUpdate(c, "UPDATE " + db.table("emerald_deliveries") + " SET state='DONE' WHERE delivery_id=? AND state='PREPARED'", id);
            commerce.audit(c, "CLAIM_EMERALD", owner, null, d.amount(), id + ":" + d.claim());
            return null;
        });
    }
    public CompletableFuture<Void> cancelDelivery(UUID id, UUID owner) {
        return db.transaction(c -> {
            Delivery d = delivery(c, id);
            if (d == null || !d.owner().equals(owner)) throw new Rejected(Failure.INVALID);
            if (d.state().equals("PREPARED")) {
                execute(c, "UPDATE " + db.table("emerald_claims") + " SET reserved_delivery=NULL WHERE claim_id=? AND reserved_delivery=?", d.claim(), id);
                execute(c, "UPDATE " + db.table("emerald_deliveries") + " SET state='CANCELLED' WHERE delivery_id=?", id);
            }
            return null;
        });
    }
    public CompletableFuture<List<Delivery>> deliveries(UUID owner) { return deliveries(owner, List.of()); }
    public CompletableFuture<List<Delivery>> deliveries(UUID owner, List<UUID> receipts) {
        var ids = List.copyOf(receipts);
        return db.run(c -> {
            List<Delivery> out = new ArrayList<>();
            try (var ps = c.prepareStatement("SELECT * FROM " + db.table("emerald_deliveries") + " WHERE owner=? AND (state='PREPARED'" + receiptCondition("delivery_id", ids) + ")")) {
                bindReceipts(ps, owner, ids); try (var rs = ps.executeQuery()) { while (rs.next()) out.add(readDelivery(rs)); }
            }
            return List.copyOf(out);
        });
    }
    private static String receiptCondition(String column, List<UUID> ids) {
        return ids.isEmpty() ? "" : " OR " + column + " IN (" + String.join(",", java.util.Collections.nCopies(ids.size(), "?")) + ")";
    }
    private static void bindReceipts(java.sql.PreparedStatement ps, UUID owner, List<UUID> ids) throws SQLException {
        ps.setString(1, owner.toString()); for (int i = 0; i < ids.size(); i++) ps.setString(i + 2, ids.get(i).toString());
    }
    private Payment payment(Connection c, UUID id) throws SQLException {
        try (var ps = c.prepareStatement("SELECT * FROM " + db.table("market_payments") + " WHERE tx_id=?")) {
            bind(ps, id); try (var rs = ps.executeQuery()) { return rs.next() ? readPayment(rs) : null; }
        }
    }
    private static Payment readPayment(java.sql.ResultSet rs) throws SQLException {
        return new Payment(UUID.fromString(rs.getString("tx_id")), UUID.fromString(rs.getString("listing_id")), UUID.fromString(rs.getString("instance_id")), UUID.fromString(rs.getString("buyer")), UUID.fromString(rs.getString("seller")), rs.getLong("amount"), rs.getString("state"));
    }
    private Delivery delivery(Connection c, UUID id) throws SQLException {
        try (var ps = c.prepareStatement("SELECT * FROM " + db.table("emerald_deliveries") + " WHERE delivery_id=?")) {
            bind(ps, id); try (var rs = ps.executeQuery()) { return rs.next() ? readDelivery(rs) : null; }
        }
    }
    private static Delivery readDelivery(java.sql.ResultSet rs) throws SQLException {
        return new Delivery(UUID.fromString(rs.getString("delivery_id")), UUID.fromString(rs.getString("claim_id")), UUID.fromString(rs.getString("owner")), rs.getLong("amount"), rs.getString("state"));
    }
    private static void execute(Connection c, String sql, Object... values) throws SQLException {
        try (var ps = c.prepareStatement(sql)) { bind(ps, values); ps.executeUpdate(); }
    }
}
