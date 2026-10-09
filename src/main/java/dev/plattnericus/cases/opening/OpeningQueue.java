package dev.plattnericus.cases.opening;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Main-thread FIFO of requests; waiting requests have not consumed any physical items. */
public final class OpeningQueue {
    public static final int LIMIT = 1000;
    public record Request(String caseId, String keyId, int amount) { }
    private final Map<UUID, ArrayDeque<Request>> requests = new LinkedHashMap<>();
    public int count(UUID owner) { return requests.getOrDefault(owner, new ArrayDeque<>()).stream().mapToInt(Request::amount).sum(); }
    public int reserved(UUID owner, String id, boolean key) {
        return requests.getOrDefault(owner, new ArrayDeque<>()).stream()
                .filter(r -> (key ? r.keyId() : r.caseId()).equals(id)).mapToInt(Request::amount).sum();
    }
    public boolean add(UUID owner, String caseId, String keyId, int amount) {
        if (amount < 1 || amount > LIMIT - count(owner)) return false;
        requests.computeIfAbsent(owner, ignored -> new ArrayDeque<>()).addLast(new Request(caseId, keyId, amount));
        return true;
    }
    public Request peek(UUID owner) { var queue = requests.get(owner); return queue == null ? null : queue.peekFirst(); }
    public Request take(UUID owner, int capacity) {
        if (capacity < 1) return null;
        var queue = requests.get(owner); if (queue == null) return null;
        Request first = queue.removeFirst(); int amount = Math.min(capacity, first.amount());
        if (first.amount() > amount) queue.addFirst(new Request(first.caseId(), first.keyId(), first.amount() - amount));
        if (queue.isEmpty()) requests.remove(owner);
        return new Request(first.caseId(), first.keyId(), amount);
    }
    public List<UUID> owners() { return List.copyOf(requests.keySet()); }
    public int clear(UUID owner) { int count = count(owner); requests.remove(owner); return count; }
    public void clear() { requests.clear(); }
}
