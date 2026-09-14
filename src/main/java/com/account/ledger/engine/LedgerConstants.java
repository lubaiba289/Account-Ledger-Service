package com.account.ledger.engine;

import com.account.ledger.model.CurrencyCode;
import com.account.ledger.model.Money;

import java.math.BigDecimal;

/** Every tunable constant the engine uses, in one place. See NUMBERS.md for
 * the rationale behind each value -- in particular why it is NOT half this,
 * or double it, or some other plausible-looking number. */
public final class LedgerConstants {

    private LedgerConstants() {}

    /** Overdraft fee, charged at most once per account per day. Currently
     * only defined for AED; see NUMBERS.md and AMBIGUITIES.md for why a
     * BHD (or other) overdraft fee amount is deliberately left unresolved
     * rather than guessed. */
    public static Money overdraftFeeFor(CurrencyCode currency) {
        if (currency == CurrencyCode.AED) {
            return Money.of(new BigDecimal("25.00"), CurrencyCode.AED);
        }
        throw new UnsupportedOperationException(
                "No overdraft fee amount is defined for " + currency
                        + ". The spec only ever states the fee in AED terms; rather than "
                        + "invent an FX-converted number, this engine refuses to charge an "
                        + "overdraft fee in any other currency until that policy is specified. "
                        + "See NUMBERS.md.");
    }

    /** 0.04% per day, expressed as a plain multiplier. */
    public static final BigDecimal DAILY_INTEREST_RATE = new BigDecimal("0.0004");

    /** The six-day replay window, Day 1 through Day 6 inclusive. */
    public static final int FIRST_DAY = 1;
    public static final int LAST_DAY = 6;
}
