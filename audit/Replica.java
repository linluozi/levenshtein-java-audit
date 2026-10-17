package audit;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 逐字复刻包内 benchmark 的两个函数（baseline / optimized），
 * 用同轮交错 + 逐轮比值中位数重测，判断「0.65x / 0.81x」是真实回归还是测量问题。
 */
public final class Replica {
    private Replica() { }

    private static final int ROUNDS = 9;
    private static volatile int sink;

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.out.println("JVM " + System.getProperty("java.version"));
        System.out.println("A=pkg baseline (for-loop init, 无 hoist), D=pkg optimized (逐字复刻交付版)");

        String prefix = repeat('p', 2048);
        String suffix = repeat('s', 2048);
        run("P1 4097 长前后缀", prefix + "X" + suffix, prefix + "Y" + suffix);
        run("P2 8000x24 长串在前", repeat('a', 8000), repeat('b', 24));
        run("P2r 24x8000 短串在前", repeat('b', 24), repeat('a', 8000));
        run("P3 128x128", repeat('a', 128), repeat('b', 128));
        if (sink == Integer.MIN_VALUE) System.out.println(sink);
    }

    private static void run(String name, String a, String b) {
        if (pkgBaseline(a, b) != pkgOptimized(a, b)) throw new AssertionError("mismatch " + name);
        int iterations = calibrate(a, b);
        for (int w = 0; w < 3; w++) {
            time(a, b, iterations, true);
            time(a, b, iterations, false);
        }
        double[] ratios = new double[ROUNDS];
        double[] absA = new double[ROUNDS];
        double[] absD = new double[ROUNDS];
        for (int round = 0; round < ROUNDS; round++) {
            double ta;
            double td;
            if ((round & 1) == 0) {
                ta = time(a, b, iterations, true) / (double) iterations;
                td = time(a, b, iterations, false) / (double) iterations;
            } else {
                td = time(a, b, iterations, false) / (double) iterations;
                ta = time(a, b, iterations, true) / (double) iterations;
            }
            ratios[round] = ta / td;
            absA[round] = ta / 1e3;
            absD[round] = td / 1e3;
        }
        System.out.printf("%n%s%n  iters=%d  baseline=%.1f us  optimized=%.1f us%n",
            name, iterations, median(absA), median(absD));
        System.out.printf("  baseline/optimized median %.3fx  [%.3f, %.3f]  (包内报的是这个数的倒数)%n",
            median(ratios), min(ratios), max(ratios));
    }

    private static long time(String a, String b, int iterations, boolean base) {
        long start = System.nanoTime();
        int local = 0;
        for (int i = 0; i < iterations; i++) local += base ? pkgBaseline(a, b) : pkgOptimized(a, b);
        long elapsed = System.nanoTime() - start;
        sink += local;
        return elapsed;
    }

    private static int calibrate(String a, String b) {
        int iters = 1;
        while (true) {
            long t = time(a, b, iters, true);
            if (t > 8_000_000L || iters > 1 << 20) return iters;
            iters = Math.max(iters + 1, (int) (iters * 8_000_000.0 / Math.max(1, t)));
        }
    }

    /** 逐字复制自 benchmarks/LevenshteinDistanceBenchmark.java 的 baseline()。 */
    static int pkgBaseline(String string1, String string2) {
        if (string1.isEmpty()) return string2.length();
        int[] previousDistance = new int[string1.length() + 1];
        for (int i = 0; i <= string1.length(); i++) previousDistance[i] = i;
        for (int j = 1; j <= string2.length(); j++) {
            int prevSubstitutionCost = previousDistance[0];
            previousDistance[0] = j;
            for (int i = 1; i <= string1.length(); i++) {
                final int deletionCost = previousDistance[i] + 1;
                final int insertionCost = previousDistance[i - 1] + 1;
                final int substitutionCost = string1.charAt(i - 1) == string2.charAt(j - 1)
                    ? prevSubstitutionCost : prevSubstitutionCost + 1;
                prevSubstitutionCost = previousDistance[i];
                previousDistance[i] = Math.min(deletionCost, Math.min(insertionCost, substitutionCost));
            }
        }
        return previousDistance[string1.length()];
    }

    /** 逐字复制交付版 optimizedLevenshteinDistance + rollingDistance。 */
    static int pkgOptimized(final String string1, final String string2) {
        final int originalLength1 = string1.length();
        final int originalLength2 = string2.length();
        int start1 = 0;
        int start2 = 0;
        while (start1 < originalLength1 && start2 < originalLength2
            && string1.charAt(start1) == string2.charAt(start2)) {
            start1++;
            start2++;
        }
        int end1 = originalLength1;
        int end2 = originalLength2;
        while (end1 > start1 && end2 > start2
            && string1.charAt(end1 - 1) == string2.charAt(end2 - 1)) {
            end1--;
            end2--;
        }
        String middle1 = start1 == 0 && end1 == originalLength1 ? string1 : string1.substring(start1, end1);
        String middle2 = start2 == 0 && end2 == originalLength2 ? string2 : string2.substring(start2, end2);
        int length1 = middle1.length();
        int length2 = middle2.length();
        if (length1 == 0) return length2;
        if (length2 == 0) return length1;
        return length1 <= length2 ? roll(middle1, middle2) : roll(middle2, middle1);
    }

    private static int roll(final String shorter, final String longer) {
        int[] previousDistance = new int[shorter.length() + 1];
        for (int i = 0; i <= shorter.length(); i++) previousDistance[i] = i;
        for (int j = 1; j <= longer.length(); j++) {
            int previousDiagonal = previousDistance[0];
            previousDistance[0] = j;
            final char longerChar = longer.charAt(j - 1);
            for (int i = 1; i <= shorter.length(); i++) {
                final int deletionCost = previousDistance[i] + 1;
                final int insertionCost = previousDistance[i - 1] + 1;
                final int substitutionCost = shorter.charAt(i - 1) == longerChar ? previousDiagonal : previousDiagonal + 1;
                previousDiagonal = previousDistance[i];
                previousDistance[i] = Math.min(deletionCost, Math.min(insertionCost, substitutionCost));
            }
        }
        return previousDistance[shorter.length()];
    }

    static String repeat(char c, int n) {
        char[] buf = new char[n];
        Arrays.fill(buf, c);
        return new String(buf);
    }

    private static double median(double[] v) {
        double[] s = v.clone();
        Arrays.sort(s);
        return s[s.length / 2];
    }

    private static double min(double[] v) {
        double m = Double.MAX_VALUE;
        for (double x : v) m = Math.min(m, x);
        return m;
    }

    private static double max(double[] v) {
        double m = -Double.MAX_VALUE;
        for (double x : v) m = Math.max(m, x);
        return m;
    }
}
