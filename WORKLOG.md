# WORKLOG.md

Real, timestamped account of the build (Asia/Dubai, UTC+4). Each entry
corresponds to one commit; commit hashes are left for `git log` rather than
copied here so this file never goes stale relative to history.

**2026-09-12 16:41** -- Started from the spec. Before writing any code,
worked the whole six-day scenario by hand on paper first: every event's
effect on both accounts' running balances, every day's fee/interest
decision, the final capitalized totals. This is the only way to know
later whether a bug is in the code or in my understanding of the rules --
and it immediately surfaced the central ambiguity of the whole exercise
(does a backdated entry reopen an already-closed day's fee decision?)
before a single class existed. Decided the domain model first: `Money`
(BigDecimal-backed, currency-scoped, rounds at construction),
`CurrencyCode` (AED=2dp, BHD=3dp), `Account`, `EntryType`, `LedgerEntry`.
Chose immutable value types throughout -- the spec's "append-only, never
mutated" requirement should be enforced by the type system where possible,
not just by convention.

**2026-09-12 16:54** -- `Ledger` (append-only entry list + authorization
table, `closingBalanceAsOf` computed on demand rather than cached -- this
is what makes backdating "just work" instead of needing special-case
invalidation logic), `Authorization` / `AuthorizationStatus` (deliberately
NOT append-only -- see REJECTED.md for why that's a considered choice, not
an oversight), and the five event types (`CreditEvent`, `DebitEvent`,
`AuthorizationEvent`, `SettlementEvent`, `ReversalEvent`). Spent real time
on `CreditEvent`'s `installments` field for E10 before writing
`ReplayEngine` -- decided the split-into-N-equal-shares logic belongs in
the engine (where rounding/remainder rules live), not in the event itself.

**2026-09-12 17:05** -- `ReplayEngine`: event application (credit, debit,
authorization approve/reject, settlement accept/reject-unknown-auth,
reversal-by-appending-an-opposite-entry) plus the daily close step
(fee-or-interest-never-both, capitalization on the last day only).
Tried sorting the incoming event list defensively before replay; ripped it
out almost immediately once I realized it would silently paper over the
exact E9/E10 ordering question I needed to reason about explicitly instead
(see AMBIGUITIES.md #3, REJECTED.md). Wrote `Scenario.java` (the literal
E1-E10 stream) and `Main`/`ReportPrinter` next so I could actually *see*
the six days rather than reason about them only through assertions.
First full run's numbers matched my paper calculation exactly on the first
try for every day except one: I'd initially forgotten that `E10`'s
instalments needed a remainder-distribution rule at all (my first draft
of `splitEvenly` just truncated every share, producing `3.333 + 3.333 +
3.333 = 9.999`, silently one BHD fils short of the credited 10.000).
Fixed by adding the backward remainder-distribution loop before moving on.

**2026-09-12 17:16** -- Test harness (`MiniTest` -- ~70 lines, zero
dependencies, see NUMBERS.md/README for why no JUnit) and the three test
classes. Writing `AcceptanceCriteriaTest` is what forced the fee-timing
ambiguity (#1 in AMBIGUITIES.md) all the way into the open: criterion #2
claims a fee "on Day 2", and only while writing the test to check that did
I fully trace through that the literal retroactive reading produces *two*
fees, not one -- which is what tipped the decision toward the
once-per-day-at-close model. Wrote `FailingDesignGapTest` as the
executable record of the rejected alternative. Hit one real test bug
while writing `ScenarioReplayTest`: a test asserting the ledger balance was
`-155.00` "before Day5's fee" failed, showing `-180.00` instead -- not a
bug in `ReplayEngine`, but in the test: `replay(events, 5)` closes Day 5
(fee included) before returning, so the *running* balance at that point is
already post-fee. Fixed by reading the pre-fee figure off
`DayCloseResult.closingBalanceAtClose()` (captured before the fee entry is
appended) instead of off the live running balance, and added an explicit
assertion for the post-fee running balance right next to it so both
numbers are pinned down, not just the one that happened to be wrong.

**2026-09-12 17:27** -- Documentation pass: NUMBERS.md, AMBIGUITIES.md,
REJECTED.md, this file, and the final README. Went back through every
constant in `LedgerConstants` and `Money` and asked "why this value and
not some other one" for real, which caught that the overdraft-fee policy
had no defined behavior for a non-AED account going negative -- added the
explicit `UnsupportedOperationException` in `LedgerConstants
.overdraftFeeFor` and documented it as a deliberate gap rather than
silently letting a BHD account get charged "25.00" with no unit meaning.

**2026-09-12 17:33** -- Final review: re-ran `run-scenario.sh` and
`run-tests.sh` end to end, cross-checked every printed number in the Day
1-6 report against the hand-worked calculation from 16:41, confirmed all
required-test assertions pass and the one intentionally-failing test still
fails for the documented reason. No further code changes; committing docs
as final.
