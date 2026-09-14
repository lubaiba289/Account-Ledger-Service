package com.account.ledger.model;

/**
 * Mutable tracking object for one authorization hold's lifecycle. This is
 * deliberately NOT append-only the way LedgerEntry is: an authorization is a
 * live, mutable "intent" record (its status changes as it is settled), not a
 * booking. The ledger of actual money movements stays append-only regardless
 * -- settlement appends a new debit entry, it does not rewrite the hold.
 */
public final class Authorization {

    private final String id;
    private final String accountId;
    private final Money holdAmount;
    private final int requestedDay;

    private AuthorizationStatus status;
    private Money settledAmount; // nullable until settled
    private Integer settledDay;  // nullable until settled
    private String note;

    public Authorization(String id, String accountId, Money holdAmount, int requestedDay) {
        this.id = id;
        this.accountId = accountId;
        this.holdAmount = holdAmount;
        this.requestedDay = requestedDay;
        this.status = AuthorizationStatus.HELD;
    }

    public String id() { return id; }
    public String accountId() { return accountId; }
    public Money holdAmount() { return holdAmount; }
    public int requestedDay() { return requestedDay; }
    public AuthorizationStatus status() { return status; }
    public Money settledAmount() { return settledAmount; }
    public Integer settledDay() { return settledDay; }
    public String note() { return note; }

    public void markRejected(String reason) {
        this.status = AuthorizationStatus.REJECTED;
        this.note = reason;
    }

    public void markSettled(Money settledAmount, int settledDay) {
        this.status = AuthorizationStatus.SETTLED;
        this.settledAmount = settledAmount;
        this.settledDay = settledDay;
    }

    public void markOutstandingAtWindowEnd() {
        if (this.status == AuthorizationStatus.HELD) {
            this.status = AuthorizationStatus.OUTSTANDING_AT_WINDOW_END;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(id).append(" ").append(status).append(" hold=").append(holdAmount)
                .append(" requestedDay=D").append(requestedDay);
        if (settledAmount != null) {
            sb.append(" settledAmount=").append(settledAmount).append(" settledDay=D").append(settledDay);
        }
        if (note != null) {
            sb.append(" (").append(note).append(")");
        }
        return sb.toString();
    }
}
