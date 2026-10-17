package audit;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 关键问题：这种「先跑相似串、再跑非对称串」的减速是**一次性过渡**，还是**持续存在**？
 * 若持续存在，则混合负载的真实服务会永久慢 3.8 倍 —— 那是生产事故级的缺陷。
 * 用法: java audit.Mixed <mode>
 *   transition   先跑 P1 若干轮，再连续测 P2 十批（看是否自愈）
 *   interleave   P1 与 P2 交替（模拟真实混合负载）
 */
public final class Mixed {
    private Mixed() { }

    private static volatile int sink;

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        String[] p1 = Cases.get("P1");
        String[] p2 = Cases.get("P2");

        if (args[0].equals("transition")) {
            System.out.println("阶段: 先跑 P1（相似长串），然后连续 10 批 P2（8000x24），看是否自愈");
            for (int r = 0; r < 5; r++) loopOpt(p1[1], p1[2], 3);
            for (int batch = 0; batch < 10; batch++) {
                double t = measureOpt(p2[1], p2[2], 60, 2);
                System.out.printf("  P2 第 %2d 批: %.1f us%n", batch + 1, t);
            }
            // 再回到 P1，看是否又变慢
            for (int r = 0; r < 5; r++) loopOpt(p1[1], p1[2], 3);
            System.out.printf("  再跑 P1 后 P2: %.1f us%n", measureOpt(p2[1], p2[2], 60, 2));
        } else {
            System.out.println("阶段: P1 与 P2 交替 12 轮（模拟混合负载）");
            double[] p2Times = new double[12];
            for (int round = 0; round < 12; round++) {
                loopOpt(p1[1], p1[2], 2);
                p2Times[round] = measureOpt(p2[1], p2[2], 40, 1);
            }
            System.out.print("  P2 逐轮:");
            for (double t : p2Times) System.out.printf(" %.0f", t);
            System.out.println();
            double[] sorted = p2Times.clone();
            Arrays.sort(sorted);
            System.out.printf("  P2 中位数 %.1f us, 最小 %.1f, 最大 %.1f%n",
                sorted[sorted.length / 2], sorted[0], sorted[sorted.length - 1]);
        }
        if (sink == Integer.MIN_VALUE) System.out.println(sink);
    }

    private static double measureOpt(String a, String b, int iters, int warmBatches) {
        for (int w = 0; w < warmBatches; w++) loopOpt(a, b, iters);
        long[] s = new long[5];
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

    private static void loopOpt(String a, String b, int n) {
        int v = 0;
        for (int i = 0; i < n; i++) v += Replica.pkgOptimized(a, b);
        sink += v;
    }
}
