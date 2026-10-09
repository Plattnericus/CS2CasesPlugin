package dev.plattnericus.cases.opening;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Main-thread registry. Capacity includes held world results, so displays cannot grow unbounded. */
public final class OpeningSessions {
    private final Map<UUID, OpeningSession> sessions = new LinkedHashMap<>();
    public List<OpeningSession> forPlayer(UUID player) { return sessions.values().stream().filter(s -> s.playerId.equals(player)).toList(); }
    public boolean add(OpeningSession session, int playerLimit, int globalLimit) {
        return addAll(List.of(session), playerLimit, globalLimit);
    }
    /** Reserve an entire request before any asynchronous work or item consumption starts. */
    public boolean addAll(List<OpeningSession> requested, int playerLimit, int globalLimit) {
        if (requested.isEmpty()) return false;
        UUID player = requested.getFirst().playerId;
        if (requested.stream().anyMatch(s -> !s.playerId.equals(player) || sessions.containsKey(s.openingId))
                || requested.stream().map(s -> s.openingId).distinct().count() != requested.size()) return false;
        var owned = forPlayer(player);
        if (requested.size() > playerLimit - owned.size() || requested.size() > globalLimit - sessions.size()) return false;
        var lanes = owned.stream().map(s -> s.lane).collect(java.util.stream.Collectors.toSet());
        for (OpeningSession session : requested) {
            session.lane = 0;
            while (lanes.contains(session.lane)) session.lane++;
            lanes.add(session.lane);
            sessions.put(session.openingId, session);
        }
        return true;
    }
    /** Cases and shared keys promised to rolls that have not consumed their items yet. */
    public int reserved(UUID player, String id, boolean key) {
        return (int) forPlayer(player).stream().filter(s -> !s.adminTest && s.state == OpeningSession.State.ROLLING
                && (key ? s.caseDef.keyId() : s.caseDef.id()).equals(id)).count();
    }
    public int capacity(UUID player, int playerLimit, int globalLimit) {
        return Math.max(0, Math.min(playerLimit - forPlayer(player).size(), globalLimit - size()));
    }
    public OpeningSession get(UUID id) { return sessions.get(id); }
    public void remove(UUID id, OpeningSession expected) { sessions.remove(id, expected); }
    public Collection<OpeningSession> values() { return List.copyOf(sessions.values()); }
    public int size() { return sessions.size(); }
    public void clear() { sessions.clear(); }
}
