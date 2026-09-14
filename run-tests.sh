#!/usr/bin/env bash
# Builds and runs the full test suite (see AllTests.java). Exit code is 0
# iff every REQUIRED assertion passed -- the one intentionally-failing test
# (FailingDesignGapTest) is reported separately and does not affect it.
set -euo pipefail
cd "$(dirname "$0")"
./build.sh
java -cp out com.account.ledger.AllTests
