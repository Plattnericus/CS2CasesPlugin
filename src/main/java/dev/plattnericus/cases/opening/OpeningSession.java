package dev.plattnericus.cases.opening;

import dev.plattnericus.cases.catalog.CaseDefinition;
import dev.plattnericus.cases.catalog.Catalog;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.skin.SkinInstance;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;

/**
 * One case opening. States only move forward:
 * ROLLING → PERSISTING → ANIMATING → REVEALING → FINISHED, or ROLLING/PERSISTING → ABORTED.
 * The reward exists (in the journal or the database) from PERSISTING on; everything after that
 * is presentation.
 */
public final class OpeningSession {

    public enum State { ROLLING, PERSISTING, ANIMATING, REVEALING, FINISHED, ABORTED }

    final UUID playerId;
    final UUID openingId = UUID.randomUUID();
    final CaseDefinition caseDef;
    final Catalog catalog;
    /** Admin test opening: consumes nothing and stores nothing unless {@link #keep}. */
    final boolean adminTest;
    final boolean keep;
    State state = State.ROLLING;
    SkinInstance instance;
    SkinDefinition reward;
    List<SkinDefinition> reel;
    int winnerIndex;
    OpeningView view;
    BukkitTask task;
    int tick;
    int lastOffset = -1;
    boolean revealed;
    boolean finalized;
    /** Preserve both consumed test flags for exact refunds. */
    dev.plattnericus.cases.items.CaseItems.Consumption consumed;

    OpeningSession(UUID playerId, CaseDefinition caseDef, Catalog catalog, boolean adminTest, boolean keep) {
        this.playerId = playerId;
        this.caseDef = caseDef;
        this.catalog = catalog;
        this.adminTest = adminTest;
        this.keep = keep;
    }

    public State state() {
        return state;
    }

    boolean isActive() {
        return state != State.FINISHED && state != State.ABORTED;
    }

    /** Whether this opening writes a real skin to the owner's inventory. */
    boolean persistent() {
        return !adminTest || keep;
    }
}
