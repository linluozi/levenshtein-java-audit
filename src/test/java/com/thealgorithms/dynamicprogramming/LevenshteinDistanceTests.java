package com.thealgorithms.dynamicprogramming;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.ToIntBiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Differential tests plus structural assertions for the trimming and shorter-first dispatch. */
class LevenshteinDistanceTests {

    private static final int REPEAT_LENGTH = 2048;
    private static final int LONG_INPUT = 80_000;

    @ParameterizedTest
    @MethodSource("testCases")
    void testLevenshteinDistance(final int expected, final String str1, final String str2, final ToIntBiFunction<String, String> dist) {
        assertEquals(expected, dist.applyAsInt(str1, str2));
        assertEquals(expected, dist.applyAsInt(str2, str1));
        assertEquals(0, dist.applyAsInt(str1, str1));
        assertEquals(0, dist.applyAsInt(str2, str2));
    }

    private static Stream<Arguments> testCases() {
        final Object[][] testData = {
            {0, "", ""},
            {0, "Hello, World!", "Hello, World!"},
            {4, "", "Rust"},
            {3, "horse", "ros"},
            {6, "tan", "elephant"},
            {8, "execute", "intention"},
            {1, "a", "b"},
            {1, "a", "aa"},
            {1, "a", ""},
            {1, "a", "ab"},
            {1, "a", "ba"},
            {2, "a", "bc"},
            {2, "a", "cb"},
            {1, "prefix-X-suffix", "prefix-Y-suffix"},
            {0, "aaa", "aaa"},
            {1, "aaa", "aa"},
            {2, "abab", "ab"},
            {3, "abcabc", "abc"},
        };

        final List<ToIntBiFunction<String, String>> methods = List.of(LevenshteinDistance::naiveLevenshteinDistance, LevenshteinDistance::optimizedLevenshteinDistance);

        return Stream.of(testData).flatMap(input -> methods.stream().map(method -> Arguments.of(input[0], input[1], input[2], method)));
    }

    @Test
    void testExhaustiveShortStrings() {
        final List<String> strings = new ArrayList<>();
        strings.add("");
        buildStrings(strings, "", 5, new char[] {'a', 'b', 'c'});

        for (final String first : strings) {
            for (final String second : strings) {
                final int expected = LevenshteinDistance.naiveLevenshteinDistance(first, second);
                assertEquals(expected, LevenshteinDistance.optimizedLevenshteinDistance(first, second), "strings: " + first + " / " + second);
                assertEquals(expected, LevenshteinDistance.optimizedLevenshteinDistance(second, first), "reversed: " + second + " / " + first);
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

    @Test
    void testRandomizedDifferentialCases() {
        final Random random = new Random(20261017L);
        for (int sample = 0; sample < 5000; sample++) {
            final String first = randomString(random, random.nextInt(65));
            final String second = randomString(random, random.nextInt(65));
            assertEquals(LevenshteinDistance.naiveLevenshteinDistance(first, second), LevenshteinDistance.optimizedLevenshteinDistance(first, second), "sample " + sample);
        }
    }

    private static String randomString(final Random random, final int length) {
        final StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append((char) ('a' + random.nextInt(5)));
        }
        return result.toString();
    }

    @Test
    void testLongAsymmetricInputs() {
        final String longString = "a".repeat(LONG_INPUT);
        final String shortString = "bbbbbbbbbbbb";
        assertEquals(LONG_INPUT, LevenshteinDistance.optimizedLevenshteinDistance(longString, shortString));
        assertEquals(LONG_INPUT, LevenshteinDistance.optimizedLevenshteinDistance(shortString, longString));
    }

    @Test
    void testRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> LevenshteinDistance.optimizedLevenshteinDistance(null, "abc"));
        assertThrows(NullPointerException.class, () -> LevenshteinDistance.optimizedLevenshteinDistance("abc", null));
    }

    // ---- structural assertions: a behavioural test alone cannot prove the optimization is still there ----

    @Test
    void testTrimsIdenticalEdgesBeforeTheGrid() {
        final String prefix = "p".repeat(REPEAT_LENGTH);
        final String suffix = "s".repeat(REPEAT_LENGTH);
        final String first = prefix + "X" + suffix;
        final String second = prefix + "Y" + suffix;

        assertEquals(1, LevenshteinDistance.optimizedLevenshteinDistance(first, second));
        assertArrayEquals(new int[] {REPEAT_LENGTH, REPEAT_LENGTH + 1, REPEAT_LENGTH, REPEAT_LENGTH + 1}, LevenshteinDistance.unmatchedRanges(first, second));
    }

    @Test
    void testUnmatchedRangesNeverOverlap() {
        // The prefix scan consumes as much as it can, so "aaa" vs "aa" leaves a single unmatched "a".
        assertArrayEquals(new int[] {2, 3, 2, 2}, LevenshteinDistance.unmatchedRanges("aaa", "aa"));
        assertArrayEquals(new int[] {2, 4, 2, 2}, LevenshteinDistance.unmatchedRanges("abab", "ab"));
        assertArrayEquals(new int[] {3, 6, 3, 3}, LevenshteinDistance.unmatchedRanges("abcabc", "abc"));
        assertArrayEquals(new int[] {4, 4, 4, 4}, LevenshteinDistance.unmatchedRanges("same", "same"));
        assertArrayEquals(new int[] {0, 1, 0, 1}, LevenshteinDistance.unmatchedRanges("Xabc", "Yabc"));
        assertArrayEquals(new int[] {3, 4, 3, 4}, LevenshteinDistance.unmatchedRanges("abcX", "abcY"));
    }

    @Test
    void testRowFollowsTheShorterArgument() {
        // 8_000 x 24: the row must be 25 ints whichever order the arguments arrive in.
        assertEquals(25, LevenshteinDistance.rollingRowLength("a".repeat(8000), "b".repeat(24)));
        assertEquals(25, LevenshteinDistance.rollingRowLength("b".repeat(24), "a".repeat(8000)));
    }
}
