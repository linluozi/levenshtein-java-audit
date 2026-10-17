package audit;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 两臂干净对照。每臂独立计时方法（循环体内无分支），交替执行，逐轮比值取中位数。
 * 用法: java audit.Two <caseId> <armX> <armY>
 * arm: A(上游原文) A2(只整理) B(整理+交换) C(整理+裁剪) D(交付版) P(包内baseline逐字) Q(包内optimized逐字)
 */
public final class Two {
    private Two() { }

    private static volatile int sink;
    private static final long TARGET_NANOS = 20_000_000L;

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        String[] pair = Cases.get(args[0]);
        String a = pair[1];
        String b = pair[2];
        String x = args[1];
        String y = args[2];

        int expect = Naive.distance(a, b);
        if (call(x, a, b) != expect || call(y, a, b) != expect) {
            System.out.println("FAIL correctness " + x + "/" + y);
            return;
        }

        warmup(x, a, b);
        warmup(y, a, b);
        // 两臂耗时可能差 100 倍以上，必须各自校准到同一时间预算
        int nx = calibrate(x, a, b);
        int ny = calibrate(y, a, b);

        int rounds = Integer.getInteger("rounds", 11);
        double[] ratios = new double[rounds];
        double[] ax = new double[rounds];
        double[] ay = new double[rounds];
        for (int round = 0; round < rounds; round++) {
            double tx;
            double ty;
            if ((round & 1) == 0) {
                tx = run(x, a, b, nx) / (double) nx;
                ty = run(y, a, b, ny) / (double) ny;
            } else {
                ty = run(y, a, b, ny) / (double) ny;
                tx = run(x, a, b, nx) / (double) nx;
            }
            ratios[round] = tx / ty;
            ax[round] = tx / 1e3;
            ay[round] = ty / 1e3;
        }
        System.out.printf("%s | %s vs %s | iters=%d/%d | %s=%.2fus %s=%.2fus | %s/%s = %.3f [%.3f, %.3f]%n",
            args[0], x, y, nx, ny, x, median(ax), y, median(ay), x, y,
            median(ratios), min(ratios), max(ratios));
        if (sink == Integer.MIN_VALUE) System.out.println(sink);
    }

    private static void warmup(String arm, String a, String b) {
        long deadline = System.nanoTime() + 250_000_000L;
        while (System.nanoTime() < deadline) {
            sink += call(arm, a, b);
        }
    }

    private static int calibrate(String arm, String a, String b) {
        int iters = 1;
        for (int i = 0; i < 40; i++) {
            long t = run(arm, a, b, iters);
            if (t >= TARGET_NANOS) return iters;
            int next = (int) Math.max(iters + 1L, (long) (iters * (double) TARGET_NANOS / Math.max(1L, t)));
            iters = Math.min(next, 1 << 22);
        }
        return iters;
    }

    private static int call(String arm, String a, String b) {
        switch (arm) {
            case "A": return Arms.aUpstream(a, b);
            case "A2": return Arms.a2Refactor(a, b);
            case "B": return Arms.bSwapOnly(a, b);
            case "C": return Arms.cTrimOnly(a, b);
            case "D": return Arms.dFull(a, b);
            case "P": return Replica.pkgBaseline(a, b);
            case "Q": return Replica.pkgOptimized(a, b);
            case "F": return Arms.fFixed(a, b);
            default: throw new IllegalArgumentException(arm);
        }
    }

    private static long run(String arm, String a, String b, int iterations) {
        switch (arm) {
            case "A": return tA(a, b, iterations);
            case "A2": return tA2(a, b, iterations);
            case "B": return tB(a, b, iterations);
            case "C": return tC(a, b, iterations);
            case "D": return tD(a, b, iterations);
            case "P": return tP(a, b, iterations);
            case "Q": return tQ(a, b, iterations);
            case "F": return tF(a, b, iterations);
            default: throw new IllegalArgumentException(arm);
        }
    }

    private static long tA(String a, String b, int n) {
        long s = System.nanoTime();
        int v = 0;
        for (int i = 0; i < n; i++) v += Arms.aUpstream(a, b);
        long e = System.nanoTime();
        sink += v;
        return e - s;
    }

    private static long tA2(String a, String b, int n) {
        long s = System.nanoTime();
        int v = 0;
        for (int i = 0; i < n; i++) v += Arms.a2Refactor(a, b);
        long e = System.nanoTime();
        sink += v;
        return e - s;
    }

    private static long tB(String a, String b, int n) {
        long s = System.nanoTime();
        int v = 0;
        for (int i = 0; i < n; i++) v += Arms.bSwapOnly(a, b);
        long e = System.nanoTime();
        sink += v;
        return e - s;
    }

    private static long tC(String a, String b, int n) {
        long s = System.nanoTime();
        int v = 0;
        for (int i = 0; i < n; i++) v += Arms.cTrimOnly(a, b);
        long e = System.nanoTime();
        sink += v;
        return e - s;
    }

    private static long tD(String a, String b, int n) {
        long s = System.nanoTime();
        int v = 0;
        for (int i = 0; i < n; i++) v += Arms.dFull(a, b);
        long e = System.nanoTime();
        sink += v;
        return e - s;
    }

    private static long tP(String a, String b, int n) {
        long s = System.nanoTime();
        int v = 0;
        for (int i = 0; i < n; i++) v += Replica.pkgBaseline(a, b);
        long e = System.nanoTime();
        sink += v;
        return e - s;
    }

    private static long tF(String a, String b, int n) {
        long s = System.nanoTime();
        int v = 0;
        for (int i = 0; i < n; i++) v += Arms.fFixed(a, b);
        long e = System.nanoTime();
        sink += v;
        return e - s;
    }

    private static long tQ(String a, String b, int n) {
        long s = System.nanoTime();
        int v = 0;
        for (int i = 0; i < n; i++) v += Replica.pkgOptimized(a, b);
        long e = System.nanoTime();
        sink += v;
        return e - s;
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
