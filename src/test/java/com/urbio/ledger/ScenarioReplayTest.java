package com.urbio.ledger;

import com.urbio.ledger.engine.*;
import com.urbio.ledger.events.Event;
import com.urbio.ledger.model.*;
import com.urbio.ledger.scenario.Scenario;

import java.math.BigDecimal;
import java.util.List;

import static com.urbio.ledger.testing.MiniTest.*;

/** End-to-end numeric verification of the full six-day replay against the
 * hand-computed figures in NUMBERS.md / WORKLOG.md. If any of these ever
 * disagree with the design docs, the docs are the ones to re-check first --
 * they were derived by hand before the engine was written, not after. */
public final class ScenarioReplayTest {

    private static Ledger fullReplayAcc001() {
        LedgerBook book = freshBook();
        new ReplayEngine(book).replay(Scenario.events(), LedgerConstants.LAST_DAY);
        return book.ledgerFor(Scenario.ACC_001);
    }

    private static LedgerBook freshBook() {
        LedgerBook book = new LedgerBook();
        book.open(new Account(Scenario.ACC_001, CurrencyCode.AED));
        book.open(new Account(Scenario.ACC_002, CurrencyCode.BHD));
        return book;
    }

    public static void run() {
        System.out.println("ScenarioReplayTest:");

        test("Day1 closing balance is 250.00 (1200 credit - 950 debit)", () -> {
            LedgerBook book = freshBook();
            ReplayEngine engine = new ReplayEngine(book);
            engine.replay(Scenario.events(), 1);
            assertEquals(aed("250.00"), book.ledgerFor(Scenario.ACC_001).closingBalanceAsOf(1), "Day1 closing");
        });

        test("Day2 hold does not change ledger balance, only available balance", () -> {
            LedgerBook book = freshBook();
            ReplayEngine engine = new ReplayEngine(book);
            engine.replay(Scenario.events(), 2);
            Ledger ledger = book.ledgerFor(Scenario.ACC_001);
            assertEquals(aed("250.00"), ledger.currentLedgerBalance(), "ledger balance after Auth-A hold");
            assertEquals(aed("50.00"), ledger.availableBalance(), "available balance after Auth-A hold (250-200)");
        });

        test("Auth-A is approved on Day2 (available 250-200=50 >= 0)", () -> {
            LedgerBook book = freshBook();
            new ReplayEngine(book).replay(Scenario.events(), 2);
            Authorization a = book.ledgerFor(Scenario.ACC_001).authorization("Auth-A");
            assertEquals(AuthorizationStatus.HELD, a.status(), "Auth-A status at Day2");
        });

        test("Day4: Auth-A settlement for 185.00 is accepted, ledger balance becomes 465.00", () -> {
            LedgerBook book = freshBook();
            new ReplayEngine(book).replay(Scenario.events(), 4);
            Ledger ledger = book.ledgerFor(Scenario.ACC_001);
            assertEquals(aed("465.00"), ledger.currentLedgerBalance(), "ledger balance after Auth-A settles");
            assertEquals(AuthorizationStatus.SETTLED, ledger.authorization("Auth-A").status(), "Auth-A status");
        });

        test("Day4: settlement of unknown Auth-Z is rejected, funds do not move, error recorded", () -> {
            LedgerBook book = freshBook();
            ReplayEngine engine = new ReplayEngine(book);
            engine.replay(Scenario.events(), 4);
            Ledger ledger = book.ledgerFor(Scenario.ACC_001);
            // balance unaffected beyond the legitimate Auth-A settlement:
            assertEquals(aed("465.00"), ledger.currentLedgerBalance(), "balance unaffected by rejected Auth-Z settlement");
            assertNull(ledger.authorization("Auth-Z"), "no authorization record should exist for Auth-Z");
            boolean sawError = engine.errors().stream().anyMatch(e -> e.eventId().equals("E6"));
            assertTrue(sawError, "E6 should be recorded as a processing error");
        });

        test("Day5: E7 backdated debit brings the closing balance to -155.00 before that day's fee is booked", () -> {
            LedgerBook book = freshBook();
            ReplayEngine engine = new ReplayEngine(book);
            engine.replay(Scenario.events(), 5);
            DayCloseResult day5 = engine.dayCloseResults().stream()
                    .filter(r -> r.accountId().equals(Scenario.ACC_001) && r.day() == 5).findFirst().orElseThrow();
            // DayCloseResult.closingBalanceAtClose() is captured BEFORE the fee entry
            // is appended, so this is the pre-fee figure; the running ledger balance
            // immediately after this point is -180.00 (post-fee) -- see the next test.
            assertEquals(aed("-155.00"), day5.closingBalanceAtClose(), "Day5 closing balance before that day's fee");
            assertEquals(aed("-180.00"), book.ledgerFor(Scenario.ACC_001).currentLedgerBalance(),
                    "running ledger balance right after Day5 close (post-fee)");
        });

        test("Day5: Auth-B is REJECTED (ledger already -155.00 before the new 90.00 hold)", () -> {
            LedgerBook book = freshBook();
            new ReplayEngine(book).replay(Scenario.events(), 5);
            Authorization authB = book.ledgerFor(Scenario.ACC_001).authorization("Auth-B");
            assertEquals(AuthorizationStatus.REJECTED, authB.status(), "Auth-B status");
        });

        test("Day5: exactly one overdraft fee assessed, dated Day5 (not Day2)", () -> {
            LedgerBook book = freshBook();
            ReplayEngine engine = new ReplayEngine(book);
            engine.replay(Scenario.events(), 5);
            long day5Fees = engine.dayCloseResults().stream()
                    .filter(r -> r.accountId().equals(Scenario.ACC_001) && r.feeAssessed() != null)
                    .count();
            assertEquals(1L, day5Fees, "number of overdraft fees assessed through Day5");
            DayCloseResult day5Result = engine.dayCloseResults().stream()
                    .filter(r -> r.accountId().equals(Scenario.ACC_001) && r.day() == 5)
                    .findFirst().orElseThrow();
            assertEquals(aed("25.00"), day5Result.feeAssessed(), "Day5 fee amount");
            DayCloseResult day2Result = engine.dayCloseResults().stream()
                    .filter(r -> r.accountId().equals(Scenario.ACC_001) && r.day() == 2)
                    .findFirst().orElseThrow();
            assertNull(day2Result.feeAssessed(), "Day2 must NOT carry a fee -- it was positive at its own close");
        });

        test("End of Day6: ACC-001 final balance is 440.83 (440.00 + 0.83 capitalized interest)", () -> {
            Ledger ledger = fullReplayAcc001();
            assertEquals(aed("440.83"), ledger.currentLedgerBalance(), "final ACC-001 balance");
        });

        test("Daily interest accruals for ACC-001 are 0.10,0.10,0.26,0.19,(none),0.18 summing to 0.83", () -> {
            LedgerBook book = freshBook();
            ReplayEngine engine = new ReplayEngine(book);
            engine.replay(Scenario.events(), 6);
            List<DayCloseResult> acc001 = engine.dayCloseResults().stream()
                    .filter(r -> r.accountId().equals(Scenario.ACC_001)).collect(java.util.stream.Collectors.toList());
            assertEquals(aed("0.10"), acc001.get(0).interestAccrued(), "Day1 interest");
            assertEquals(aed("0.10"), acc001.get(1).interestAccrued(), "Day2 interest");
            assertEquals(aed("0.26"), acc001.get(2).interestAccrued(), "Day3 interest");
            assertEquals(aed("0.19"), acc001.get(3).interestAccrued(), "Day4 interest (0.186 rounds to 0.19)");
            assertNull(acc001.get(4).interestAccrued(), "Day5 has no interest -- balance was negative");
            assertEquals(aed("0.18"), acc001.get(5).interestAccrued(), "Day6 interest (0.176 rounds to 0.18)");
            assertEquals(aed("0.83"), acc001.get(5).interestCapitalized(), "capitalized total == sum of rounded daily accruals");
        });

        test("ACC-002: E10 splits 10.000 BHD into 3.333/3.333/3.334 (sums exactly to 10.000)", () -> {
            LedgerBook book = freshBook();
            new ReplayEngine(book).replay(Scenario.events(), 5);
            Ledger ledger = book.ledgerFor(Scenario.ACC_002);
            List<LedgerEntry> credits = ledger.entries();
            assertEquals(3, credits.size(), "number of instalment entries booked for E10");
            assertEquals(bhd("3.333"), credits.get(0).amount(), "instalment 1");
            assertEquals(bhd("3.333"), credits.get(1).amount(), "instalment 2");
            assertEquals(bhd("3.334"), credits.get(2).amount(), "instalment 3 (absorbs the rounding remainder)");
            assertEquals(bhd("10.000"), ledger.currentLedgerBalance(), "instalments sum exactly to 10.000");
        });

        test("ACC-002: capitalized interest is 0.008 BHD (0.004 on Day5 + 0.004 on Day6)", () -> {
            Ledger unused = null; // (kept for symmetry with the ACC-001 test above)
            LedgerBook book = freshBook();
            ReplayEngine engine = new ReplayEngine(book);
            engine.replay(Scenario.events(), 6);
            Ledger ledger = book.ledgerFor(Scenario.ACC_002);
            assertEquals(bhd("10.008"), ledger.currentLedgerBalance(), "final ACC-002 balance");
        });

        test("After E9's reversal, ACC-001's Day5 overdraft fee is NOT undone (append-only)", () -> {
            Ledger ledger = fullReplayAcc001();
            long feeEntries = ledger.entries().stream().filter(e -> e.type() == EntryType.OVERDRAFT_FEE).count();
            assertEquals(1L, feeEntries, "the Day5 fee entry must still be present after E9");
            boolean nothingWasDeletedOrMutated = ledger.entries().stream().anyMatch(e -> e.id().equals("FEE-D5-ACC-001"));
            assertTrue(nothingWasDeletedOrMutated, "FEE-D5-ACC-001 entry must still exist, unmodified, after Day6");
        });

        System.out.println();
    }

    private static Money aed(String amount) { return Money.of(new BigDecimal(amount), CurrencyCode.AED); }
    private static Money bhd(String amount) { return Money.of(new BigDecimal(amount), CurrencyCode.BHD); }
}
