#!/usr/bin/env bash
# Builds and runs the fixed E1..E10 scenario, printing the per-day report:
# closing ledger balance, fee assessments, authorization states, and errors.
set -euo pipefail
cd "$(dirname "$0")"
./build.sh
java -cp out com.urbio.ledger.Main
