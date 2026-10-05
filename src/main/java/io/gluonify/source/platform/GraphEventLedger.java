package io.gluonify.source.platform;

import io.gluonify.source.notes.GraphNoteStore;
import java.util.Map;

/**
 * Ledger in Gdown, shared by all replicas. Each event is an {@code (:Event {id})} node protected by a UNIQUENESS CONSTRAINT: of several
 * concurrent {@code CREATE}s of the same id, exactly one succeeds and the others are refused by the database (a constraint violation = duplicate).
 * The constraint is created on first use ({@code IF NOT EXISTS}, idempotent; the account needs CONSTRAINT MANAGEMENT, see deploy/gdown-setup.cypher).
 */
public class GraphEventLedger implements EventLedger {
    private final GraphNoteStore graph;
    private volatile boolean constraintReady;

    public GraphEventLedger(GraphNoteStore graph) {
        this.graph = graph;
    }

    @Override
    public boolean firstSeen(String eventId) {
        if (!constraintReady) {
            graph.run("CREATE CONSTRAINT event_id IF NOT EXISTS FOR (e:Event) REQUIRE e.id IS UNIQUE", Map.of());
            constraintReady = true;
        }
        try {
            graph.run("CREATE (:Event {id: $id})", Map.of("id", eventId));
            return true;
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase(java.util.Locale.ROOT).contains("constraint")) return false;
            throw e;
        }
    }
}
