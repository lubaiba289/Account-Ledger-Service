# REJECTED.md

## Acceptance criteria: verdicts

Four of the eight stated acceptance criteria are wrong. Each verdict below
is backed by a test in `AcceptanceCriteriaTest` (or `ScenarioReplayTest`)
that asserts what actually happens; full reasoning for the underlying
design choices lives in AMBIGUITIES.md and is only summarized here.

### #1 -- ACCEPTED
> "The Day 2 closing ledger balance, evaluated at end of Day 5 and before
> any fee is assessed, is AED −370.00."

Correct. `E1 + E2 + E7 = 1200.00 - 950.00 - 620.00 = -370.00`, and this is
exactly what `Ledger.closingBalanceAsOf(2)` returns when queried right
after replaying through Day 5 (before E9's Day 6 reversal). See
AMBIGUITIES.md #4 for why this "recomputed as of a later point" query is a
distinct, equally real notion from the frozen fee-triggering balance used
in #2 below -- both are implemented, and this criterion is asking for the
former.

### #2 -- REJECTED
> "E7 causes exactly one overdraft fee assessed, on Day 2."

Wrong on the day, right on the count. E7 does cause exactly one overdraft
fee -- but it is dated **Day 5** (E7's own processing day), not Day 2. Day
2 already closed positive (`250.00`) using only E1/E2, before E7 (posted
Day 5, value-dated Day 2) ever existed, and this engine does not
retroactively reopen an already-closed day's fee decision. See
AMBIGUITIES.md #1 for the full argument, including why a literal reading
that *does* retroactively charge Day 2 would actually produce **two** fees
(Day 2 and Day 5), not one -- so this criterion is not a viable target to
"just fix the date on."

### #3 -- ACCEPTED
> "The Day 4 settlement of Auth-A must be accepted."

Correct. Auth-A exists (opened by E3), is still `HELD` when E5 arrives, and
nothing in the spec requires a settlement amount to equal the original
hold amount (settling for less than the hold, AED 185.00 vs. 200.00, is
completely ordinary in card-authorization systems: the hold is an
estimate, the settlement is the real, final amount). E5 is accepted, Auth-A
becomes `SETTLED`, and the unused AED 15.00 of hold simply stops being
held (see AMBIGUITIES.md #8).

### #4 -- ACCEPTED
> "Any settlement referencing an authorization ID not present in the
> ledger must be rejected and the funds must not leave the account."

Correct, and this is exactly what E6 (settling the never-opened `Auth-Z`)
demonstrates: rejected, recorded as an error, zero effect on ACC-001's
balance.

### #5 -- ACCEPTED (but only vacuously true -- read this one carefully)
> "If Auth-B is approved, its hold reduces available balance but not
> ledger balance."

The general rule stated is correct and is exactly how this engine treats
every `HELD` authorization (verified directly against Auth-A while it was
still held, in `AcceptanceCriteriaTest` #5). But in this stream, Auth-B is
never actually approved -- see AMBIGUITIES.md #2: by the time E8 is
processed, E7 has already pushed the running ledger balance to `-155.00`,
and `-155.00 - 90.00 = -245.00 < 0`, so Auth-B is rejected at creation. The
criterion is phrased as a conditional ("if... approved"), so it is not
falsified by this outcome; it is simply never triggered. Flagged here
rather than silently marked ACCEPTED because a reviewer expecting Auth-B
to be approved (the "never settled inside the window" phrasing in the spec
could read that way) should know this engine's Auth-B outcome is REJECTED,
not HELD-forever, and that this is the one criterion where the gap between
"technically true" and "probably what was meant" is narrowest.

### #6 -- REJECTED
> "After E9, all balances and fees return to their pre-E7 values."

Wrong. E9 reverses E7's *balance* effect (Day 2's recomputed balance does
return to `250.00`), but the Day 5 overdraft fee that E7's presence caused
is a separate, already-appended ledger entry (`FEE-D5-ACC-001`,
`AED -25.00`) and is never un-appended by a later reversal of its trigger
-- see AMBIGUITIES.md #5. Concretely: pre-E7, Day 5 (recomputed) would
have been `465.00`; post-E9, Day 5 recomputed is `440.00`, not `465.00` --
a permanent AED 25.00 gap. "The ledger is append-only. No event record is
ever mutated or deleted" is incompatible with a fee retroactively
vanishing, no matter how its trigger is resolved later.

### #7 -- REJECTED
> "The three BHD instalments in E10 must each be BHD 3.334."

Arithmetically impossible as stated: `3.334 x 3 = 10.002`, not `10.000`.
`10.000 / 3` cannot be represented exactly at 3 decimal places, so the
three instalments cannot all be equal at BHD's own precision while still
summing exactly to the credited total. This project books `3.333, 3.333,
3.334` (see NUMBERS.md for the remainder-allocation rule), which sums
exactly to `10.000`; the criterion's proposed `3.334 x 3` does not sum to
the credited amount and is rejected on that basis alone.

### #8 -- REJECTED
> "If the rounded daily interest accruals do not sum to the capitalized
> total, the remainder is discarded."

Contradicts the spec's own immediately-preceding sentence ("The rounded
daily accruals must sum exactly to the capitalized total") and, separately,
is simply the wrong fix even if that sentence didn't exist: silently
discarding a rounding remainder means money that was accrued (and shown to
the account holder day-by-day) never actually reaches the account, which
is a real economic loss with no stated justification. This project makes
the premise of the criterion impossible rather than choosing a remainder
policy: the capitalized total is *defined* as the sum of the six already-
rounded daily figures (see NUMBERS.md, "Interest-accrual rounding vs.
capitalized total"), so there is never a difference to discard in the
first place.

## Approaches tried and abandoned mid-build

**Retroactive fee re-assessment (reading (b) in AMBIGUITIES.md #1).** Built
far enough to see the double-charging problem (E7 would independently push
both the recomputed Day 2 and the actual Day 5 closing balance negative),
then abandoned in favor of the once-per-day-at-close model. Kept alive on
purpose as the project's one required failing test
(`FailingDesignGapTest`) instead of deleting the branch entirely, so the
fork is a runnable artifact, not just a paragraph someone has to take on
faith.

**Silently sorting/normalizing the event stream before replay.** Early on,
`ReplayEngine.replay` re-sorted its input by `processedDay` (a stable sort)
before applying anything, to be "defensive" against out-of-order input.
Dropped it: silently reordering a stream that arrived out of order hides
exactly the kind of upstream bug (or, per AMBIGUITIES.md #3, the exact
E9/E10 ordering question) this exercise is about surfacing, not masking.
The engine now documents non-decreasing `processedDay` order as a
precondition of `replay(...)` and leaves detecting a violation of that
precondition as the caller's problem -- `Scenario.events()` is the only
caller in this repository, and it satisfies the precondition by
construction.

**A general largest-remainder-method allocator for `splitEvenly`.**
Considered building a fully general largest-remainder allocator (sort
shares by their truncated fractional remainder, hand out leftover minor
units to the largest remainders first) so it would behave sensibly for any
number of instalments and any remainder size. Abandoned in favor of the
simpler "truncate everything, then hand out leftover minor units to the
last instalment(s) first" rule actually implemented, because: (a) with
equal shares, every instalment's remainder is identical, so "largest
remainder first" has no real tie-breaking meaning over "last instalment(s)
first" -- both are equally arbitrary, and the simpler one is easier for a
reviewer to audit against NUMBERS.md; (b) YAGNI -- the one case this repo
actually exercises (10.000 / 3) needs only a single-minor-unit remainder,
and the simpler rule already generalizes to handle a remainder larger than
one minor unit (verified by the loop in `splitEvenly`, though not by a
test with a scenario that needs it).

**`double` for money.** Never actually written, but worth recording as
rejected before the first line of `Money` was: floating-point binary
representation cannot exactly hold values like `0.10` or `3.333`, which
would silently violate "amounts stored and rounded to their own precision"
the moment two such values were added. `BigDecimal`, scaled per-currency at
construction, was the only real candidate.

**JUnit + Maven/Gradle.** Considered, not adopted. See README.md and
NUMBERS.md: this project has zero build-tool and zero test-framework
dependency by design, so it builds and runs with nothing but a JDK on the
PATH.

**Making `Authorization` append-only like `LedgerEntry`.** Considered
modelling authorization status changes as a series of immutable
`Authorization` snapshots appended to a per-id list (mirroring the
ledger's append-only discipline exactly), instead of one mutable
`Authorization` object whose status field changes in place. Abandoned:
the spec's append-only guarantee is explicitly scoped to "event record"
(actual money movements), and a hold is a still-open intent, not a
booking -- the actual settlement or fee it produces IS append-only (a real
`LedgerEntry`); the hold-tracking object recording *that a settlement
happened* does not need the same discipline applied a second time, and
doing so would have added a parallel history mechanism for no behavioral
difference anyone could observe from the report or the tests.
