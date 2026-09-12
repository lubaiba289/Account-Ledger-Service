# Account Ledger Service

An in-memory account ledger core: no web layer, no persistence, no UI, no
database. It is a plain Java library plus a runnable scenario/report and a
runnable test suite, both driven from a hand-built (but fully deterministic)
six-day event stream.

## Requirements

Just a JDK. Developed and tested against OpenJDK 21, but the code is
deliberately Java-11-compatible (no records, no sealed types, no pattern
matching) and has zero external dependencies -- not even a test framework
-- so it should build on whatever JDK 11+ happens to be on hand. See
NUMBERS.md for why no build tool or test framework was used.

## How to run it

```bash
./run-scenario.sh   # builds, then replays E1..E10 and prints the per-day report
./run-tests.sh       # builds, then runs the full test suite
```

(Or manually: `./build.sh` compiles everything to `./out`, then
`java -cp out com.urbio.ledger.Main` runs the scenario and
`java -cp out com.urbio.ledger.AllTests` runs the tests.)

## How to read the output

`run-scenario.sh` prints one block per day (Day 1 .. Day 6). Each block
shows, per account:

- **closing ledger balance** -- the sum of every entry booked so far whose
  `value_date` is on or before that day, evaluated using everything known
  *at that point in the replay*.
- **overdraft fee assessed** -- present only on a day where that account's
  closing balance came out negative at that day's own close.
- **daily interest accrued** -- present only on a day where the balance
  came out positive; not yet in the account, just the rounded amount that
  will be capitalized.
- **interest capitalized** -- appears once, on Day 6, as the sum of all six
  days' rounded accruals (see NUMBERS.md for why this sum is exact by
  construction).
- **Authorization states** -- every authorization requested on or before
  that day, and its current status (`HELD`, `SETTLED`, `REJECTED`, or
  `OUTSTANDING_AT_WINDOW_END`).
- **Errors** -- any event rejected that day, and why.

The final section dumps every ledger entry ever appended, in booking order,
for both accounts -- this is the literal append-only log, useful for
tracing exactly which entry caused which balance.

`run-tests.sh` prints `[PASS]` / `[FAIL]` per test, plus one
`[EXPECTED FAIL]` (see `FailingDesignGapTest.java` and the "one failing
test" section below). It exits 0 iff every REQUIRED assertion passed.

## Project layout

```
src/main/java/com/urbio/ledger/
  model/     Money, Account, Ledger, LedgerEntry, Authorization, ...
  events/    the five event types (Credit, Debit, Authorization, Settlement, Reversal)
  engine/    ReplayEngine (event application + daily fee/interest close), LedgerBook
  scenario/  Scenario.java -- the hard-coded E1..E10 stream from the spec
  report/    ReportPrinter -- the per-day console report
  Main.java  entry point

src/test/java/com/urbio/ledger/
  ScenarioReplayTest.java       end-to-end numeric checks against the full replay
  AcceptanceCriteriaTest.java   one test per acceptance criterion (see REJECTED.md)
  FailingDesignGapTest.java     the required failing test, inline-annotated
  testing/MiniTest.java         the ~70-line, zero-dependency test harness
  AllTests.java                 runs everything
```

## Required reading, in order

1. **NUMBERS.md** -- every constant, and why it is not some other
   plausible-looking value.
2. **AMBIGUITIES.md** -- every place the spec under-specified something,
   and the reasoning behind the choice made. Read this before arguing with
   a test's expected value; the reasoning is there, not just the number.
3. **REJECTED.md** -- which of the eight acceptance criteria are wrong, and
   why, plus approaches tried and abandoned mid-build.
4. **WORKLOG.md** -- a timestamped account of how the build actually went.

## Design in one paragraph

Everything is a pure function of an append-only entry list plus a table of
authorization holds. "Closing balance for day D" is not a stored field --
it is `sum of entries with value_date <= D`, computed on demand, which is
what lets a backdated entry (E7, E9) legitimately change the answer for an
already-elapsed day when asked about it *later*, without ever mutating a
previously-booked entry. Fee and interest decisions are different: they are
each made exactly **once** per account per day, at that day's own close, in
strict processing-day order, using only what the ledger held at that
moment -- deliberately NOT re-triggered by a later backdated arrival. See
AMBIGUITIES.md for the full argument, and REJECTED.md #2 for the
acceptance criterion this distinction rejects.
