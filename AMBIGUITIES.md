# AMBIGUITIES.md

Every place the spec left more than one defensible reading, what was
chosen, and why. Ordered roughly by how much the choice actually matters to
the final numbers -- the first one is the load-bearing decision of the
whole exercise.

## 1. Does a backdated entry retroactively reopen an already-closed day's fee/interest decision?

**The problem.** The overdraft-fee rule says a fee is "assessed once per
day per account when that day's closing ledger balance ... is negative."
`E7` is a DEBIT processed on Day 5 but value-dated Day 2. By the time E7
posts, Day 2 has already come and gone in the replay -- it closed at
`250.00` (positive, no fee) using only `E1`/`E2`. Once E7 lands, Day 2's
closing balance, *recomputed with everything now known*, is `-370.00`
(`E1 + E2 + E7 = 1200 - 950 - 620`). Two readings are both defensible:

- **(a) Once, at that day's own close.** Each day's fee/interest decision
  is made exactly once, when that day closes in the replay, using
  whatever the ledger holds at that instant. Day 2 is never revisited.
  E7 still matters -- it is now part of the ledger -- so it shows up in
  **Day 5's** own close instead (Day 5's closing balance, using everything
  value-dated `<= 5` known by Day 5, is `-155.00`, which is negative, so
  Day 5 gets the fee).
- **(b) Retroactive re-check.** Whenever a backdated entry lands, walk back
  and re-evaluate every day it touches; if a day that was previously
  fine is now negative and has not yet been fee'd, charge it retroactively,
  dated to that earlier day.

**Why (a) was chosen.** Three reasons, in order of how much they matter:

1. **(b) either double-charges or needs an unspecified extra rule.** E7's
   value date (2) is `<= 5` no matter which reading you use, so E7 also
   makes Day 5's own close negative independently of what happens to Day
   2. Under (b), a naive implementation charges Day 2 (retroactively) *and*
   Day 5 (at its normal close) -- two fees from one backdated debit. The
   spec never says anything like "a day already covered by an earlier
   day's retroactive fee is exempt", so avoiding double-charging under (b)
   requires inventing a rule the spec doesn't state. (a) has no such gap:
   each day is assessed exactly once, period, so there is structurally only
   one fee to explain.
2. **"Once per day" reads most naturally as an end-of-day batch step**,
   the way real banking batch jobs work: a day closes, its own fee/interest
   decision is made from what is known at that moment, and it stays closed.
   A backdated adjustment that arrives later is new information for
   *today*, not a time machine back to a prior close.
3. **Append-only cuts both ways.** (b) is not inherently a mutation (a
   retroactive fee would still be a new appended entry, dated to the
   earlier day, not an edit to history) -- so "no mutation" alone doesn't
   settle this. But it does mean that whichever reading is chosen, the
   *other* consequence -- what happens when the debit is later reversed
   (E9) -- has to be equally disciplined: a fee entry, once appended, is
   never un-appended just because its trigger was reversed later. That
   consequence is the same under both readings, and it is why acceptance
   criterion #6 ("after E9, all balances and fees return to their pre-E7
   values") is wrong regardless of which reading you pick -- see
   REJECTED.md.

**What this rejects.** Acceptance criterion #2 ("E7 causes exactly one
overdraft fee assessed, on Day 2") assumes reading (b), and gets the day
wrong even on its own terms (as shown above, (b) would actually produce
*two* fees, not one, dated Day 2 and Day 5). Under this project's reading
(a), E7 causes exactly one fee, and it is dated Day 5. See REJECTED.md #2.

**What's tested.** `AcceptanceCriteriaTest` ("#2 REJECTED...") and
`FailingDesignGapTest` -- the latter is an executable version of reading
(b), kept in the suite failing on purpose so a reviewer who prefers (b) has
a precise, runnable starting point instead of just this prose.

## 2. What balance does an authorization hold check against?

