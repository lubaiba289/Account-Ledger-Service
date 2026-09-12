package com.urbio.ledger.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An immutable, currency-scoped amount. Every Money value is rounded to its
 * currency's own decimal precision at construction time (HALF_UP -- see
 * NUMBERS.md for why that rounding mode was chosen), so a Money instance is
 * always already "stored and rounded to its own precision" per the spec.
 *
 * Arithmetic between two Money values of different currencies is refused:
 * this ledger never converts between AED and BHD, so mixing them is a bug,
 * not a feature to support silently.
 */
public final class Money implements Comparable<Money> {

    private final BigDecimal amount;
    private final CurrencyCode currency;

    private Money(BigDecimal amount, CurrencyCode currency) {
        this.amount = amount.setScale(currency.scale(), RoundingMode.HALF_UP);
        this.currency = currency;
    }

    public static Money of(BigDecimal amount, CurrencyCode currency) {
        return new Money(amount, currency);
    }

    public static Money of(String amount, CurrencyCode currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money zero(CurrencyCode currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public CurrencyCode currency() {
        return currency;
    }

    public BigDecimal amount() {
        return amount;
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), currency);
    }

    public Money negate() {
        return new Money(this.amount.negate(), currency);
    }

    /**
     * Multiplies by a plain (unitless) rate -- e.g. the 0.0004 daily interest
     * rate -- and rounds the result to this Money's currency precision. This
     * is the ONLY place uncontrolled rounding happens; every other operation
     * on already-scaled Money values is exact.
     */
    public Money multiply(BigDecimal rate) {
        return new Money(this.amount.multiply(rate), currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isGreaterThanOrEqualToZero() {
        return amount.signum() >= 0;
    }

    private void requireSameCurrency(Money other) {
        if (this.currency != other.currency) {
            throw new IllegalArgumentException(
                    "Cannot combine " + this.currency + " with " + other.currency
                            + " -- this ledger does not perform currency conversion.");
        }
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money)) return false;
        Money money = (Money) o;
        return amount.equals(money.amount) && currency == money.currency;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }

    @Override
    public String toString() {
        return currency + " " + amount.toPlainString();
    }
}
