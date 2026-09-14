package com.account.ledger.testing;

import java.util.ArrayList;
import java.util.List;

/**
 * A deliberately tiny, dependency-free test harness. This project has no
 * build tool and no test framework dependency by design (see NUMBERS.md /
 * README -- "why not JUnit"): the whole point of an in-memory core exercise
 * is that a reviewer can clone the repo and run it with nothing but a JDK.
 *
 * Usage: subclass nothing, just call {@link #test(String, Runnable)} for a
 * test expected to pass, or {@link #expectedFailure(String, String, Runnable)}
 * for the one intentionally-failing test the spec asks for. Call
 * {@link #summarizeAndExit()} at the end of your suite's main().
 */
public final class MiniTest {

    private static final List<String> passed = new ArrayList<>();
    private static final List<String> failed = new ArrayList<>();
    private static final List<String[]> expectedFailures = new ArrayList<>(); // {name, revealed}

    private MiniTest() {}

    public static void test(String name, Runnable body) {
        try {
            body.run();
            passed.add(name);
            System.out.println("  [PASS] " + name);
        } catch (Throwable t) {
            failed.add(name + " -- " + t.getMessage());
            System.out.println("  [FAIL] " + name + " -- " + t);
        }
    }

    /**
     * Runs a test that is EXPECTED to fail against the current design, and
     * records why. If it unexpectedly passes, that is itself reported as a
     * failure (the documented gap would no longer be true).
     */
    public static void expectedFailure(String name, String whatItReveals, Runnable body) {
        try {
            body.run();
            failed.add(name + " -- expected this to fail (it did not): " + whatItReveals);
            System.out.println("  [UNEXPECTED PASS] " + name);
        } catch (Throwable t) {
            expectedFailures.add(new String[]{name, whatItReveals, t.toString()});
            System.out.println("  [EXPECTED FAIL] " + name);
            System.out.println("                  reveals: " + whatItReveals);
            System.out.println("                  threw:   " + t);
        }
    }

    public static void assertEquals(Object expected, Object actual, String context) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(context + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void assertTrue(boolean condition, String context) {
        if (!condition) {
            throw new AssertionError(context + ": expected true");
        }
    }

    public static void assertNull(Object value, String context) {
        if (value != null) {
            throw new AssertionError(context + ": expected null but was <" + value + ">");
        }
    }

    public static int summarizeAndExit() {
        System.out.println();
        System.out.println("Passed: " + passed.size() + ", Failed: " + failed.size()
                + ", Expected-failures: " + expectedFailures.size());
        if (!failed.isEmpty()) {
            System.out.println("FAILURES:");
            for (String f : failed) {
                System.out.println("  - " + f);
            }
        }
        return failed.isEmpty() ? 0 : 1;
    }
}
