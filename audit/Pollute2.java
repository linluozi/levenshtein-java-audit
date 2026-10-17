package audit;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 污染归因：分别用 baseline / optimized / 两者 跑 P1，看谁把 P2 拖慢。
 * 用法: java audit.Pollute2 <mode>
 *   clean        只测 P2
 *   p1-base      先跑 P1 的 baseline
 *   p1-opt       先跑 P1 的 optimized
 *   p1-both      先跑两者（= 包内原样）
 *   p1-once      先跑 P1 一轮（3 次迭代）
 *   warm-p2-first  先充分预热 P2，再跑 P1，再测 P2
 */
public final class Pollute2 {
    private Pollute2() { }

    private static volatile int sink;

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        String mode = args[0];
        String[] p1 = Cases.get("P1");
        String[] p2 = Cases.get("P2");
        String[] p3 = Cases.get("P3");

        boolean warmFirst = mode.equals("warm-p2-first");
        if (warmFirst) {
            for (int w = 0; w < 8; w++) loopOpt(p2[1], p2[2], 60);
        }

        switch (mode) {
            case "p1-base":
                for (int r = 0; r < 5; r++) loopBase(p1[1], p1[2], 3);
                break;
            case "p1-opt":
                for (int r = 0; r < 5; r++) loopOpt(p1[1], p1[2], 3);
                break;
            case "p1-once":
                loopBase(p1[1], p1[2], 3);
                loopOpt(p1[1], p1[2], 3);
                break;
            case "p1-both":
            case "warm-p2-first":
                for (int r = 0; r < 5; r++) {
                    loopBase(p1[1], p1[2], 3);
                    loopOpt(p1[1], p1[2], 3);
                }
                break;
            default:
                break;
        }

        double t2 = measureOpt(p2[1], p2[2], 60);
        double t3 = measureOpt(p3[1], p3[2], 900);
        System.out.printf("%s\tP2=%.2fus\tP3=%.3fus%n", mode, t2, t3);
        if (sink == Integer.MIN_VALUE) System.out.println(sink);
    }

    private static double measureOpt(String a, String b, int iters) {
        for (int w = 0; w < 8; w++) loopOpt(a, b, iters);
        long[] s = new long[7];
        for (int i = 0; i < s.length; i++) {
            long t0 = System.nanoTime();
            int v = 0;
            for (int k = 0; k < iters; k++) v += Replica.pkgOptimized(a, b);
            s[i] = System.nanoTime() - t0;
            sink += v;
        }
        Arrays.sort(s);
        return s[s.length / 2] / (double) iters / 1e3;
    }

    private static void loopBase(String a, String b, int n) {
        int v = 0;
        for (int i = 0; i < n; i++) v += Replica.pkgBaseline(a, b);
        sink += v;
    }

    private static void loopOpt(String a, String b, int n) {
        int v = 0;
        for (int i = 0; i < n; i++) v += Replica.pkgOptimized(a, b);
        sink += v;
    }
}
