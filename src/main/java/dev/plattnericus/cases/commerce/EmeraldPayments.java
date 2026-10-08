package dev.plattnericus.cases.commerce;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.storage.EmeraldRepository;
import dev.plattnericus.cases.storage.CommerceRepository.Listing;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Serializes each player's financial operations without freezing their inventory across SQL calls. */
public final class EmeraldPayments {
    private final CasesContext ctx;
    private final EmeraldRepository repository;
    private final ItemReceipts receipts;
    private final Set<UUID> busy = new HashSet<>(), failed = new HashSet<>();
    public boolean canRecover(UUID id) { return !busy.contains(id) || failed.contains(id); }
    private final Map<UUID, Long> claims = new HashMap<>();
    public EmeraldPayments(CasesContext ctx, EmeraldRepository repository) {
        this.ctx = ctx; this.repository = repository; receipts = new ItemReceipts(ctx.plugin());
    }
    public boolean busy(UUID id) { return busy.contains(id); }
    public void forget(UUID id) { claims.remove(id); }
    public long pending(UUID id) { return claims.getOrDefault(id, 0L); }
    private boolean live(Player p) { return p.isOnline() && Bukkit.getPlayer(p.getUniqueId()) == p; }
    private void main(Runnable work) { if (ctx.plugin().isEnabled()) Bukkit.getScheduler().runTask(ctx.plugin(), work); }
    private void error(Player p, Throwable error) {
        if (busy.contains(p.getUniqueId())) failed.add(p.getUniqueId());
        ctx.plugin().getLogger().log(java.util.logging.Level.SEVERE, "Emerald transaction retained for recovery for " + p.getUniqueId(), error);
        if (live(p)) ctx.messages(p).send(p, "market.recovery-needed");
    }
    public void refresh(Player p, Runnable done) {
        repository.claims(p.getUniqueId()).whenComplete((list, error) -> main(() -> {
            if (error != null) { error(p, error); return; }
            claims.put(p.getUniqueId(), list.stream().mapToLong(EmeraldRepository.Claim::remaining).sum());
            if (live(p)) done.run();
        }));
    }
    public void buy(Player p, Listing listing, Consumer<SkinInstance> success, Runnable released) {
        UUID owner = p.getUniqueId();
        if (!busy.add(owner)) { released.run(); ctx.messages(p).send(p, "market.payment-busy"); return; }
        UUID tx = UUID.randomUUID();
        repository.prepare(tx, owner, listing.id(), listing.price()).whenComplete((payment, prepareError) -> main(() -> {
            if (prepareError != null) { busy.remove(owner); released.run(); error(p, prepareError); return; }
            if (!live(p) || !p.hasPermission("mccases.market") || !EmeraldItems.take(p.getInventory(), payment.amount(), ctx.commerce().includeOffhand())) {
                repository.cancelPayment(tx, owner).whenComplete((v, cancelError) -> main(() -> {
                    busy.remove(owner); released.run();
                    if (cancelError != null) error(p, cancelError);
                    else if (live(p)) ctx.messages(p).send(p, "market.emerald-not-enough");
                }));
                return;
            }
            try { receipts.save(p, "PAY", tx, payment.amount()); }
            catch (RuntimeException saveError) { released.run(); error(p, saveError); return; }
            repository.completePayment(tx, owner).whenComplete((skin, commitError) -> main(() -> {
                released.run();
                if (commitError != null) { error(p, commitError); return; }
                if (live(p)) {
                    try { receipts.clear(p, "PAY", tx, payment.amount()); }
                    catch (RuntimeException saveError) { error(p, saveError); return; }
                }
                busy.remove(owner); success.accept(skin);
            }));
        }));
    }
    public void claim(Player p) {
        UUID owner = p.getUniqueId();
        if (!busy.add(owner)) { ctx.messages(p).send(p, "market.payment-busy"); return; }
        repository.claims(owner).whenComplete((list, readError) -> main(() -> {
            if (readError != null) { busy.remove(owner); error(p, readError); return; }
            if (!live(p)) { busy.remove(owner); return; }
            int capacity = Math.min(EmeraldItems.capacity(p.getInventory()), ctx.commerce().maxDelivery());
            var claim = list.stream().filter(c -> !c.reserved()).findFirst().orElse(null);
            if (claim != null && !ctx.commerce().partialClaims() && claim.remaining() > capacity) capacity = 0;
            if (claim == null || capacity == 0) {
                busy.remove(owner); ctx.messages(p).send(p, claim == null ? "market.no-claims" : "market.claim-full"); refresh(p, () -> { }); return;
            }
            UUID id = UUID.randomUUID(); final int availableCapacity = capacity;
            repository.prepareDelivery(id, owner, claim.id(), availableCapacity).whenComplete((delivery, prepareError) -> main(() -> {
                if (prepareError != null) { busy.remove(owner); error(p, prepareError); return; }
                if (!live(p) || !EmeraldItems.give(p.getInventory(), delivery.amount())) {
                    repository.cancelDelivery(id, owner).whenComplete((v, cancelError) -> main(() -> {
                        busy.remove(owner); if (cancelError != null) error(p, cancelError);
                        else if (live(p)) ctx.messages(p).send(p, "market.claim-full");
                    })); return;
                }
                try { receipts.save(p, "GIVE", id, delivery.amount()); }
                catch (RuntimeException saveError) { error(p, saveError); return; }
                repository.completeDelivery(id, owner).whenComplete((v, completeError) -> main(() -> {
                    if (completeError != null) { error(p, completeError); return; }
                    if (live(p)) {
                        try { receipts.clear(p, "GIVE", id, delivery.amount()); }
                        catch (RuntimeException saveError) { error(p, saveError); return; }
                        ctx.messages(p).send(p, "market.claimed", Text.unparsed("amount", delivery.amount()));
                    }
                    busy.remove(owner); refresh(p, () -> {
                        if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof MarketMenu menu) menu.render();
                    });
                }));
            }));
        }));
    }
    /** Runs before profile reads, while the player cannot use commerce. Offline preparations wait for login. */
    public CompletableFuture<Void> recover(Player p) {
        UUID owner = p.getUniqueId();
        if (!canRecover(owner)) return CompletableFuture.failedFuture(new IllegalStateException("Payment still running"));
        busy.add(owner);
        CompletableFuture<Void> result = new CompletableFuture<>();
        var payIds = receipts.ids(p, "PAY");
        var giveIds = receipts.ids(p, "GIVE");
        repository.payments(owner, payIds).thenCombine(repository.deliveries(owner, giveIds), (payments, deliveries) -> new Object[]{payments, deliveries})
                .whenComplete((rows, readError) -> main(() -> {
                    if (!live(p)) { failed.add(owner); result.completeExceptionally(new IllegalStateException("Player disconnected during recovery")); return; }
                    if (readError != null) { error(p, readError); result.completeExceptionally(readError); return; }
                    @SuppressWarnings("unchecked") var payments = (java.util.List<EmeraldRepository.Payment>) rows[0];
                    @SuppressWarnings("unchecked") var deliveries = (java.util.List<EmeraldRepository.Delivery>) rows[1];
                    // A receipt without its exact SQL operation is ambiguous. Retain it for audit.
                    boolean invalid = payIds.stream().anyMatch(id -> payments.stream().noneMatch(row -> row.id().equals(id)))
                            || giveIds.stream().anyMatch(id -> deliveries.stream().noneMatch(row -> row.id().equals(id)))
                            || payments.stream().anyMatch(row -> payIds.contains(row.id()) && (!receipts.has(p, "PAY", row.id(), row.amount()) || row.state().equals("CANCELLED")))
                            || deliveries.stream().anyMatch(row -> giveIds.contains(row.id()) && (!receipts.has(p, "GIVE", row.id(), row.amount()) || row.state().equals("CANCELLED")));
                    if (invalid) {
                        var error = new IllegalStateException("Inventory receipt does not match Emerald ledger; manual audit required");
                        error(p, error); result.completeExceptionally(error); return;
                    }
                    CompletableFuture<Void> work = CompletableFuture.completedFuture(null);
                    for (var payment : payments) {
                        boolean paid = receipts.has(p, "PAY", payment.id(), payment.amount());
                        if (payment.state().equals("PREPARED")) work = work.thenCompose(v -> paid
                                ? repository.completePayment(payment.id(), owner).thenApply(s -> null)
                                : repository.cancelPayment(payment.id(), owner));
                    }
                    for (var delivery : deliveries) {
                        boolean given = receipts.has(p, "GIVE", delivery.id(), delivery.amount());
                        if (delivery.state().equals("PREPARED")) work = work.thenCompose(v -> given
                                ? repository.completeDelivery(delivery.id(), owner) : repository.cancelDelivery(delivery.id(), owner));
                    }
                    work.whenComplete((v, recoveryError) -> main(() -> {
                        if (recoveryError != null) { error(p, recoveryError); result.completeExceptionally(recoveryError); return; }
                        if (live(p)) {
                            try {
                                for (var payment : payments) if (receipts.has(p, "PAY", payment.id(), payment.amount())) receipts.clear(p, "PAY", payment.id(), payment.amount());
                                for (var delivery : deliveries) if (receipts.has(p, "GIVE", delivery.id(), delivery.amount())) receipts.clear(p, "GIVE", delivery.id(), delivery.amount());
                            } catch (RuntimeException saveError) { error(p, saveError); result.completeExceptionally(saveError); return; }
                        }
                        busy.remove(owner); failed.remove(owner); ctx.commerce().reloadListings(); refresh(p, () -> { }); result.complete(null);
                    }));
                }));
        return result;
    }
}
