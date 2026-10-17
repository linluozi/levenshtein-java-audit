package com.thealgorithms.dynamicprogramming;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Dependency-free version of {@link LevenshteinDistanceTests}, for anyone who
 * wants to run the checks without Maven or JUnit.
 *
 * <p>It asserts the same things the JUnit class does: differential correctness
 * against an independent reference implementation, and the structural
 * assertions that prove the trimming and the shorter-first dispatch are still
 * present.
 *
 * <p>Run with {@code sh scripts/test.sh}.
 */
public final class LevenshteinDistanceProbe {
    private static int checks;

    private LevenshteinDistanceProbe() { }

    public static void main(final String[] args) {
        knownCases();
        exhaustiveShortStrings();
        randomisedDifferentialCases();
        longAsymmetricInputs();
        rejectsNullArguments();
        trimsIdenticalEdges();
        unmatchedRangesNeverOverlap();
        rowFollowsTheShorterArgument();
        System.out.println("PASS: " + checks + " checks");
    }

    private static void knownCases() {
        final Object[][] data = {
            {"", "", 0}, {"", "abc", 3}, {"abc", "", 3}, {"kitten", "sitting", 3},
            {"flaw", "lawn", 2}, {"same", "same", 0}, {"prefix-X-suffix", "prefix-Y-suffix", 1},
            {"a", "ba", 1}, {"ba", "a", 1}, {"\uD83D\uDE42", "\uD83D\uDE43", 1},
            {"aaa", "aa", 1}, {"abab", "ab", 2}, {"abcabc", "abc", 3}, {"a", "aa", 1},
        };
        for (final Object[] row : data) {
            final String first = (String) row[0];
            final String second = (String) row[1];
            final int expected = (Integer) row[2];
            check(LevenshteinDistance.optimizedLevenshteinDistance(first, second) == expected,
                "known case " + quote(first) + " / " + quote(second) + " expected " + expected);
            check(LevenshteinDistance.optimizedLevenshteinDistance(second, first) == expected,
                "known case reversed " + quote(second) + " / " + quote(first));
        }
    }

    private static void exhaustiveShortStrings() {
        final List<String> strings = new ArrayList<>();
        strings.add("");
        buildStrings(strings, "", 5, new char[] {'a', 'b', 'c'});
        for (final String first : strings) {
            for (final String second : strings) {
                final int expected = reference(first, second);
                check(LevenshteinDistance.optimizedLevenshteinDistance(first, second) == expected,
                    "exhaustive " + quote(first) + " / " + quote(second));
                check(LevenshteinDistance.optimizedLevenshteinDistance(second, first) == expected,
                    "exhaustive reversed " + quote(second) + " / " + quote(first));
            }
        }
    }

    private static void buildStrings(final List<String> out, final String prefix, final int remaining, final char[] alphabet) {
        if (!prefix.isEmpty()) {
            out.add(prefix);
        }
        if (remaining == 0) {
            return;
        }
        for (final char character : alphabet) {
            buildStrings(out, prefix + character, remaining - 1, alphabet);
        }
    }

    private static void randomisedDifferentialCases() {
        final Random random = new Random(20261017L);
        for (int sample = 0; sample < 5000; sample++) {
            final String first = randomString(random, random.nextInt(65));
            final String second = randomString(random, random.nextInt(65));
            check(LevenshteinDistance.optimizedLevenshteinDistance(first, second) == reference(first, second),
                "random sample " + sample);
        }
    }

    private static String randomString(final Random random, final int length) {
        final StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append((char) ('a' + random.nextInt(5)));
        }
        return result.toString();
    }

    private static void longAsymmetricInputs() {
        final String longString = "a".repeat(80_000);
        final String shortString = "bbbbbbbbbbbb";
        check(LevenshteinDistance.optimizedLevenshteinDistance(longString, shortString) == 80_000, "80k x 12");
        check(LevenshteinDistance.optimizedLevenshteinDistance(shortString, longString) == 80_000, "12 x 80k");
    }

    private static void rejectsNullArguments() {
        check(throwsNpe(null, "abc"), "null first argument");
        check(throwsNpe("abc", null), "null second argument");
    }

    private static boolean throwsNpe(final String first, final String second) {
        try {
            LevenshteinDistance.optimizedLevenshteinDistance(first, second);
            return false;
        } catch (final NullPointerException expected) {
            return true;
        }
    }

    // ---- structural: a behavioural check alone cannot tell whether the optimisation is still there ----

    private static void trimsIdenticalEdges() {
        final String prefix = "p".repeat(2048);
        final String suffix = "s".repeat(2048);
        final String first = prefix + "X" + suffix;
        final String second = prefix + "Y" + suffix;
        check(LevenshteinDistance.optimizedLevenshteinDistance(first, second) == 1, "shared-edge distance");
        final int[] ranges = LevenshteinDistance.unmatchedRanges(first, second);
        check(Arrays.equals(ranges, new int[] {2048, 2049, 2048, 2049}),
            "shared edges are trimmed, got " + Arrays.toString(ranges));
    }

    private static void unmatchedRangesNeverOverlap() {
        check(Arrays.equals(LevenshteinDistance.unmatchedRanges("aaa", "aa"), new int[] {2, 3, 2, 2}), "ranges aaa/aa");
        check(Arrays.equals(LevenshteinDistance.unmatchedRanges("abab", "ab"), new int[] {2, 4, 2, 2}), "ranges abab/ab");
        check(Arrays.equals(LevenshteinDistance.unmatchedRanges("abcabc", "abc"), new int[] {3, 6, 3, 3}), "ranges abcabc/abc");
        check(Arrays.equals(LevenshteinDistance.unmatchedRanges("same", "same"), new int[] {4, 4, 4, 4}), "ranges same/same");
        check(Arrays.equals(LevenshteinDistance.unmatchedRanges("Xabc", "Yabc"), new int[] {0, 1, 0, 1}), "ranges Xabc/Yabc");
    }

    private static void rowFollowsTheShorterArgument() {
        check(LevenshteinDistance.rollingRowLength("a".repeat(8000), "b".repeat(24)) == 25, "row length, long first");
        check(LevenshteinDistance.rollingRowLength("b".repeat(24), "a".repeat(8000)) == 25, "row length, short first");
    }

    // ---- helpers ----

    /** Independent two-row implementation, used as the reference. */
    private static int reference(final String first, final String second) {
        final int n = first.length();
        final int m = second.length();
        int[] previous = new int[m + 1];
        int[] current = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            current[0] = i;
            for (int j = 1; j <= m; j++) {
                final int cost = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(previous[j] + 1, Math.min(current[j - 1] + 1, previous[j - 1] + cost));
            }
            final int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[m];
    }

    private static String quote(final String value) {
        return "'" + value + "'";
    }

    private static void check(final boolean condition, final String label) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + label);
        }
    }
}
