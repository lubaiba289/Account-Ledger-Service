package com.urbio.ledger.model;

/** A named account with a fixed home currency. No balance lives here -- the
 * ledger entries are the only source of truth (see {@link Ledger}). */
public final class Account {

    private final String id;
    private final CurrencyCode currency;

    public Account(String id, CurrencyCode currency) {
        this.id = id;
        this.currency = currency;
    }

    public String id() {
        return id;
    }

    public CurrencyCode currency() {
        return currency;
    }

    @Override
    public String toString() {
        return id + " (" + currency + ")";
    }
}
