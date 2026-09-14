package com.account.ledger.model;

/** Lifecycle states of a card-style authorization hold. */
public enum AuthorizationStatus {
    /** Approved and currently reducing available balance. */
    HELD,
    /** Approved, later settled: the hold was released and an actual debit booked. */
    SETTLED,
    /** Declined at request time (available balance would have gone negative). */
    REJECTED,
    /** Still HELD when the six-day window ends, with no settlement seen. */
    OUTSTANDING_AT_WINDOW_END
}
