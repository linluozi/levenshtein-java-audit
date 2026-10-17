package audit;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 噪声底：同一臂对同一臂（A vs A 的副本），用完全相同的两臂法测量。
 * 若微输入上「自己 vs 自己」都能测出 1.5x，那微输入上的任何小倍数都不可信。
 * 用法: java audit.Noise <caseId>
 */
public final class Noise {
    private Noise() { }

    private static volatile int sink;

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        for (String id : args) {
            String[] p = Cases.get(id);
            String a = p[1];
            String b = p[2];
            // 预热
            for (int w = 0; w < 6; w++) {
                runA(a, b, 200_000);
                runA2(a, b, 200_000);
            }
            int iters = 300_000;
            double[] ratios = new double[11];
            for (int r = 0; r < ratios.length; r++) {
                double ta;
                double tb;
                if ((r & 1) == 0) {
                    ta = runA(a, b, iters) / (double) iters;
                    tb = runA(a, b, iters) / (double) iters;
                } else {
                    tb = runA(a, b, iters) / (double) iters;
                    ta = runA(a, b, iters) / (double) iters;
                }
                ratios[r] = ta / tb;
            }
            double[] s = ratios.clone();
            Arrays.sort(s);
            System.out.printf("%s | %-34s A vs A(副本) median %.3f [%.3f, %.3f] | 单次 %.3f us%n",
                id, p[0], s[s.length / 2], s[0], s[s.length - 1], runA(a, b, iters) / (double) iters / 1e3);
        }
        if (sink == Integer.MIN_VALUE) System.out.println(sink);
    }

    private static long runA(String a, String b, int n) {
        long t = System.nanoTime();
        int v = 0;
        for (int i = 0; i < n; i++) v += Arms.aUpstream(a, b);
        long e = System.nanoTime();
        sink += v;
        return e - t;
    }

    private static long runA2(String a, String b, int n) {
        long t = System.nanoTime();
        int v = 0;
        for (int i = 0; i < n; i++) v += Arms.a2Refactor(a, b);
        long e = System.nanoTime();
        sink += v;
        return e - t;
    }
}
