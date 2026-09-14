package com.account.ledger.model;

/**
 * A single, immutable, append-only booking on an account's ledger. Once
 * constructed a LedgerEntry can never be changed or removed -- correcting a
 * mistake means appending a new, opposite entry (see the REVERSAL handling
 * in ReplayEngine), never mutating history.
 *
 * <p>{@code valueDate} is the accounting day this entry counts toward for
 * closing-balance purposes. {@code postedDay} is the day-in-the-replay it
 * was actually appended to the ledger. The two differ exactly when an entry
 * is backdated (e.g. E7: postedDay=5, valueDate=2) -- see AMBIGUITIES.md.
 */
public final class LedgerEntry {

    private final String id;
    private final String accountId;
    private final EntryType type;
    private final Money amount; // signed: positive = credit-like, negative = debit-like
    private final int valueDate;
    private final int postedDay;
    private final String description;
    private final String relatedAuthorizationId; // nullable

    public LedgerEntry(String id, String accountId, EntryType type, Money amount,
                        int valueDate, int postedDay, String description,
                        String relatedAuthorizationId) {
        this.id = id;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.valueDate = valueDate;
        this.postedDay = postedDay;
        this.description = description;
        this.relatedAuthorizationId = relatedAuthorizationId;
    }

    public String id() { return id; }
    public String accountId() { return accountId; }
    public EntryType type() { return type; }
    public Money amount() { return amount; }
    public int valueDate() { return valueDate; }
    public int postedDay() { return postedDay; }
    public String description() { return description; }
    public String relatedAuthorizationId() { return relatedAuthorizationId; }

    public boolean isBackdated() {
        return valueDate != postedDay;
    }

    @Override
    public String toString() {
        return String.format("[%s] %-24s %-10s %10s  value_date=D%d posted=D%d %s",
                id, accountId, type, amount, valueDate, postedDay,
                description == null ? "" : "- " + description);
    }
}
