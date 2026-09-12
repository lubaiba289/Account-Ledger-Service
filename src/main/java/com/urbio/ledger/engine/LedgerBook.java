package com.urbio.ledger.engine;

import com.urbio.ledger.model.Account;
import com.urbio.ledger.model.Ledger;

import java.util.LinkedHashMap;
import java.util.Map;

/** The whole in-memory ledger core: every account's {@link Ledger}, keyed by
 * account id. This is the "database" the spec forbids having an actual
 * database for -- it lives for the lifetime of one JVM process/run. */
public final class LedgerBook {

    private final Map<String, Ledger> ledgers = new LinkedHashMap<>();

    public Ledger open(Account account) {
        Ledger ledger = new Ledger(account);
        ledgers.put(account.id(), ledger);
        return ledger;
    }

    public Ledger ledgerFor(String accountId) {
        Ledger ledger = ledgers.get(accountId);
        if (ledger == null) {
            throw new IllegalArgumentException("No such account: " + accountId);
        }
        return ledger;
    }

    public Map<String, Ledger> all() {
        return ledgers;
    }
}
