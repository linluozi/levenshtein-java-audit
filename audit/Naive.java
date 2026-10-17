package audit;

/** 独立全矩阵实现，作为唯一可信参考答案（不用包内 naive 的 IntStream 版本，避免共享同一 bug）。 */
public final class Naive {
    private Naive() { }

    public static int distance(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int del = prev[j] + 1;
                int ins = cur[j - 1] + 1;
                int sub = prev[j - 1] + cost;
                cur[j] = Math.min(del, Math.min(ins, sub));
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[m];
    }
}
