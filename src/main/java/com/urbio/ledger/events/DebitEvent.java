package com.urbio.ledger.events;

import com.urbio.ledger.model.Money;

/** A straight debit (withdrawal) booking. */
public final class DebitEvent extends Event {
    private final Money amount;

    public DebitEvent(String id, int processedDay, int valueDate, String accountId, Money amount) {
        super(id, processedDay, valueDate, accountId);
        this.amount = amount;
    }

    public Money amount() { return amount; }
}
