package com.urbio.ledger.engine;

import com.urbio.ledger.events.*;
import com.urbio.ledger.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Replays an ordered event stream against a {@link LedgerBook}, one
 * processing day at a time. For every day in the six-day window it:
 *
 * <ol>
 *   <li>applies every event whose {@code processedDay} equals that day, in
 *       stream order (append-only postings, hold approve/reject,
 *       settlement, reversal);</li>
 *   <li>then closes the day for every open account: computes that day's
 *       closing balance using every entry known so far, and -- exactly
 *       once, using only what is known at that moment -- either assesses an
 *       overdraft fee (balance &lt; 0) or records that day's interest
 *       accrual (balance &gt; 0), never both, never neither unless the
 *       balance is exactly zero;</li>
 *   <li>on the last day only, after that day's own fee/interest decision,
 *       capitalizes the sum of all six (rounded) daily accruals as one
 *       credit.</li>
 * </ol>
 *
 * See AMBIGUITIES.md for why fee/interest assessment is "once, at that
 * day's own close" rather than retroactively re-evaluated whenever a
 * backdated entry lands -- and REJECTED.md for the acceptance criterion
 * that assumed the opposite.
 */
public final class ReplayEngine {

    private final LedgerBook book;
    private final List<ProcessingError> errors = new ArrayList<>();
    private final List<DayCloseResult> dayCloseResults = new ArrayList<>();
    private final Map<String, List<LedgerEntry>> entriesByOriginatingEventId = new LinkedHashMap<>();
    private final Map<String, List<Money>> pendingDailyInterest = new LinkedHashMap<>();

    public ReplayEngine(LedgerBook book) {
        this.book = book;
        for (String accountId : book.all().keySet()) {
            pendingDailyInterest.put(accountId, new ArrayList<>());
        }
    }

    public List<ProcessingError> errors() { return errors; }
    public List<DayCloseResult> dayCloseResults() { return dayCloseResults; }

