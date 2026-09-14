package com.account.ledger.model;

/**
 * Supported ledger currencies. Each currency carries the number of minor-unit
 * decimal places its amounts are stored and rounded to (ISO 4217 style: AED
 * has 2, BHD has 3). All {@link Money} values for a currency are rounded to
 * this scale the moment they are constructed -- see NUMBERS.md for why.
 */
public enum CurrencyCode {
    AED(2),
    BHD(3);

    private final int scale;

    CurrencyCode(int scale) {
        this.scale = scale;
    }

    public int scale() {
        return scale;
    }
}
