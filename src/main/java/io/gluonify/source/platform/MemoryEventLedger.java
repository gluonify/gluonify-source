package io.gluonify.source.platform;

import java.util.concurrent.ConcurrentHashMap;

/** In-memory ledger: correct for a single instance only (storage mode {@code memory}). */
public class MemoryEventLedger implements EventLedger {
    private static final int MAX_REMEMBERED = 10_000;
    private final ConcurrentHashMap<String, Boolean> seen = new ConcurrentHashMap<>();

    @Override
    public boolean firstSeen(String eventId) {
        if (seen.size() > MAX_REMEMBERED) seen.clear(); // bounded memory
        return seen.putIfAbsent(eventId, Boolean.TRUE) == null;
    }
}
