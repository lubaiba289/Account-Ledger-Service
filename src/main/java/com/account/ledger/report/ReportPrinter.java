package com.account.ledger.report;

import com.account.ledger.engine.DayCloseResult;
import com.account.ledger.engine.LedgerBook;
import com.account.ledger.engine.ProcessingError;
import com.account.ledger.model.Authorization;
import com.account.ledger.model.Ledger;
import com.account.ledger.model.LedgerEntry;

import java.io.PrintStream;
import java.util.List;

/** Prints the per-day report the take-home requires: closing ledger
 * balance, fee assessments, authorization states, and errors, for every day
 * in the window. Plain text, deterministic, no external formatting
 * dependency -- easy to diff or paste into a review. */
public final class ReportPrinter {

    private final PrintStream out;

    public ReportPrinter(PrintStream out) {
        this.out = out;
    }

    public void printDailyReport(LedgerBook book, List<DayCloseResult> results,
                                  List<ProcessingError> errors, int firstDay, int lastDay) {
        for (int day = firstDay; day <= lastDay; day++) {
            out.println("================ Day " + day + " ================");
            for (DayCloseResult r : results) {
                if (r.day() != day) continue;
                out.println("  " + r.accountId() + " closing ledger balance (value_date<=" + day + "): "
                        + r.closingBalanceAtClose());
                if (r.feeAssessed() != null) {
                    out.println("    overdraft fee assessed: " + r.feeAssessed());
                }
                if (r.interestAccrued() != null) {
                    out.println("    daily interest accrued (not yet capitalized): " + r.interestAccrued());
                }
                if (r.interestCapitalized() != null) {
                    out.println("    interest capitalized (sum of days 1-" + day + "): " + r.interestCapitalized());
                }
            }

            out.println("  Authorization states as of end of day " + day + ":");
            for (Ledger ledger : book.all().values()) {
                for (Authorization a : ledger.authorizations().values()) {
                    if (a.requestedDay() <= day) {
                        out.println("    " + a);
                    }
                }
            }

            boolean anyErrorToday = false;
            for (ProcessingError e : errors) {
                if (e.day() == day) {
                    if (!anyErrorToday) {
                        out.println("  Errors on day " + day + ":");
                        anyErrorToday = true;
                    }
                    out.println("    " + e);
                }
            }
        }

        out.println("================ Final ledger entries (append-only, in booking order) ================");
        for (Ledger ledger : book.all().values()) {
            out.println("  " + ledger.account() + " final balance: " + ledger.currentLedgerBalance());
            for (LedgerEntry entry : ledger.entries()) {
                out.println("    " + entry);
            }
        }
    }
}
