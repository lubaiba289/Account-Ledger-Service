package com.account.ledger.events;

import com.account.ledger.model.Money;

/** Settles a previously-authorized hold for a (possibly different) final amount. */
public final class SettlementEvent extends Event {
    private final String authorizationId;
    private final Money settledAmount;

    public SettlementEvent(String id, int processedDay, int valueDate, String accountId,
                            String authorizationId, Money settledAmount) {
        super(id, processedDay, valueDate, accountId);
        this.authorizationId = authorizationId;
        this.settledAmount = settledAmount;
    }

    public String authorizationId() { return authorizationId; }
    public Money settledAmount() { return settledAmount; }
}
