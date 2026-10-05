package io.gluonify.source.platform;

/**
 * Remembers the {@code X-Gluonify-Event-Id} values already processed, so that the webhook receiver is idempotent <b>across all replicas</b>
 * (Photon's load balancing can deliver the same event to any instance). One implementation per storage mode (see {@link EventLedgers}).
 */
public interface EventLedger {
    /** Atomically records the event: true for the ONE caller (on any replica) that records it first, false for everybody else (duplicate). */
    boolean firstSeen(String eventId);
}
