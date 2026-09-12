#!/usr/bin/env bash
# Compiles the whole project (main + tests) with plain javac. No build tool,
# no external dependencies -- see NUMBERS.md for why.
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out
mkdir -p out
javac -d out $(find src/main src/test -name "*.java")
echo "Build OK -> ./out"
