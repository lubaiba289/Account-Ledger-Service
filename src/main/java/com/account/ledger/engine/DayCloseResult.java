package com.account.ledger.engine;

import com.account.ledger.model.Money;

/** A snapshot of one account's end-of-day outcome, taken once, at that day's
 * own close, and never revised afterwards -- even if a later backdated
 * entry would make the numbers look different in hindsight. That
 * "assessed-at-the-time" snapshot is what the fee/interest engine acts on;
 * see AMBIGUITIES.md for why this is kept distinct from the "closing
 * balance as recomputed later" query that {@link com.account.ledger.model.Ledger#closingBalanceAsOf}
 * answers on demand. */
public final class DayCloseResult {
    private final String accountId;
    private final int day;
    private final Money closingBalanceAtClose;
    private final Money feeAssessed;       // null if none
    private final Money interestAccrued;    // null if none (incl. if balance <= 0)
    private final Money interestCapitalized; // non-null only on the last day, if > 0

    public DayCloseResult(String accountId, int day, Money closingBalanceAtClose,
                           Money feeAssessed, Money interestAccrued, Money interestCapitalized) {
        this.accountId = accountId;
        this.day = day;
        this.closingBalanceAtClose = closingBalanceAtClose;
        this.feeAssessed = feeAssessed;
        this.interestAccrued = interestAccrued;
        this.interestCapitalized = interestCapitalized;
    }

    public String accountId() { return accountId; }
    public int day() { return day; }
    public Money closingBalanceAtClose() { return closingBalanceAtClose; }
    public Money feeAssessed() { return feeAssessed; }
    public Money interestAccrued() { return interestAccrued; }
    public Money interestCapitalized() { return interestCapitalized; }
}
