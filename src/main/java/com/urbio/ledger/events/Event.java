package com.urbio.ledger.events;

/**
 * Base type for the incoming event stream. {@code processedDay} is when the
 * event arrives / is replayed (the stream's own ordering); {@code valueDate}
 * is the accounting day it books against. They differ for backdated events
 * (E7, E9) -- that gap is the whole point of the exercise.
 */
public abstract class Event {
    private final String id;
    private final int processedDay;
    private final int valueDate;
    private final String accountId;

    protected Event(String id, int processedDay, int valueDate, String accountId) {
        this.id = id;
        this.processedDay = processedDay;
        this.valueDate = valueDate;
        this.accountId = accountId;
    }

    public String id() { return id; }
    public int processedDay() { return processedDay; }
    public int valueDate() { return valueDate; }
    public String accountId() { return accountId; }
}
