This repository is an independent audit. It is not affiliated with, endorsed
by, or a pull request against TheAlgorithms/Java.

Source provenance
-----------------
`src/main/java/com/thealgorithms/dynamicprogramming/LevenshteinDistance.java`
is derived from:

    TheAlgorithms/Java
    src/main/java/com/thealgorithms/dynamicprogramming/LevenshteinDistance.java
    https://github.com/TheAlgorithms/Java

Licensed under the MIT License; the upstream copyright and permission notice
are reproduced verbatim in `LICENSE`, which is byte-for-byte identical to the
upstream file.

Modifications
-------------
Relative to upstream `master` as fetched on 2026-10-17, the derived file:

- adds prefix/suffix trimming before the dynamic-programming stage;
- allocates the rolling row from the shorter of the two strings, which is what
  the upstream Javadoc already claims the method does;
- extracts `unmatchedRanges`, `rowString` and `rollingRowLength` as
  package-private methods so the tests can assert the optimisation is present;
- adds braces to every `if` and `for`, as required by the upstream checkstyle
  ruleset;
- adds a `string2.isEmpty()` early return.

The file under `audit/` is original work: it re-implements the upstream and
third-party algorithms in order to compare them, and is not derived from either.

The third-party archive reviewed here is not redistributed. Only measurements
and short quotations of its own reported numbers appear in this repository.
