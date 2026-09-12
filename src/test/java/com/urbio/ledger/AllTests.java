package com.urbio.ledger;

import static com.urbio.ledger.testing.MiniTest.summarizeAndExit;

/** Runs every test suite in the project. See README.md for how to build and
 * run this. Exit code 0 means every REQUIRED assertion passed (the one
 * intentionally-failing test is expected to fail and does not affect the
 * exit code -- see FailingDesignGapTest). */
public final class AllTests {
    public static void main(String[] args) {
        ScenarioReplayTest.run();
        AcceptanceCriteriaTest.run();
        FailingDesignGapTest.run();

        int exitCode = summarizeAndExit();
        System.exit(exitCode);
    }
}
