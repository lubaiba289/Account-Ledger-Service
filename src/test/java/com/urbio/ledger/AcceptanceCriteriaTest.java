package com.urbio.ledger;

import com.urbio.ledger.engine.*;
import com.urbio.ledger.model.*;
import com.urbio.ledger.scenario.Scenario;

import java.math.BigDecimal;
import java.util.List;

import static com.urbio.ledger.testing.MiniTest.*;

/**
 * One test per acceptance criterion from the take-home. Each is labelled
 * ACCEPTED or REJECTED to match REJECTED.md; a REJECTED criterion's test
 * asserts what the engine ACTUALLY does (which is why the criterion is
 * wrong), not what the criterion claimed.
 */
public final class AcceptanceCriteriaTest {

    public static void run() {
        System.out.println("AcceptanceCriteriaTest:");

        test("#1 ACCEPTED: Day2 closing balance, evaluated at end of Day5, before any fee, is AED -370.00", () -> {
            LedgerBook book = freshBook();
            new ReplayEngine(book).replay(Scenario.events(), 5); // stop exactly at end of Day5, before E9
            Money day2Recomputed = book.ledgerFor(Scenario.ACC_001).closingBalanceAsOf(2);
            assertEquals(aed("-370.00"), day2Recomputed, "Day2 balance recomputed as of end of Day5");
        });

        test("#2 REJECTED: E7 causes exactly one fee, but it lands on Day5, not Day2", () -> {
            LedgerBook book = freshBook();
            ReplayEngine engine = new ReplayEngine(book);
            engine.replay(Scenario.events(), 6);
            List<DayCloseResult> acc001 = engine.dayCloseResults();
            DayCloseResult day2 = acc001.stream().filter(r -> r.accountId().equals(Scenario.ACC_001) && r.day() == 2).findFirst().orElseThrow();
            DayCloseResult day5 = acc001.stream().filter(r -> r.accountId().equals(Scenario.ACC_001) && r.day() == 5).findFirst().orElseThrow();
            assertNull(day2.feeAssessed(), "Day2 was never assessed a fee -- it closed positive, before E7 existed");
            assertEquals(aed("25.00"), day5.feeAssessed(), "the one fee E7 causes is dated Day5, its own processing day");
        });

        test("#3 ACCEPTED: Day4 settlement of Auth-A (for 185.00, less than the 200.00 hold) is accepted", () -> {
            LedgerBook book = freshBook();
            new ReplayEngine(book).replay(Scenario.events(), 4);
            Authorization authA = book.ledgerFor(Scenario.ACC_001).authorization("Auth-A");
            assertEquals(AuthorizationStatus.SETTLED, authA.status(), "Auth-A status after E5");
            assertEquals(aed("185.00"), authA.settledAmount(), "Auth-A settled amount");
        });

        test("#4 ACCEPTED: settlement referencing an unknown authorization id is rejected, no funds move", () -> {
            LedgerBook book = freshBook();
            ReplayEngine engine = new ReplayEngine(book);
            engine.replay(Scenario.events(), 4);
            assertNull(book.ledgerFor(Scenario.ACC_001).authorization("Auth-Z"), "Auth-Z must not exist");
            boolean rejected = engine.errors().stream().anyMatch(e -> e.eventId().equals("E6"));
            assertTrue(rejected, "E6 (settle unknown Auth-Z) must be recorded as an error");
        });

        test("#5 ACCEPTED (vacuously): a HELD authorization's hold reduces available, not ledger, balance", () -> {
            // General rule is correct; in THIS stream Auth-B is actually REJECTED at
            // creation (see AMBIGUITIES.md), so the premise never triggers -- but we
            // can still verify the rule directly using Auth-A while it was HELD.
            LedgerBook book = freshBook();
            new ReplayEngine(book).replay(Scenario.events(), 2); // Auth-A HELD, not yet settled
            Ledger ledger = book.ledgerFor(Scenario.ACC_001);
            assertEquals(aed("250.00"), ledger.currentLedgerBalance(), "ledger balance unaffected by a HELD hold");
            assertEquals(aed("50.00"), ledger.availableBalance(), "available balance IS reduced by the HELD hold");
        });

        test("#6 REJECTED: after E9, balances/fees do NOT return to pre-E7 values (Day5 fee persists)", () -> {
            LedgerBook book = freshBook();
            new ReplayEngine(book).replay(Scenario.events(), 6);
            Ledger ledger = book.ledgerFor(Scenario.ACC_001);
            // Pre-E7 (end of Day4) balance was 465.00. If E9 truly undid everything
            // E7-related, post-E9 Day5-recomputed balance would also be 465.00.
            Money day5Recomputed = ledger.closingBalanceAsOf(5);
            assertEquals(aed("440.00"), day5Recomputed,
                    "Day5 recomputed balance after E9 is 440.00, NOT the pre-E7 465.00 -- the -25.00 fee never reverses");
        });

        test("#7 REJECTED: E10's three instalments are NOT all 3.334 (that would sum to 10.002, not 10.000)", () -> {
            LedgerBook book = freshBook();
            new ReplayEngine(book).replay(Scenario.events(), 5);
            List<LedgerEntry> entries = book.ledgerFor(Scenario.ACC_002).entries();
            boolean allThreeAre3334 = entries.stream().allMatch(e -> e.amount().equals(bhd("3.334")));
            assertTrue(!allThreeAre3334, "instalments must not all be 3.334");
            assertEquals(bhd("10.000"), book.ledgerFor(Scenario.ACC_002).currentLedgerBalance(),
                    "the three instalments must still sum exactly to 10.000");
        });

        test("#8 REJECTED: rounding remainder is reconciled, never discarded", () -> {
            LedgerBook book = freshBook();
            ReplayEngine engine = new ReplayEngine(book);
            engine.replay(Scenario.events(), 6);
            DayCloseResult finalDay = engine.dayCloseResults().stream()
                    .filter(r -> r.accountId().equals(Scenario.ACC_001) && r.day() == 6).findFirst().orElseThrow();
            BigDecimal sumOfDailyAccruals = new BigDecimal("0.10").add(new BigDecimal("0.10"))
                    .add(new BigDecimal("0.26")).add(new BigDecimal("0.19")).add(new BigDecimal("0.18"));
            assertEquals(Money.of(sumOfDailyAccruals, CurrencyCode.AED), finalDay.interestCapitalized(),
                    "capitalized total must equal the exact sum of the rounded daily accruals, nothing discarded");
        });

        System.out.println();
    }

    private static LedgerBook freshBook() {
        LedgerBook book = new LedgerBook();
        book.open(new Account(Scenario.ACC_001, CurrencyCode.AED));
        book.open(new Account(Scenario.ACC_002, CurrencyCode.BHD));
        return book;
    }

    private static Money aed(String amount) { return Money.of(new BigDecimal(amount), CurrencyCode.AED); }
    private static Money bhd(String amount) { return Money.of(new BigDecimal(amount), CurrencyCode.BHD); }
}
