package com.account.ledger;

import com.account.ledger.engine.LedgerBook;
import com.account.ledger.engine.LedgerConstants;
import com.account.ledger.engine.ReplayEngine;
import com.account.ledger.events.Event;
import com.account.ledger.model.Account;
import com.account.ledger.model.CurrencyCode;
import com.account.ledger.report.ReportPrinter;
import com.account.ledger.scenario.Scenario;

import java.util.List;

/**
 * Runnable entry point: builds the two accounts, replays the fixed E1..E10
 * event stream from the take-home spec, and prints the per-day report.
 *
 * Run with: java -cp out com.account.ledger.Main   (see README.md)
 */
public final class Main {

    public static void main(String[] args) {
        LedgerBook book = new LedgerBook();
        book.open(new Account(Scenario.ACC_001, CurrencyCode.AED));
        book.open(new Account(Scenario.ACC_002, CurrencyCode.BHD));

        List<Event> events = Scenario.events();

        ReplayEngine engine = new ReplayEngine(book);
        engine.replay(events, LedgerConstants.LAST_DAY);

        ReportPrinter printer = new ReportPrinter(System.out);
        printer.printDailyReport(book, engine.dayCloseResults(), engine.errors(),
                LedgerConstants.FIRST_DAY, LedgerConstants.LAST_DAY);
    }
}
