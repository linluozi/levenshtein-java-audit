package com.thealgorithms.dynamicprogramming;

import java.util.stream.IntStream;

/**
 * Provides functions to calculate the Levenshtein distance between two strings.
 *
 * The Levenshtein distance is a measure of the similarity between two strings by calculating the minimum number of single-character
 * edits (insertions, deletions, or substitutions) required to change one string into the other.
 */
public final class LevenshteinDistance {
    private LevenshteinDistance() {
    }

    /**
     * Calculates the Levenshtein distance between two strings using a naive dynamic programming approach.
     *
     * @param string1 The first string.
     * @param string2 The second string.
     * @return The Levenshtein distance between the two input strings.
     *
     * Time complexity: O(nm),
     * Space complexity: O(nm),
     *
     * where n and m are lengths of `string1` and `string2`.
     */
    public static int naiveLevenshteinDistance(final String string1, final String string2) {
        int[][] distanceMatrix = IntStream.rangeClosed(0, string1.length()).mapToObj(i -> IntStream.rangeClosed(0, string2.length()).map(j -> (i == 0) ? j : (j == 0) ? i : 0).toArray()).toArray(int[][] ::new);

        IntStream.range(1, string1.length() + 1).forEach(i -> IntStream.range(1, string2.length() + 1).forEach(j -> {
            final int cost = (string1.charAt(i - 1) == string2.charAt(j - 1)) ? 0 : 1;
            distanceMatrix[i][j] = Math.min(distanceMatrix[i - 1][j - 1] + cost, Math.min(distanceMatrix[i][j - 1] + 1, distanceMatrix[i - 1][j] + 1));
        }));

        return distanceMatrix[string1.length()][string2.length()];
    }

    /**
     * Calculates the Levenshtein distance between two strings using an optimized dynamic programming approach.
     *
     * This edit distance is defined as 1 point per insertion, substitution, or deletion required to make the strings equal.
     *
     * <p>Identical leading and trailing characters contribute nothing to the distance, so they are trimmed before the
     * dynamic-programming stage. Only the unmatched middle regions enter the grid, and the shorter of the two middle
     * regions determines the rolling row, so the auxiliary space is O(min(n, m)) regardless of argument order.
     *
     * @param string1 The first string.
     * @param string2 The second string.
     * @return The Levenshtein distance between the two input strings.
     *
     * Time complexity: O(nm) worst case,
     * Space complexity: O(min(n, m)) auxiliary,
     *
     * where n and m are lengths of `string1` and `string2`.
     */
    public static int optimizedLevenshteinDistance(final String string1, final String string2) {
        if (string1.isEmpty()) {
            return string2.length();
        }
        if (string2.isEmpty()) {
            return string1.length();
        }

        final int[] ranges = unmatchedRanges(string1, string2);
        final String middle1 = string1.substring(ranges[0], ranges[1]);
        final String middle2 = string2.substring(ranges[2], ranges[3]);
        if (middle1.isEmpty()) {
            return middle2.length();
        }
        if (middle2.isEmpty()) {
            return middle1.length();
        }

        return rollingDistance(middle1, middle2);
    }

    /**
     * Rolls a single row over the edit-distance grid, using the shorter argument as the row dimension.
     *
     * <p>Visible for testing so that the allocated row length and the number of visited grid cells can be asserted
     * directly: a purely behavioural test cannot distinguish a working optimization from one that silently stopped
     * trimming, because both return the same distance.
     *
     * @param string1 One of the two strings.
     * @param string2 The other string.
     * @return The Levenshtein distance between the two input strings.
     */
    static int rollingDistance(final String string1, final String string2) {
        final String shorter = rowString(string1, string2);
        final String longer = shorter == string1 ? string2 : string1;
        final int shortLength = shorter.length();
        final int longLength = longer.length();

        int[] previousDistance = allocateRow(shortLength);

        for (int j = 1; j <= longLength; j++) {
            int previousDiagonal = previousDistance[0];
            previousDistance[0] = j;
            final char longerChar = longer.charAt(j - 1);
            for (int i = 1; i <= shortLength; i++) {
                final int deletionCost = previousDistance[i] + 1;
                final int insertionCost = previousDistance[i - 1] + 1;
                final int substitutionCost = (shorter.charAt(i - 1) == longerChar) ? previousDiagonal : previousDiagonal + 1;
                previousDiagonal = previousDistance[i];
                previousDistance[i] = Math.min(deletionCost, Math.min(insertionCost, substitutionCost));
            }
        }

        return previousDistance[shortLength];
    }

    /**
     * Returns the argument that should become the rolling row, i.e. the shorter of the two.
     *
     * <p>Split out so that the structural test can assert which string drives the row width.
     *
     * @param string1 One of the two strings.
     * @param string2 The other string.
     * @return {@code string1} when it is no longer than {@code string2}, otherwise {@code string2}.
     */
    static String rowString(final String string1, final String string2) {
        return string1.length() <= string2.length() ? string1 : string2;
    }

    /**
     * Returns the number of ints the rolling row allocates for the given pair.
     *
     * <p>Exposed so that the structural test can assert the memory bound directly, which a purely behavioural
     * assertion cannot observe.
     *
     * @param string1 One of the two strings.
     * @param string2 The other string.
     * @return The row length in ints.
     */
    static int rollingRowLength(final String string1, final String string2) {
        return rowString(string1, string2).length() + 1;
    }

    /**
     * Allocates the rolling row and fills it with {@code 0..shortLength}.
     *
     * @param shortLength The width of the row minus one.
     * @return The initialized row.
     */
    private static int[] allocateRow(final int shortLength) {
        return IntStream.rangeClosed(0, shortLength).toArray();
    }

    /**
     * Returns the unmatched middle region of the two strings as {@code {start1, end1, start2, end2}}.
     *
     * <p>The trailing scan stops before the two ranges could overlap the already-trimmed prefix, so
     * {@code end >= start} always holds and {@code substring} never throws.
     *
     * @param string1 The first string.
     * @param string2 The second string.
     * @return The four half-open range bounds.
     */
    static int[] unmatchedRanges(final String string1, final String string2) {
        final int length1 = string1.length();
        final int length2 = string2.length();

        int start1 = 0;
        int start2 = 0;
        while (start1 < length1 && start2 < length2 && string1.charAt(start1) == string2.charAt(start2)) {
            start1++;
            start2++;
        }

        int end1 = length1;
        int end2 = length2;
        while (end1 > start1 && end2 > start2 && string1.charAt(end1 - 1) == string2.charAt(end2 - 1)) {
            end1--;
            end2--;
        }

        return new int[] {start1, end1, start2, end2};
    }
}
