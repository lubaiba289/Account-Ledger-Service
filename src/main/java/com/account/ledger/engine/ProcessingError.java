package com.account.ledger.engine;

/** A rejected event or a hard validation failure surfaced during replay.
 * Recorded, never thrown -- the replay must keep going and the test suite /
 * report must be able to show, per day, what was rejected and why. */
public final class ProcessingError {
    private final int day;
    private final String eventId;
    private final String message;

    public ProcessingError(int day, String eventId, String message) {
        this.day = day;
        this.eventId = eventId;
        this.message = message;
    }

    public int day() { return day; }
    public String eventId() { return eventId; }
    public String message() { return message; }

    @Override
    public String toString() {
        return "D" + day + " " + eventId + ": " + message;
    }
}
