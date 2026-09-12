package com.urbio.ledger.events;

/** Reverses a previously booked, non-system-generated ledger entry by
 * appending an equal-and-opposite entry at the ORIGINAL entry's value date.
 * The original entry is never touched -- append-only. */
public final class ReversalEvent extends Event {
    private final String reversedEventId;

    public ReversalEvent(String id, int processedDay, int valueDate, String accountId, String reversedEventId) {
        super(id, processedDay, valueDate, accountId);
        this.reversedEventId = reversedEventId;
    }

    public String reversedEventId() { return reversedEventId; }
}
