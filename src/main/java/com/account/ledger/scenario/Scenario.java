package com.account.ledger.scenario;

import com.account.ledger.events.*;
import com.account.ledger.model.CurrencyCode;
import com.account.ledger.model.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Builds the exact ten-event stream E1..E10 from the take-home spec, in
 * the exact order given. This is the ONLY place those events are
 * hand-encoded -- both {@code Main} (prints the report) and the test suite
 * replay this same list, so the scenario can never drift between "what we
 * demo" and "what we test". */
public final class Scenario {

    public static final String ACC_001 = "ACC-001";
    public static final String ACC_002 = "ACC-002";

    private Scenario() {}

    public static List<Event> events() {
        List<Event> events = new ArrayList<>();

        // E1 - Day1 - CREDIT - ACC-001 AED 1200.00 - value_date Day1
        events.add(new CreditEvent("E1", 1, 1, ACC_001, aed("1200.00")));

        // E2 - Day1 - DEBIT - ACC-001 AED 950.00 - value_date Day1
        events.add(new DebitEvent("E2", 1, 1, ACC_001, aed("950.00")));

        // E3 - Day2 - AUTHORIZATION - ACC-001 Auth-A hold AED 200.00 - value_date Day2
        events.add(new AuthorizationEvent("E3", 2, 2, ACC_001, "Auth-A", aed("200.00")));

        // E4 - Day3 - CREDIT - ACC-001 AED 400.00 - value_date Day3
        events.add(new CreditEvent("E4", 3, 3, ACC_001, aed("400.00")));

        // E5 - Day4 - SETTLEMENT - ACC-001 Auth-A settles for AED 185.00 - value_date Day4
        events.add(new SettlementEvent("E5", 4, 4, ACC_001, "Auth-A", aed("185.00")));

        // E6 - Day4 - SETTLEMENT - ACC-001 Auth-Z settles for AED 180.00 - value_date Day4
        // (Auth-Z has no preceding authorization event -- must be rejected)
        events.add(new SettlementEvent("E6", 4, 4, ACC_001, "Auth-Z", aed("180.00")));

        // E7 - Day5 - DEBIT - ACC-001 AED 620.00 - value_date Day2 (backdated)
        events.add(new DebitEvent("E7", 5, 2, ACC_001, aed("620.00")));

        // E8 - Day5 - AUTHORIZATION - ACC-001 Auth-B hold AED 90.00 - value_date Day5
        events.add(new AuthorizationEvent("E8", 5, 5, ACC_001, "Auth-B", aed("90.00")));

        // E10 - Day5 - CREDIT - ACC-002 BHD 10.000, posted as three equal instalments - value_date Day5
        // (listed here, before E9, to match the given processedDay=5 <= E9's processedDay=6;
        //  order relative to E8/E7 does not matter since it is a different account)
        events.add(new CreditEvent("E10", 5, 5, ACC_002, bhd("10.000"), 3));

        // E9 - Day6 - REVERSAL - ACC-001 reverses E7 - value_date Day2
        events.add(new ReversalEvent("E9", 6, 2, ACC_001, "E7"));

        return events;
    }

    private static Money aed(String amount) {
        return Money.of(new BigDecimal(amount), CurrencyCode.AED);
    }

    private static Money bhd(String amount) {
        return Money.of(new BigDecimal(amount), CurrencyCode.BHD);
    }
}
