package com.account.ledger.model;

/** The kind of a booked ledger entry. Every entry is signed (its Money amount
 * carries the sign); this enum is purely descriptive, for reporting and for
 * distinguishing system-generated entries (fees, interest) from
 * event-sourced ones. */
public enum EntryType {
    CREDIT,
    DEBIT,
    AUTH_SETTLEMENT_DEBIT,
    OVERDRAFT_FEE,
    INTEREST_CAPITALIZATION,
    REVERSAL
}
