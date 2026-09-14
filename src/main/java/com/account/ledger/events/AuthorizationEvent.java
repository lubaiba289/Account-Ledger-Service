package com.account.ledger.events;

import com.account.ledger.model.Money;

/** A request to place a hold against an account for a card-style authorization. */
public final class AuthorizationEvent extends Event {
    private final String authorizationId;
    private final Money holdAmount;

    public AuthorizationEvent(String id, int processedDay, int valueDate, String accountId,
                               String authorizationId, Money holdAmount) {
        super(id, processedDay, valueDate, accountId);
        this.authorizationId = authorizationId;
        this.holdAmount = holdAmount;
    }

    public String authorizationId() { return authorizationId; }
    public Money holdAmount() { return holdAmount; }
}