    /**
     * Replays every event with {@code processedDay <= throughDay}, then
     * closes days {@link LedgerConstants#FIRST_DAY}..{@code throughDay} in
     * order. Events MUST already be in non-decreasing {@code processedDay}
     * order (i.e. the order they were actually received/replayed in) --
     * this engine does not re-sort them, because doing so would hide
     * exactly the backdating behaviour the exercise is testing.
     *
     * <p>Calling this with {@code throughDay} less than the full window
     * (e.g. 5 instead of 6) replays a true PREFIX of the stream and stops
     * -- it deliberately does NOT process later events. This is what lets
     * the test suite ask "what did the ledger look like at the end of Day
     * 5, before Day 6's reversal happened" (see acceptance criterion #1 in
     * REJECTED.md / AcceptanceCriteriaTest), as distinct from
     * {@link com.urbio.ledger.model.Ledger#closingBalanceAsOf} which
     * answers "what does day D's balance look like using everything known
     * right now" against whatever state the ledger is currently in.
     */
    public void replay(List<Event> events, int throughDay) {
        int idx = 0;
        for (int day = LedgerConstants.FIRST_DAY; day <= throughDay; day++) {
            while (idx < events.size() && events.get(idx).processedDay() == day) {
                apply(events.get(idx));
                idx++;
            }
            closeDay(day, day == LedgerConstants.LAST_DAY);
        }
        if (throughDay == LedgerConstants.LAST_DAY) {
            for (Ledger ledger : book.all().values()) {
                for (Authorization a : ledger.authorizations().values()) {
                    a.markOutstandingAtWindowEnd();
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Event application
    // ------------------------------------------------------------------

    private void apply(Event event) {
        if (event instanceof CreditEvent) {
            applyCredit((CreditEvent) event);
        } else if (event instanceof DebitEvent) {
            applyDebit((DebitEvent) event);
        } else if (event instanceof AuthorizationEvent) {
            applyAuthorization((AuthorizationEvent) event);
        } else if (event instanceof SettlementEvent) {
            applySettlement((SettlementEvent) event);
        } else if (event instanceof ReversalEvent) {
            applyReversal((ReversalEvent) event);
        } else {
            throw new IllegalArgumentException("Unknown event type: " + event.getClass());
        }
    }

    private void applyCredit(CreditEvent event) {
        Ledger ledger = book.ledgerFor(event.accountId());
        List<Money> shares = splitEvenly(event.amount(), event.installments());
        List<LedgerEntry> booked = new ArrayList<>();
        for (int i = 0; i < shares.size(); i++) {
            String entryId = shares.size() == 1 ? event.id() : event.id() + "-" + (i + 1);
            LedgerEntry entry = new LedgerEntry(entryId, event.accountId(), EntryType.CREDIT,
                    shares.get(i), event.valueDate(), event.processedDay(),
                    "credit from " + event.id()
                            + (shares.size() > 1 ? " (instalment " + (i + 1) + "/" + shares.size() + ")" : ""),
                    null);
            ledger.append(entry);
            booked.add(entry);
        }
        entriesByOriginatingEventId.put(event.id(), booked);
    }

    private void applyDebit(DebitEvent event) {
        Ledger ledger = book.ledgerFor(event.accountId());
        LedgerEntry entry = new LedgerEntry(event.id(), event.accountId(), EntryType.DEBIT,
                event.amount().negate(), event.valueDate(), event.processedDay(),
                "debit from " + event.id(), null);
        ledger.append(entry);
        entriesByOriginatingEventId.put(event.id(), List.of(entry));
    }

    private void applyAuthorization(AuthorizationEvent event) {
        Ledger ledger = book.ledgerFor(event.accountId());
        Authorization authorization = new Authorization(event.authorizationId(), event.accountId(),
                event.holdAmount(), event.processedDay());

        Money availableIfApplied = ledger.availableBalance().minus(event.holdAmount());
        if (availableIfApplied.isGreaterThanOrEqualToZero()) {
            ledger.putAuthorization(authorization); // status defaults to HELD
        } else {
            authorization.markRejected("available balance would be " + availableIfApplied
                    + " (ledger " + ledger.currentLedgerBalance() + " - existing holds "
                    + ledger.activeHoldsTotal() + " - requested hold " + event.holdAmount() + ")");
            ledger.putAuthorization(authorization);
            errors.add(new ProcessingError(event.processedDay(), event.id(),
                    "authorization " + event.authorizationId() + " REJECTED: " + authorization.note()));
        }
    }

    private void applySettlement(SettlementEvent event) {
        Ledger ledger = book.ledgerFor(event.accountId());
        Authorization authorization = ledger.authorization(event.authorizationId());
        if (authorization == null) {
            errors.add(new ProcessingError(event.processedDay(), event.id(),
                    "settlement references unknown authorization " + event.authorizationId()
                            + " -- rejected, no funds moved"));
            return;
        }
        if (authorization.status() != AuthorizationStatus.HELD) {
            errors.add(new ProcessingError(event.processedDay(), event.id(),
                    "settlement references authorization " + event.authorizationId()
                            + " which is not currently HELD (status=" + authorization.status()
                            + ") -- rejected, no funds moved"));
            return;
        }
        authorization.markSettled(event.settledAmount(), event.processedDay());
        LedgerEntry entry = new LedgerEntry(event.id(), event.accountId(), EntryType.AUTH_SETTLEMENT_DEBIT,
                event.settledAmount().negate(), event.valueDate(), event.processedDay(),
                "settlement of " + event.authorizationId(), event.authorizationId());
        ledger.append(entry);
        entriesByOriginatingEventId.put(event.id(), List.of(entry));
    }

    private void applyReversal(ReversalEvent event) {
        List<LedgerEntry> originals = entriesByOriginatingEventId.get(event.reversedEventId());
        if (originals == null || originals.isEmpty()) {
            errors.add(new ProcessingError(event.processedDay(), event.id(),
                    "reversal references unknown or unbooked event " + event.reversedEventId()
                            + " -- rejected"));
            return;
        }
        Ledger ledger = book.ledgerFor(event.accountId());
        List<LedgerEntry> booked = new ArrayList<>();
        int i = 0;
        for (LedgerEntry original : originals) {
            i++;
            String entryId = originals.size() == 1 ? event.id() : event.id() + "-" + i;
            LedgerEntry reversal = new LedgerEntry(entryId, event.accountId(), EntryType.REVERSAL,
                    original.amount().negate(), event.valueDate(), event.processedDay(),
                    "reversal of " + event.reversedEventId(), original.relatedAuthorizationId());
            ledger.append(reversal);
            booked.add(reversal);
        }
        entriesByOriginatingEventId.put(event.id(), booked);
    }

    // ------------------------------------------------------------------
    // Day close: fee / interest, once per day per account
    // ------------------------------------------------------------------

    private void closeDay(int day, boolean isLastDay) {
        for (Ledger ledger : book.all().values()) {
            String accountId = ledger.account().id();
            CurrencyCode currency = ledger.account().currency();
            Money closing = ledger.closingBalanceAsOf(day);

            Money fee = null;
            Money interest = null;

            if (closing.isNegative()) {
                Money feeAmount = LedgerConstants.overdraftFeeFor(currency);
                LedgerEntry feeEntry = new LedgerEntry("FEE-D" + day + "-" + accountId, accountId,
                        EntryType.OVERDRAFT_FEE, feeAmount.negate(), day, day,
                        "overdraft fee for day " + day + " closing balance " + closing, null);
                ledger.append(feeEntry);
                fee = feeAmount;
            } else if (closing.isPositive()) {
                interest = closing.multiply(LedgerConstants.DAILY_INTEREST_RATE);
                pendingDailyInterest.get(accountId).add(interest);
            }

            Money capitalized = null;
            if (isLastDay) {
                capitalized = sumInterest(pendingDailyInterest.get(accountId), currency);
                if (capitalized.isPositive()) {
                    LedgerEntry capEntry = new LedgerEntry("CAP-D" + day + "-" + accountId, accountId,
                            EntryType.INTEREST_CAPITALIZATION, capitalized, day, day,
                            "capitalized interest for days " + LedgerConstants.FIRST_DAY + "-" + day, null);
                    ledger.append(capEntry);
                } else {
                    capitalized = null;
                }
            }

            dayCloseResults.add(new DayCloseResult(accountId, day, closing, fee, interest, capitalized));
        }
    }

    private Money sumInterest(List<Money> accruals, CurrencyCode currency) {
        Money total = Money.zero(currency);
        for (Money m : accruals) {
            total = total.plus(m);
        }
        return total;
    }

    /**
     * Splits {@code total} into {@code parts} shares that are each rounded
     * to the currency's own precision and that sum EXACTLY back to
     * {@code total}. Each share starts at total/parts truncated down to the
     * currency scale; whatever remainder is left over (always less than one
     * minor unit per remaining share) is handed out one minor unit at a time
     * to the LAST shares first. See NUMBERS.md.
     */
    static List<Money> splitEvenly(Money total, int parts) {
        if (parts < 1) {
            throw new IllegalArgumentException("parts must be >= 1");
        }
        CurrencyCode currency = total.currency();
        int scale = currency.scale();
        BigDecimal exactShare = total.amount().divide(BigDecimal.valueOf(parts), scale, RoundingMode.DOWN);
        BigDecimal minorUnit = BigDecimal.ONE.movePointLeft(scale);

        List<BigDecimal> shares = new ArrayList<>();
        BigDecimal runningSum = BigDecimal.ZERO;
        for (int i = 0; i < parts; i++) {
            shares.add(exactShare);
            runningSum = runningSum.add(exactShare);
        }
        BigDecimal remainder = total.amount().subtract(runningSum);
        int remainderUnits = remainder.divide(minorUnit, 0, RoundingMode.HALF_UP).intValue();
        for (int i = 0; i < remainderUnits; i++) {
            int targetIndex = parts - 1 - i; // hand the odd cents/fils to the last instalment(s)
            shares.set(targetIndex, shares.get(targetIndex).add(minorUnit));
        }

        List<Money> result = new ArrayList<>();
        for (BigDecimal share : shares) {
            result.add(Money.of(share, currency));
        }
        return result;
    }
}
