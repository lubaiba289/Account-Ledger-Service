package com.urbio.ledger;

import com.urbio.ledger.engine.*;
import com.urbio.ledger.model.*;
import com.urbio.ledger.scenario.Scenario;

import java.math.BigDecimal;
import java.util.List;

import static com.urbio.ledger.testing.MiniTest.*;

/**
 * The one test the spec explicitly asks for: a failing test against my own
 * design, kept in the suite on purpose, with an inline note on what it
 * reveals. It is run through {@link com.urbio.ledger.testing.MiniTest#expectedFailure}
 * so the overall suite still exits 0 -- an "expected failure" is not the
 * same thing as a broken build.
 *
 * <p>WHAT THIS TEST ASSERTS: under the alternative ("retroactive")
 * reading of the overdraft-fee rule, a backdated entry that flips an
 * ALREADY-CLOSED day's recomputed closing balance from non-negative to
 * negative should retroactively book a fee dated to THAT earlier day
 * (Day 2, value-dated with E7's own value date) the moment the backdated
 * entry is seen (Day 5) -- rather than only ever evaluating Day 2 once,
 * at Day 2's own close, and letting Day 5's own close pick up E7's effect
 * on Day 5's balance instead. See AMBIGUITIES.md ("once-per-day-at-close"
 * vs. "retroactive re-check") and REJECTED.md (#2) for the full argument
 * for why the engine implements the FIRST reading, not this one.
 *
 * <p>WHAT IT REVEALS: the two readings are not just a cosmetic
 * difference of which day gets blamed. Under the retroactive reading, E7
 * would ALSO still make Day 5's own close negative (E7's value date, 2,
 * is <= 5 either way), so a naive "recheck every day whenever an entry
 * lands" implementation would double-charge -- one fee for Day 2 AND
 * one for Day 5 -- unless it adds extra bookkeeping to suppress the
 * second charge. That extra rule is nowhere in the spec, which is the
 * concrete reason this design chose NOT to implement retroactive
 * re-assessment. This test is the executable record of that fork, kept
 * failing so a reviewer who disagrees has a precise, runnable place to
 * start the alternative implementation rather than a paragraph of prose
 * to take on faith.
 */
public final class FailingDesignGapTest {

    public static void run() {
        System.out.println("FailingDesignGapTest:");

        expectedFailure(
                "retroactive reading: E7 should book a Day2-dated fee the moment it lands on Day5",
                "This engine assesses each day's overdraft fee exactly once, at that day's own "
                        + "close, using only what was known at that moment. Day 2 closed positive "
                        + "(250.00) before E7 (posted Day5, value-dated Day2) ever existed, so Day 2 "
                        + "is never revisited. A design that instead re-evaluates every affected "
                        + "day whenever a backdated entry lands would need additional, unspecified "
                        + "rules to avoid double-charging (Day2 AND Day5 both go negative because of "
                        + "the same E7). This test documents that fork explicitly instead of silently "
                        + "picking a side.",
                () -> {
                    LedgerBook book = new LedgerBook();
                    book.open(new Account(Scenario.ACC_001, CurrencyCode.AED));
                    book.open(new Account(Scenario.ACC_002, CurrencyCode.BHD));
                    ReplayEngine engine = new ReplayEngine(book);
                    engine.replay(Scenario.events(), 6);

                    List<LedgerEntry> feeEntriesValueDatedDay2 = book.ledgerFor(Scenario.ACC_001).entries()
                            .stream()
                            .filter(e -> e.type() == EntryType.OVERDRAFT_FEE && e.valueDate() == 2)
                            .collect(java.util.stream.Collectors.toList());

                    // Under the retroactive reading this list would have one entry.
                    // Under this engine's actual (documented) design it has zero,
                    // so the assertion below fails on purpose.
                    assertEquals(1, feeEntriesValueDatedDay2.size(),
                            "expected one Day2-value-dated overdraft fee entry under the retroactive reading");
                });

        System.out.println();
    }
}
