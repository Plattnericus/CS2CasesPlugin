package dev.plattnericus.cases.storage;

import dev.plattnericus.cases.skin.SkinInstance;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Skin ownership and wallet changes commit together on the database worker. */
public final class CommerceRepository {
    public static final long MAX_BALANCE = 9_000_000_000_000L;
    public record Listing(UUID id, SkinInstance skin, String sellerName, long price, long createdAt) { }
    public record Transfer(UUID instance, UUID from, UUID to) { }
    public enum Failure { UNAVAILABLE, FUNDS, LIMIT, INVALID }
    public static final class Rejected extends SQLException {
        private final Failure reason;
        public Rejected(Failure reason) { super(reason.name()); this.reason = reason; }
        public Failure reason() { return reason; }
    }

    private final Database db;
    public CommerceRepository(Database db) { this.db = db; }

    public CompletableFuture<List<Listing>> listings() {
        return db.run(c -> {
            List<Listing> out = new ArrayList<>();
            try (var ps = c.prepareStatement("SELECT s.*, l.listing_id, l.seller_name, l.price, l.created_at AS listed_at FROM "
                    + db.table("market_listings") + " l JOIN " + db.table("skins")
                    + " s ON s.instance_id=l.instance_id AND s.owner=l.seller WHERE s.status='LISTED' ORDER BY l.created_at DESC, l.listing_id")) {
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new Listing(UUID.fromString(rs.getString("listing_id")),
                            SkinRepository.read(rs), rs.getString("seller_name"), rs.getLong("price"), rs.getLong("listed_at")));
                }
            }
            return List.copyOf(out);
        });
    }

    public CompletableFuture<Listing> list(UUID seller, String name, UUID instance, long price, long maxPrice, int limit) {
        return db.transaction(c -> {
            if (price < 1 || price > maxPrice) throw new Rejected(Failure.INVALID);
            try (var ps = c.prepareStatement("SELECT COUNT(*) FROM " + db.table("market_listings") + " WHERE seller=?")) {
                ps.setString(1, seller.toString());
                try (var rs = ps.executeQuery()) { if (rs.next() && rs.getInt(1) >= limit) throw new Rejected(Failure.LIMIT); }
            }
            SkinInstance skin = skin(c, instance, seller, "OWNED");
            if (skin.origin() == SkinInstance.Origin.TEST) throw new Rejected(Failure.INVALID);
            UUID id = UUID.randomUUID(); long now = System.currentTimeMillis();
            requireUpdate(c, "UPDATE " + db.table("skins") + " SET status='LISTED' WHERE instance_id=? AND owner=? AND status='OWNED'", instance, seller);
            unequip(c, seller, instance);
            try (var ps = c.prepareStatement("INSERT INTO " + db.table("market_listings")
                    + " (listing_id, instance_id, seller, seller_name, price, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
                bind(ps, id, instance, seller, name, price, now); ps.executeUpdate();
            }
            audit(c, "LIST", seller, null, price, id + ":" + instance);
            return new Listing(id, skin.copyWithStatus(SkinInstance.Status.LISTED), name, price, now);
        });
    }

    public CompletableFuture<SkinInstance> cancel(UUID seller, UUID id) {
        return db.transaction(c -> {
            Listing listing = listing(c, id);
            if (!listing.skin().owner().equals(seller)) throw new Rejected(Failure.UNAVAILABLE);
            requireUpdate(c, "UPDATE " + db.table("skins") + " SET status='OWNED' WHERE instance_id=? AND owner=? AND status='LISTED'",
                    listing.skin().id(), seller);
            requireUpdate(c, "DELETE FROM " + db.table("market_listings") + " WHERE listing_id=? AND seller=?", id, seller);
            audit(c, "CANCEL", seller, null, 0, id.toString());
            return listing.skin().copyWithStatus(SkinInstance.Status.OWNED);
        });
    }

    public CompletableFuture<SkinInstance> buy(UUID buyer, UUID id, long expectedPrice, long startingBalance) {
        return db.transaction(c -> {
            Listing listing = listing(c, id);
            UUID seller = listing.skin().owner();
            if (seller.equals(buyer) || listing.price() != expectedPrice) throw new Rejected(Failure.UNAVAILABLE);
            long buyerBalance = account(c, buyer, startingBalance), sellerBalance = account(c, seller, startingBalance);
            if (buyerBalance < listing.price()) throw new Rejected(Failure.FUNDS);
            if (sellerBalance > MAX_BALANCE - listing.price()) throw new Rejected(Failure.LIMIT);
            requireUpdate(c, "UPDATE " + db.table("wallets") + " SET balance=balance-? WHERE owner=? AND balance>=?", listing.price(), buyer, listing.price());
            requireUpdate(c, "UPDATE " + db.table("wallets") + " SET balance=balance+? WHERE owner=? AND balance<=?", listing.price(), seller, MAX_BALANCE - listing.price());
            move(c, new Transfer(listing.skin().id(), seller, buyer), "LISTED");
            requireUpdate(c, "DELETE FROM " + db.table("market_listings") + " WHERE listing_id=?", id);
            audit(c, "BUY", buyer, seller, listing.price(), id + ":" + listing.skin().id());
            return listing.skin().transferTo(buyer);
        });
    }

    public CompletableFuture<List<SkinInstance>> trade(UUID first, UUID second, List<Transfer> transfers) {
        List<Transfer> snapshot = List.copyOf(transfers);
        return db.transaction(c -> {
            if (first.equals(second) || snapshot.isEmpty() || snapshot.size() > 24
                    || snapshot.stream().map(Transfer::instance).distinct().count() != snapshot.size()) throw new Rejected(Failure.INVALID);
            List<SkinInstance> results = new ArrayList<>();
            for (Transfer t : snapshot) {
                if (!(t.from().equals(first) && t.to().equals(second) || t.from().equals(second) && t.to().equals(first)))
                    throw new Rejected(Failure.INVALID);
                SkinInstance skin = skin(c, t.instance(), t.from(), "OWNED");
                if (skin.origin() == SkinInstance.Origin.TEST) throw new Rejected(Failure.INVALID);
                move(c, t, "OWNED"); results.add(skin.transferTo(t.to()));
            }
            audit(c, "TRADE", first, second, 0, snapshot.toString());
            return List.copyOf(results);
        });
    }

    public CompletableFuture<Long> balance(UUID owner, long startingBalance) {
        return db.transaction(c -> account(c, owner, startingBalance));
    }

    public CompletableFuture<Long> credit(UUID admin, UUID owner, long amount, long startingBalance) {
        return db.transaction(c -> {
            long balance = account(c, owner, startingBalance);
            if (amount < 1 || amount > MAX_BALANCE - balance) throw new Rejected(Failure.INVALID);
            requireUpdate(c, "UPDATE " + db.table("wallets") + " SET balance=balance+? WHERE owner=?", amount, owner);
            audit(c, "CREDIT", admin, owner, amount, ""); return balance + amount;
        });
    }

    private long account(Connection c, UUID owner, long startingBalance) throws SQLException {
        if (startingBalance < 0 || startingBalance > MAX_BALANCE) throw new Rejected(Failure.INVALID);
        try (var ps = c.prepareStatement(db.dialect().insertIgnore() + db.table("wallets") + " (owner, balance) VALUES (?, ?)")) {
            bind(ps, owner, startingBalance); ps.executeUpdate();
        }
        try (var ps = c.prepareStatement("SELECT balance FROM " + db.table("wallets") + " WHERE owner=?")) {
            bind(ps, owner); try (var rs = ps.executeQuery()) { if (rs.next()) return rs.getLong(1); }
        }
        throw new SQLException("Wallet missing after creation");
    }

    private SkinInstance skin(Connection c, UUID id, UUID owner, String status) throws SQLException {
        try (var ps = c.prepareStatement("SELECT * FROM " + db.table("skins") + " WHERE instance_id=? AND owner=? AND status=?")) {
            bind(ps, id, owner, status); try (var rs = ps.executeQuery()) { if (rs.next()) return SkinRepository.read(rs); }
        }
        throw new Rejected(Failure.UNAVAILABLE);
    }

    private Listing listing(Connection c, UUID id) throws SQLException {
        try (var ps = c.prepareStatement("SELECT * FROM " + db.table("market_listings") + " WHERE listing_id=?")) {
            bind(ps, id); try (var rs = ps.executeQuery()) {
                if (rs.next()) return new Listing(id, skin(c, UUID.fromString(rs.getString("instance_id")),
                        UUID.fromString(rs.getString("seller")), "LISTED"), rs.getString("seller_name"), rs.getLong("price"), rs.getLong("created_at"));
            }
        }
        throw new Rejected(Failure.UNAVAILABLE);
    }

    private void move(Connection c, Transfer t, String status) throws SQLException {
        requireUpdate(c, "UPDATE " + db.table("skins") + " SET owner=?, status='OWNED', favorite=0 WHERE instance_id=? AND owner=? AND status=?",
                t.to(), t.instance(), t.from(), status);
        unequip(c, t.from(), t.instance());
    }

    private void unequip(Connection c, UUID owner, UUID instance) throws SQLException {
        try (var ps = c.prepareStatement("DELETE FROM " + db.table("equipped") + " WHERE owner=? AND instance_id=?")) { bind(ps, owner, instance); ps.executeUpdate(); }
    }

    private void audit(Connection c, String kind, UUID actor, UUID other, long amount, String details) throws SQLException {
        try (var ps = c.prepareStatement("INSERT INTO " + db.table("commerce_log")
                + " (event_id, kind, actor, counterparty, amount, details, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            bind(ps, UUID.randomUUID(), kind, actor, other, amount, details, System.currentTimeMillis()); ps.executeUpdate();
        }
    }

    private static void requireUpdate(Connection c, String query, Object... values) throws SQLException {
        try (var ps = c.prepareStatement(query)) { bind(ps, values); if (ps.executeUpdate() != 1) throw new Rejected(Failure.UNAVAILABLE); }
    }
    private static void bind(PreparedStatement ps, Object... values) throws SQLException {
        for (int i = 0; i < values.length; i++) ps.setObject(i + 1, values[i] instanceof UUID id ? id.toString() : values[i]);
    }
}
