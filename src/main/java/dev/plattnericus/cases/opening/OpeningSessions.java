package dev.plattnericus.cases.opening;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Main-thread registry. Capacity includes held world results, so displays cannot grow unbounded. */
public final class OpeningSessions {
    private final Map<UUID, OpeningSession> sessions = new HashMap<>();
    public List<OpeningSession> forPlayer(UUID player) { return sessions.values().stream().filter(s -> s.playerId.equals(player)).toList(); }
    public boolean add(OpeningSession session, int playerLimit, int globalLimit) {
        var owned = forPlayer(session.playerId);
        if (owned.size() >= playerLimit || sessions.size() >= globalLimit || sessions.containsKey(session.openingId)) return false;
        var lanes = owned.stream().map(s -> s.lane).collect(java.util.stream.Collectors.toSet());
        while (lanes.contains(session.lane)) session.lane++;
        sessions.put(session.openingId, session); return true;
    }
    public OpeningSession get(UUID id) { return sessions.get(id); }
    public void remove(UUID id, OpeningSession expected) { sessions.remove(id, expected); }
    public Collection<OpeningSession> values() { return List.copyOf(sessions.values()); }
    public int size() { return sessions.size(); }
    public void clear() { sessions.clear(); }
}
