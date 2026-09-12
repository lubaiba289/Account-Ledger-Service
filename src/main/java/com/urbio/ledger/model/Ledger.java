package com.urbio.ledger.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * One account's append-only ledger: an ordered list of {@link LedgerEntry}
 * postings that is only ever appended to (never mutated, never truncated),
 * plus a table of {@link Authorization} holds keyed by authorization id.
 *
 * <p>"Ledger balance" and "available balance" are both DERIVED, never
 * stored: they are recomputed on demand from the entry list and the hold
 * table. This is what lets a backdated entry correctly change the answer to
 * "what was day 2's closing balance" when asked again later, without ever
 * touching a previously-appended record.
 */
public final class Ledger {

    private final Account account;
    private final List<LedgerEntry> entries = new ArrayList<>();
    private final Map<String, Authorization> authorizations = new LinkedHashMap<>();

    public Ledger(Account account) {
        this.account = account;
    }

    public Account account() {
        return account;
    }

    public void append(LedgerEntry entry) {
        if (!entry.accountId().equals(account.id())) {
            throw new IllegalArgumentException("Entry for " + entry.accountId()
                    + " appended to ledger of " + account.id());
        }
        entries.add(entry);
    }

    public List<LedgerEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * The ledger balance "as of now" for value dates up to and including
     * {@code day}: the sum of every entry appended so far (regardless of
     * when it was posted) whose value_date is &lt;= day. This is a pure,
     * repeatable query -- calling it again later, after more backdated
     * entries land, can legitimately return a different answer for the same
     * day. That is intentional: see AMBIGUITIES.md ("recomputed" vs
     * "as-assessed" balances).
     */
    public Money closingBalanceAsOf(int day) {
        Money total = Money.zero(account.currency());
        for (LedgerEntry e : entries) {
            if (e.valueDate() <= day) {
                total = total.plus(e.amount());
            }
        }
        return total;
    }

    /** The current running ledger balance: sum of every entry appended so
     * far, irrespective of value date. In this scenario no entry is ever
     * value-dated in the future relative to the day it is posted, so this
     * always equals {@code closingBalanceAsOf(currentDay)} -- see
     * AMBIGUITIES.md for why that equivalence matters for authorization
     * checks. */
    public Money currentLedgerBalance() {
        Money total = Money.zero(account.currency());
        for (LedgerEntry e : entries) {
            total = total.plus(e.amount());
        }
        return total;
    }

    public Money activeHoldsTotal() {
        Money total = Money.zero(account.currency());
        for (Authorization a : authorizations.values()) {
            if (a.status() == AuthorizationStatus.HELD) {
                total = total.plus(a.holdAmount());
            }
        }
        return total;
    }

    public Money availableBalance() {
        return currentLedgerBalance().minus(activeHoldsTotal());
    }

    public void putAuthorization(Authorization authorization) {
        authorizations.put(authorization.id(), authorization);
    }

    public Authorization authorization(String id) {
        return authorizations.get(id);
    }

    public Map<String, Authorization> authorizations() {
        return Collections.unmodifiableMap(authorizations);
    }
}
