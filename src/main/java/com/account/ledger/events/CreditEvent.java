package com.account.ledger.events;

import com.account.ledger.model.Money;

/** A straight credit (deposit) booking. May be split into several equal
 * ledger postings via {@code installments} (E10: BHD 10.000 as three equal
 * instalments) -- see NUMBERS.md for the remainder-allocation rule used when
 * the total doesn't divide evenly at the currency's precision. */
public final class CreditEvent extends Event {
    private final Money amount;
    private final int installments;

    public CreditEvent(String id, int processedDay, int valueDate, String accountId, Money amount) {
        this(id, processedDay, valueDate, accountId, amount, 1);
    }

    public CreditEvent(String id, int processedDay, int valueDate, String accountId, Money amount, int installments) {
        super(id, processedDay, valueDate, accountId);
        this.amount = amount;
        this.installments = installments;
    }

    public Money amount() { return amount; }
    public int installments() { return installments; }
}
