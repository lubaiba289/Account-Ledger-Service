# NUMBERS.md

Every constant the engine uses, where it lives in the code, and why it is
that value and not some other plausible-looking one.

## Overdraft fee: AED 25.00 (`LedgerConstants.overdraftFeeFor`)

Given directly by the spec ("Overdraft fee: AED 25.00"). Nothing to derive.
The interesting decision is scope, not size: the constant is defined **only
for AED**. Calling `overdraftFeeFor(BHD)` throws
`UnsupportedOperationException` with an explicit message, rather than
returning "AED 25.00 converted to BHD at some rate" or "BHD 25.000" (same
digits, wrong currency, no economic meaning) or silently falling back to
zero. None of those is defensible: the spec never gives an FX rate and
never states a BHD-native fee amount, so any number I picked would be a
guess dressed up as a decision. Because ACC-002 (the only BHD account in
the scenario) never actually goes negative, this branch is never exercised
by the scenario itself -- it exists so that *if* a future scenario put a
BHD account into overdraft, the engine fails loudly instead of quietly
charging a made-up amount. See AMBIGUITIES.md.

Why not half it (AED 12.50) or double it (AED 50.00): both are exactly as
plausible-looking and exactly as wrong; the spec states one number and
that's the one implemented.

## Daily interest rate: 0.0004 (`LedgerConstants.DAILY_INTEREST_RATE`)

Given directly ("0.04% per day"). Stored as the `BigDecimal` `"0.0004"`,
not as a `double`, specifically so that `Money.multiply(rate)` never
introduces binary-floating-point error before the final `HALF_UP` rounding
step. Every other arithmetic operation on `Money` (`plus`, `minus`,
`negate`) is exact by construction (both operands are already rounded to
the currency's scale); `multiply` is the ONE place uncontrolled precision
could sneak in, and it is exactly the one place the spec requires rounding
("rounded to their own precision") -- so that is where scale + rounding
mode are applied.

## Rounding mode: HALF_UP

Used everywhere a `Money` value is scaled: at construction (`Money.of`)
and after `multiply`. `HALF_UP` was chosen over `HALF_EVEN` ("banker's
rounding") because the spec gives no reason to expect statistical
rounding-bias cancellation to matter here (this is a fixed six-day, two-
account scenario, not a rounding-heavy high-volume ledger where HALF_EVEN's
bias-cancelling property would earn its complexity), and `HALF_UP` is the
rounding an interviewer reading the numbers by hand would most likely
reach for first. Concretely, this is what turns Day4's raw
`465.00 * 0.0004 = 0.186` into `0.19`, and Day6's raw
`440.00 * 0.0004 = 0.176` into `0.18`. Under `HALF_EVEN` both results would
be identical here (0.186 and 0.176 aren't tie-breaking cases -- the digit
after the rounding point is 6, not 5 -- so this particular scenario
happens not to distinguish the two modes; HALF_UP is still the explicit,
documented choice rather than an accident of the JDK default, because
`BigDecimal.divide`/`setScale` have no implicit default and something had
to be picked).

Why not `DOWN` (truncate) or `UP`: both would still make the daily-accrual
sum-to-capitalized-total property hold (see below), but they would
silently under- or over-state interest by up to one minor unit per account
per day for no stated reason; `HALF_UP` is the closest to "ordinary
rounding" a reviewer expects when no rounding rule is specified.

## Currency scale: AED = 2, BHD = 3 (`CurrencyCode`)

Given directly ("AED is 2 decimal places, BHD is 3"). These mirror the real
ISO 4217 minor-unit counts for AED and BHD (BHD, like KWD and OMR, is a
three-decimal-place dinar), so they are also independently checkable
against the standard, not just against this spec's prose.

## Splitting E10 (BHD 10.000 / 3 instalments): 3.333, 3.333, 3.334

`10.000 / 3 = 3.3333...`, which cannot be represented exactly at 3 decimal
places. `ReplayEngine.splitEvenly` computes the truncated (`RoundingMode
.DOWN`) equal share for every instalment first (`3.333` x 3 = `9.999`),
then distributes the leftover minor units (`0.001`, i.e. exactly one BHD
fils) one at a time starting from the **last** instalment backwards. With
only one leftover unit here, that means instalment 3 becomes `3.334` and
instalments 1-2 stay `3.333`. The three instalments sum to exactly
`10.000` by construction -- not by coincidence, and not by re-rounding the
total afterwards (there is no "total" entry for E10 at all; only the three
instalments are ever booked, so their sum IS the total, definitionally).

Why hand the remainder to the *last* instalment(s) rather than the first:
arbitrary, but arbitrary-and-documented beats arbitrary-and-silent. Either
choice satisfies "must sum exactly"; this project pairs the choice with a
test (`ScenarioReplayTest`, "ACC-002: E10 splits...") that pins it down so
a reviewer knows immediately if the convention ever changes. See
AMBIGUITIES.md for the case where a scenario needs more remainder units
than instalments (not exercised here, since `10.000` only ever produces a
one-minor-unit remainder for 3 instalments, but the loop in
`splitEvenly` handles more than one unit of remainder too, by construction:
it would keep walking backward through the instalment list).

## Interest-accrual rounding vs. capitalized total: must match exactly, by construction

The spec requires "the rounded daily accruals must sum exactly to the
capitalized total." This project does not treat that as a constraint to
verify after the fact and patch up if it fails -- it makes the requirement
structurally impossible to violate: the capitalized total is *defined* as
`sum(the six already-rounded daily accrual Money values)`
(`ReplayEngine.sumInterest`), not as a separately computed
`closingBalance * rate * 6` (or similar) that happens to be rounded
afterwards and compared. There is no separate "total" formula to disagree
with the sum of the parts, so there is no remainder to discard, allocate,
or reconcile in the first place. (Contrast this with the E10 split above,
where the *opposite* problem exists -- a fixed total that must be
subdivided into rounded parts -- which is why that one needs an explicit
remainder-distribution rule and this one does not.)

## Window boundaries: Day 1 through Day 6 (`LedgerConstants.FIRST_DAY` / `LAST_DAY`)

Given directly. Kept as named constants rather than literal `1` and `6`
scattered through the engine so the "six-day window" assumption is visible
and grep-able in exactly one place.

## Zero-balance day: neither fee nor interest

Not stated as a number, but worth recording as a boundary decision: a
closing balance of exactly `0.00` / `0.000` triggers neither the overdraft
fee ("when that day's closing balance is negative" -- zero is not
negative) nor interest ("positive balances only" -- zero is not positive).
No day in this scenario actually lands on exactly zero, so this branch is
implemented but not exercised by the fixed scenario; it is exercised
implicitly by ACC-002's Days 1-4 (balance `0.000` before E10 posts on Day
5), where neither a fee nor an interest line appears in the report.

## Skipping a zero-amount capitalization entry

If an account's total accrued interest across all six days rounded to
`0.00`/`0.000`, this engine would not append an `INTEREST_CAPITALIZATION`
entry at all (`ReplayEngine.closeDay`, the `capitalized.isPositive()`
check), rather than appending a same-currency zero-amount credit. An
append-only ledger gains nothing from a no-op entry, and "capitalize" reads
naturally as "add the accrued interest", which is vacuous at zero. Not
exercised by this scenario (both accounts end with strictly positive
capitalized interest), but recorded here because it is a real branch, not
a gap.