The rule: "approved only if the account's available balance -- ledger
balance minus active holds -- remains at or above zero after the hold is
applied." "Ledger balance" here could mean (a) the account's current
running total (sum of every entry booked so far, at the instant the
authorization is requested), or (b) the closing balance for some specific
day (e.g. the day the authorization is requested, using only entries
value-dated on or before that day).

**Resolution: (a), the current running total.** In every event in this
stream, an entry's value date is never later than its processing day (no
event is "post-dated" into the future), which means, at any point in the
replay, the running total and "closing balance for the current processing
day" are numerically identical anyway (see `Ledger.currentLedgerBalance`'s
javadoc). So the two readings cannot actually be told apart by this
scenario -- but (a) is still the one implemented, because it is the
simpler rule ("the balance right now") and needs no notion of "which day's
closing balance" a hold even belongs to (a hold isn't value-dated at all
in the spec; only bookings are). If a future scenario ever introduced a
future-value-dated entry, (a) and (b) would start disagreeing and this
choice would need revisiting explicitly.

**Why this matters for Auth-B.** By the time `E8` (Auth-B, hold AED 90.00)
is processed on Day 5, `E7` has already posted (it comes first in the
stream), so the running ledger balance is already `-155.00` with no active
holds. `-155.00 - 90.00 = -245.00 < 0`, so Auth-B is **rejected at
creation**. The spec's closing note "Auth-B is never settled inside the
window" is consistent with this (a rejected authorization is trivially
never settled), but it is also consistent with an alternate reading where
Auth-B is approved and simply left open. Given the strict, literal
event-stream order (E7 before E8) and reading (a) above, rejection is what
the numbers say happens; this is called out explicitly because it is the
one place the spec's own scenario prose ("is never settled") could be
read as a hint toward approval instead. Acceptance criterion #5 is phrased
conditionally ("if Auth-B is approved...") and is therefore not falsified
by this outcome -- it is vacuously true -- but it is worth a reviewer's
attention as the closest thing to a trap in the criteria that is not
outright wrong. See REJECTED.md #5.

## 3. E10 is listed after E9 in the spec's prose, but is itself Day-5-tagged

The spec's event list literally reads `... E7, E8, E9, E10` with E9 (a
Day-6-processed reversal) appearing before E10 (a Day-5-processed,
Day-5-value-dated credit on the *other* account). Taken completely
literally ("replayed in this order"), this would mean a Day-5 event
arrives, in true stream order, after a Day-6 event -- which breaks the
otherwise-universal invariant in this stream that processing days are
non-decreasing.

**Resolution:** treated the "Day 5" tag on E10 as authoritative for its
true position in the replay, i.e. `Scenario.events()` places E10
immediately after E8 (both Day 5) and before E9 (Day 6), preserving
non-decreasing processing-day order. Two reasons: first, every other event
in the stream is consistent with monotonic arrival, and a real append-only
log does not receive a Day 5 event after a Day 6 event without that being
worth its own explicit callout, which the spec doesn't give; second, E10
touches a different account (ACC-002) entirely, so its exact interleaving
with ACC-001's E9 has zero effect on any balance in the scenario --
the only thing it could plausibly affect is which day's `close` step "sees"
it first, and ACC-002's own days 1-4 have no entries either way.

**What this constrains:** `ReplayEngine.replay` assumes its input is
already in non-decreasing `processedDay` order and does not defend against
or re-sort out-of-order input -- see REJECTED.md ("approaches abandoned")
for the sorting approach that was tried and dropped.

## 4. "Closing ledger balance" for the daily report: frozen-at-close, or live-recomputed?

The spec asks the report to print, per day, the "closing ledger balance."
But this project distinguishes two different quantities that can
legitimately differ for the same day once backdating is involved (Day 2 is
the concrete example: `250.00` at its own close, `-370.00` recomputed as of
end of Day 5, `250.00` again recomputed after E9 on Day 6):

- **Frozen-at-close** (`DayCloseResult.closingBalanceAtClose`): the value
  used to decide that day's fee/interest, fixed forever once that day
  closes.
