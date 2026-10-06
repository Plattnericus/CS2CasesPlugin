package dev.plattnericus.cases.commerce;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A confirmation belongs to one exact revision of both offers. */
public final class TradeSession {
    public static final int MAX_ITEMS = 12;
    private final UUID first, second;
    private final Set<UUID> firstOffer = new LinkedHashSet<>(), secondOffer = new LinkedHashSet<>();
    private int revision, firstConfirmed = -1, secondConfirmed = -1;
    private boolean committing;
    private long changedAt = System.currentTimeMillis();
    public TradeSession(UUID first, UUID second) {
        if (first.equals(second)) throw new IllegalArgumentException("Two distinct players required");
        this.first = first; this.second = second;
    }
    public UUID first() { return first; }
    public UUID second() { return second; }
    public UUID other(UUID owner) { requireParticipant(owner); return owner.equals(first) ? second : first; }
    private void requireParticipant(UUID owner) { if (!owner.equals(first) && !owner.equals(second)) throw new IllegalArgumentException("Not a participant"); }
    private Set<UUID> offer(UUID owner) { requireParticipant(owner); return owner.equals(first) ? firstOffer : secondOffer; }
    public List<UUID> items(UUID owner) { return List.copyOf(offer(owner)); }
    public int revision() { return revision; }
    public boolean empty() { return firstOffer.isEmpty() && secondOffer.isEmpty(); }
    public int reviewSeconds(long now) {
        long elapsed = Math.max(0, now - changedAt);
        return elapsed >= 2000 ? 0 : (int) ((2000 - elapsed + 999) / 1000);
    }
    public boolean toggle(UUID owner, UUID skin) {
        if (committing) return false;
        Set<UUID> offer = offer(owner);
        if (!offer.remove(skin)) {
            if (offer.size() >= MAX_ITEMS || offer(other(owner)).contains(skin)) return false;
            offer.add(skin);
        }
        revision++; firstConfirmed = secondConfirmed = -1; changedAt = System.currentTimeMillis(); return true;
    }
    public boolean confirm(UUID owner, long now) {
        return confirm(owner, revision, now);
    }
    public boolean confirm(UUID owner, int expectedRevision, long now) {
        requireParticipant(owner);
        if (committing || expectedRevision != revision || empty() || reviewSeconds(now) > 0) return false;
        if (owner.equals(first)) firstConfirmed = revision; else secondConfirmed = revision;
        return true;
    }
    public boolean confirmed(UUID owner) { requireParticipant(owner); return (owner.equals(first) ? firstConfirmed : secondConfirmed) == revision; }
    public boolean unconfirm(UUID owner) {
        requireParticipant(owner);
        if (committing || !confirmed(owner)) return false;
        if (owner.equals(first)) firstConfirmed = -1; else secondConfirmed = -1;
        return true;
    }
    public boolean ready() { return confirmed(first) && confirmed(second); }
    public boolean committing() { return committing; }
    public void beginCommit() { if (!ready() || committing) throw new IllegalStateException("Offer is not confirmed"); committing = true; }
}
