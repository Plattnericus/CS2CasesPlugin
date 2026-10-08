package dev.plattnericus.cases.storage;

import dev.plattnericus.cases.skin.SkinInstance;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static dev.plattnericus.cases.storage.CommerceRepository.*;

/** Consumes all inputs and creates exactly one output in one SQL transaction. */
public final class ContractRepository {
    private final Database db;
    private final CommerceRepository commerce;
    private final SkinRepository skins;
    public ContractRepository(Database db) { this.db = db; commerce = new CommerceRepository(db); skins = new SkinRepository(db); }
    public CompletableFuture<SkinInstance> commit(UUID contract, UUID owner, List<SkinInstance> inputs, SkinInstance reward) {
        var snapshot = inputs.stream().map(s -> s.copyWithStatus(s.status())).toList();
        String identities = snapshot.stream().map(s -> s.id().toString()).sorted().collect(java.util.stream.Collectors.joining(","));
        return db.transaction(c -> {
            if (!reward.owner().equals(owner) || reward.status() != SkinInstance.Status.OWNED
                    || snapshot.size() != 5 && snapshot.size() != 10 || snapshot.stream().map(SkinInstance::id).distinct().count() != snapshot.size()) throw new Rejected(Failure.INVALID);
            try (var ps = c.prepareStatement("SELECT owner, instance_id, inputs FROM " + db.table("trade_contracts") + " WHERE contract_id=?")) {
                bind(ps, contract); try (var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        if (!rs.getString(1).equals(owner.toString()) || !rs.getString(2).equals(reward.id().toString()) || !rs.getString(3).equals(identities)) throw new Rejected(Failure.INVALID);
                        return commerce.skin(c, reward.id(), owner, "OWNED");
                    }
                }
            }
            for (var expected : snapshot) {
                SkinInstance current = commerce.skin(c, expected.id(), owner, "OWNED");
                if (!current.skinId().equals(expected.skinId()) || current.floatValue() != expected.floatValue() || current.pattern() != expected.pattern()
                        || current.statTrak() != expected.statTrak() || current.origin() != expected.origin()) throw new Rejected(Failure.UNAVAILABLE);
                requireUpdate(c, "UPDATE " + db.table("skins") + " SET status='REMOVED' WHERE instance_id=? AND owner=? AND status='OWNED'", expected.id(), owner);
                try (var ps = c.prepareStatement("DELETE FROM " + db.table("equipped") + " WHERE owner=? AND instance_id=?")) { bind(ps, owner, expected.id()); ps.executeUpdate(); }
            }
            skins.insert(c, reward);
            try (var ps = c.prepareStatement("INSERT INTO " + db.table("trade_contracts") + " (contract_id, owner, instance_id, inputs, created_at) VALUES (?, ?, ?, ?, ?)")) {
                bind(ps, contract, owner, reward.id(), identities, System.currentTimeMillis()); ps.executeUpdate();
            }
            commerce.audit(c, "TRADE_IN", owner, null, 0, contract + ":" + reward.id());
            return reward;
        });
    }
    /** At-most-once outbox dispatch: mark BEFORE sending, so retry/restart cannot duplicate chat. */
    public CompletableFuture<Boolean> claimAnnouncement(UUID contract) {
        return db.run(c -> {
            try (var ps = c.prepareStatement("UPDATE " + db.table("trade_contracts") + " SET announced=1 WHERE contract_id=? AND announced=0")) {
                bind(ps, contract); return ps.executeUpdate() == 1;
            }
        });
    }
}