- **Live-recomputed** (`Ledger.closingBalanceAsOf(day)`, callable at any
  later point): "what would day D's balance be if I recomputed it right
  now, from everything currently on the ledger."

**Resolution:** the per-day report (`ReportPrinter`) prints the
frozen-at-close value, because that is the value tied to the fee/interest
decision shown right next to it on the same line -- printing a
live-recomputed number there instead would make a positive-interest line
sit next to a since-gone-negative balance and look like a contradiction.
The live-recomputed query is still fully supported and is exactly what
acceptance criterion #1 needs (`AcceptanceCriteriaTest`, "#1 ACCEPTED"
replays only through Day 5 and calls `closingBalanceAsOf(2)` directly to
get `-370.00`). Both notions are real, tested, and named differently in
code specifically so nobody has to guess which one a given number means.

## 5. Does reversing an entry also undo its downstream consequences (fees, interest)?

E9 reverses E7 by appending an equal-and-opposite CREDIT, value-dated to
match E7 (Day 2) -- never touching E7 itself. This makes the *recomputed*
Day 2 balance return to its pre-E7 value (`250.00`). It does **not**
retroactively remove the Day 5 overdraft fee that E7's presence caused at
the time Day 5 closed (see Ambiguity #1): that fee is a separate,
already-appended entry, and nothing in the spec suggests reversals cascade
into un-appending downstream system-generated entries -- doing so would
also violate "no event record is ever mutated or deleted" the moment that
downstream entry needs to disappear rather than just being offset. This is
exactly why acceptance criterion #6 is rejected (REJECTED.md #6): "all
balances and fees return to their pre-E7 values" is false by construction
under an append-only ledger, however the fee timing question in Ambiguity
#1 is resolved.

## 6. Authorizations still open when the six-day window ends

Not exercised by the fixed scenario (Auth-B ends up rejected at creation,
see Ambiguity #2), but designed for anyway: an authorization still `HELD`
when Day 6 closes is marked `OUTSTANDING_AT_WINDOW_END` rather than being
silently left as `HELD` forever or auto-expired/released. The spec gives no
expiry rule and no instruction to release an unsettled hold at the window
boundary, so this project does neither -- it just gives the terminal state
a distinct, honest name so a report reader can tell "still open, window
ended" apart from "open, mid-window" without inventing a business rule
(auto-release, auto-expire-with-fee, etc.) that was never specified.

## 7. Every account is closed every day, even with zero activity

`ReplayEngine.closeDay` runs the fee/interest decision for **every** open
account on **every** day in the window, regardless of whether that account
had any event that day. This is why ACC-002 correctly shows a `0.000`
closing balance (and neither a fee nor an interest line) on Days 1-4, even
though nothing ever mentions ACC-002 before E10 on Day 5 -- "no entries yet"
and "zero balance" are the same closing-balance outcome (neither negative
nor positive), so the absence of activity does not need special-casing
against the two real per-day decisions.

## 8. Settlement for less than the hold amount needs no separate "release excess" event

E5 settles Auth-A (held at AED 200.00) for AED 185.00. The unused AED 15.00
of hold simply evaporates back into available balance the moment the hold
is released as part of settlement -- there is no modelled notion of a
partial, still-open remainder of a hold once any settlement for that
authorization id has been seen. Nothing in the spec suggests authorizations
can be partially settled and partially still held, and the given event
(`E5`) is a single, final settlement for the full hold, so this project
does not build machinery for partial-settlement-with-remaining-hold.

## 9. Fee currency outside AED is left unresolved (a policy gap, not a bug)

Covered in depth in NUMBERS.md; noted here because it is a genuine
ambiguity, not just a constant: the spec defines the overdraft fee only in
AED and gives no FX mechanism, so this project explicitly declines to
guess a BHD (or other) fee amount, throwing rather than fabricating a
number. Unexercised by the fixed scenario since ACC-002 never goes
negative.
