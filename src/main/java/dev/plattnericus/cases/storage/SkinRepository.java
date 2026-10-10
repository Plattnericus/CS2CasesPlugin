package dev.plattnericus.cases.storage;

import dev.plattnericus.cases.skin.PatternInfo;
import dev.plattnericus.cases.skin.SkinInstance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** All persistence for skin instances, equipped items and the opening audit log. */
public final class SkinRepository {

    private static final String COLUMNS = "instance_id, owner, skin_id, float_value, pattern, wear_seed, stattrak, "
            + "stattrak_kills, variant, variant_name, classification, class_tier, class_color, fade_percent, source, "
            + "origin, created_at, favorite, status, traded";

    private final Database db;
    private final String skins;
    private final String equipped;
    private final String openings;

    public SkinRepository(Database db) {
        this.db = db;
        this.skins = db.table("skins");
        this.equipped = db.table("equipped");
        this.openings = db.table("openings");
    }

    // ------------------------------------------------------------------ reads

    /** Owned and pending instances of a player. */
    public CompletableFuture<List<SkinInstance>> loadActive(UUID owner) {
        return db.run(c -> {
            List<SkinInstance> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM " + skins
                    + " WHERE owner = ? AND status <> 'REMOVED' ORDER BY created_at")) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(read(rs));
                    }
                }
            }
            return out;
        });
    }

    public CompletableFuture<Optional<UUID>> equipped(UUID owner, String slot) {
        return db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT instance_id FROM " + equipped + " WHERE owner = ? AND slot = ?")) {
                ps.setString(1, owner.toString());
                ps.setString(2, slot);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty();
                }
            }
        });
    }

    /** Every equipped slot of a player (slot id -> instance). */
    public CompletableFuture<java.util.Map<String, UUID>> equippedAll(UUID owner) {
        return db.run(c -> {
            java.util.Map<String, UUID> out = new java.util.HashMap<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT slot, instance_id FROM " + equipped + " WHERE owner = ?")) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.put(rs.getString(1), UUID.fromString(rs.getString(2)));
                    }
                }
            }
            return out;
        });
    }

    public CompletableFuture<List<OpeningRecord>> history(UUID owner, int limit) {
        return db.run(c -> {
            List<OpeningRecord> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT opening_id, owner, owner_name, case_id, instance_id, skin_id, "
                    + "rarity, float_value, pattern, stattrak, test, opened_at FROM " + openings
                    + " WHERE owner = ? ORDER BY opened_at DESC LIMIT ?")) {
                ps.setString(1, owner.toString());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new OpeningRecord(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                                rs.getString(3), rs.getString(4), UUID.fromString(rs.getString(5)), rs.getString(6),
                                rs.getString(7), rs.getDouble(8), rs.getInt(9), rs.getInt(10) != 0, rs.getInt(11) != 0,
                                rs.getLong(12)));
                    }
                }
            }
            return out;
        });
    }

    // ------------------------------------------------------------------ writes

    /**
     * Stores a freshly rolled instance together with its audit row in one transaction.
     * Insert-ignore makes the call idempotent: replaying the journal can never create a second copy.
     */
    public CompletableFuture<Void> persistOpening(SkinInstance instance, OpeningRecord record) {
        return db.transaction(c -> {
            insert(c, instance);
            try (PreparedStatement ps = c.prepareStatement(db.dialect().insertIgnore() + openings
                    + " (opening_id, owner, owner_name, case_id, instance_id, skin_id, rarity, float_value, pattern, "
                    + "stattrak, test, opened_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, record.openingId().toString());
                ps.setString(2, record.owner().toString());
                ps.setString(3, record.ownerName());
                ps.setString(4, record.caseId());
                ps.setString(5, record.instanceId().toString());
                ps.setString(6, record.skinId());
                ps.setString(7, record.rarity());
                ps.setDouble(8, record.floatValue());
                ps.setInt(9, record.pattern());
                ps.setInt(10, record.statTrak() ? 1 : 0);
                ps.setInt(11, record.test() ? 1 : 0);
                ps.setLong(12, record.openedAt());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Audit row only (test openings that are not kept). */
    public CompletableFuture<Void> logOpening(OpeningRecord record) {
        return db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement(db.dialect().insertIgnore() + openings
                    + " (opening_id, owner, owner_name, case_id, instance_id, skin_id, rarity, float_value, pattern, "
                    + "stattrak, test, opened_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, record.openingId().toString());
                ps.setString(2, record.owner().toString());
                ps.setString(3, record.ownerName());
                ps.setString(4, record.caseId());
                ps.setString(5, record.instanceId().toString());
                ps.setString(6, record.skinId());
                ps.setString(7, record.rarity());
                ps.setDouble(8, record.floatValue());
                ps.setInt(9, record.pattern());
                ps.setInt(10, record.statTrak() ? 1 : 0);
                ps.setInt(11, 1);
                ps.setLong(12, record.openedAt());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Idempotent insert (admin gives, journal recovery). */
    public CompletableFuture<Void> insert(SkinInstance instance) {
        return db.run(c -> {
            insert(c, instance);
            return null;
        });
    }

    void insert(Connection c, SkinInstance i) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(db.dialect().insertIgnore() + skins + " (" + COLUMNS + ") "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            PatternInfo p = i.patternInfo();
            ps.setString(1, i.id().toString());
            ps.setString(2, i.owner().toString());
            ps.setString(3, i.skinId());
            ps.setDouble(4, i.floatValue());
            ps.setInt(5, i.pattern());
            ps.setLong(6, i.wearSeed());
            ps.setInt(7, i.statTrak() ? 1 : 0);
            ps.setInt(8, i.kills());
            ps.setString(9, p.variantId());
            ps.setString(10, p.variantName());
            ps.setString(11, p.classification());
            ps.setInt(12, p.tier());
            ps.setInt(13, p.color());
            if (p.fadePercent() == null) {
                ps.setNull(14, Types.DOUBLE);
            } else {
                ps.setDouble(14, p.fadePercent());
            }
            ps.setString(15, i.sourceCase());
            ps.setString(16, i.origin().name());
            ps.setLong(17, i.createdAt());
            ps.setInt(18, i.favorite() ? 1 : 0);
            ps.setString(19, i.status().name());
            ps.setInt(20, i.traded() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public CompletableFuture<Integer> setStatus(UUID id, SkinInstance.Status status) {
        return update("UPDATE " + skins + " SET status = ? WHERE instance_id = ?", status.name(), id.toString());
    }

    public CompletableFuture<Integer> removeOwned(UUID owner, UUID id) {
        return update("UPDATE " + skins + " SET status='REMOVED' WHERE instance_id=? AND owner=? AND status='OWNED'", id.toString(), owner.toString());
    }

    /** A confirmed admin removal cannot leave a persistent equipped reference behind. */
    public CompletableFuture<Integer> removeOwnedAndUnequip(UUID owner, UUID id) {
        return db.transaction(c -> {
            int changed;
            try (var ps = c.prepareStatement("UPDATE " + skins + " SET status='REMOVED' WHERE instance_id=? AND owner=? AND status='OWNED'")) {
                ps.setString(1, id.toString()); ps.setString(2, owner.toString()); changed = ps.executeUpdate();
            }
            if (changed == 1) try (var ps = c.prepareStatement("DELETE FROM " + equipped + " WHERE owner=? AND instance_id=?")) {
                ps.setString(1, owner.toString()); ps.setString(2, id.toString()); ps.executeUpdate();
            }
            return changed;
        });
    }

    /** Marks pending rows of a player as owned; returns the number of recovered rows. */
    public CompletableFuture<Integer> finalizePending(UUID owner) {
        return update("UPDATE " + skins + " SET status = 'OWNED' WHERE owner = ? AND status = 'PENDING'", owner.toString());
    }

    public CompletableFuture<Integer> setFavorite(UUID id, boolean favorite) {
        return update("UPDATE " + skins + " SET favorite = ? WHERE instance_id = ?", favorite ? 1 : 0, id.toString());
    }

    /** Atomic increment so concurrent kills can never overwrite each other. */
    public CompletableFuture<Integer> addKills(UUID id, int delta) {
        return update("UPDATE " + skins + " SET stattrak_kills = stattrak_kills + ? WHERE instance_id = ?", delta, id.toString());
    }

    /** Writes admin-forced values (float, pattern, StatTrak, pattern info). */
    public CompletableFuture<Integer> updateRoll(SkinInstance i) {
        PatternInfo p = i.patternInfo();
        return update("UPDATE " + skins + " SET float_value = ?, pattern = ?, stattrak = ?, variant = ?, variant_name = ?, "
                        + "classification = ?, class_tier = ?, class_color = ?, fade_percent = ? WHERE instance_id = ? AND owner = ? AND status = 'OWNED'",
                i.floatValue(), i.pattern(), i.statTrak() ? 1 : 0, p.variantId(), p.variantName(), p.classification(),
                p.tier(), p.color(), p.fadePercent(), i.id().toString(), i.owner().toString());
    }

    public CompletableFuture<Integer> setEquipped(UUID owner, String slot, UUID instance) {
        if (instance == null) {
            return update("DELETE FROM " + equipped + " WHERE owner = ? AND slot = ?", owner.toString(), slot);
        }
        return update(db.dialect().upsertEquipped(equipped), owner.toString(), slot, instance.toString());
    }

    /** Validate ownership and move a single skin between equipment slots atomically. */
    public CompletableFuture<Integer> equipOwned(UUID owner, String slot, UUID instance) {
        return db.transaction(c -> {
            try (var ps = c.prepareStatement("SELECT instance_id FROM " + skins + " WHERE instance_id=? AND owner=? AND status='OWNED'")) {
                ps.setString(1, instance.toString()); ps.setString(2, owner.toString());
                try (var rs = ps.executeQuery()) { if (!rs.next()) return 0; }
            }
            try (var ps = c.prepareStatement("DELETE FROM " + equipped + " WHERE owner=? AND instance_id=?")) {
                ps.setString(1, owner.toString()); ps.setString(2, instance.toString()); ps.executeUpdate();
            }
            try (var ps = c.prepareStatement(db.dialect().upsertEquipped(equipped))) {
                ps.setString(1, owner.toString()); ps.setString(2, slot); ps.setString(3, instance.toString()); ps.executeUpdate();
            }
            return 1;
        });
    }

    private CompletableFuture<Integer> update(String sql, Object... args) {
        return db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) {
                    Object a = args[i];
                    if (a == null) {
                        ps.setNull(i + 1, Types.NULL);
                    } else {
                        ps.setObject(i + 1, a);
                    }
                }
                return ps.executeUpdate();
            }
        });
    }

    static SkinInstance read(ResultSet rs) throws SQLException {
        double fade = rs.getDouble("fade_percent");
        Double fadeValue = rs.wasNull() ? null : fade;
        PatternInfo info = new PatternInfo(rs.getString("variant"), rs.getString("variant_name"),
                rs.getString("classification"), rs.getInt("class_tier"), rs.getInt("class_color"), fadeValue);
        SkinInstance.Origin origin;
        try {
            origin = SkinInstance.Origin.valueOf(rs.getString("origin"));
        } catch (IllegalArgumentException e) {
            origin = SkinInstance.Origin.ADMIN;
        }
        return new SkinInstance(UUID.fromString(rs.getString("instance_id")), UUID.fromString(rs.getString("owner")),
                rs.getString("skin_id"), rs.getDouble("float_value"), rs.getInt("pattern"), rs.getLong("wear_seed"),
                rs.getInt("stattrak") != 0, rs.getInt("stattrak_kills"), info, rs.getString("source"), origin,
                rs.getLong("created_at"), rs.getInt("favorite") != 0,
                SkinInstance.Status.valueOf(rs.getString("status")), rs.getInt("traded") != 0);
    }
}
