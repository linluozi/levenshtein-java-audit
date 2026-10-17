package audit;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 反向喂毒：把交付版逐处改坏，看包内测试（test.sh 里那套）能否抓住。
 * 每个毒株独立编译运行，跑的是「包内测试逻辑」的等价复刻 + 交付版代码的毒株副本。
 * 判据：抓到 = 抛 AssertionError / 崩溃；抓不到 = 全部通过。
 */
public final class Poison {
    private Poison() { }

    /** 毒株编号 -> 描述 */
    private static final String[][] STRAINS = {
        {"0", "control: unmodified (must pass)"},
        {"1", "直接返回 0"},
        {"2", "drop the end>start guard (suffix may cross the prefix)"},
        {"3", "trim prefix only (equivalent)"},
        {"4", "swap condition <= becomes < (equivalent)"},
        {"5", "substitutionCost misses +1"},
        {"6", "drop the length==0 early return (equivalent)"},
        {"7", "suffix guard end1>0 (allows overlap)"},
        {"8", "insertion cost +1 becomes +0"},
        {"9", "prefix scan stops after one char (equivalent)"},
        {"10", "always swap (equivalent, memory only)"},
        {"11", "row init skips last element (equivalent)"},
        {"12", "swap condition inverted (equivalent)"},
        {"13", "return abs(len1-len2)"},
        {"14", "suffix trims end1 only"},
        {"15", "empty middle returns 0"},
        {"16", "start1++ but start2 frozen"},
        {"17", "only length1==0 is checked"},
        {"18", "row[0] = j written as row[0]++"},
        {"19", "* trim removed entirely (optimisation gone, result correct)"},
        {"20", "* swap removed (memory goal gone, result correct)"},
    };

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        String only = args.length > 0 ? args[0] : null;
        for (String[] s : STRAINS) {
            if (only != null && !only.equals(s[0])) continue;
            String result;
            try {
                runSuite(Integer.parseInt(s[0]));
                result = "MISSED (suite green)";
            } catch (AssertionError e) {
                result = "caught: " + e.getMessage();
            } catch (RuntimeException e) {
                result = "caught (crash): " + e.getClass().getSimpleName();
            }
            System.out.printf("[%2s] %-46s -> %s%n", s[0], s[1], result);
        }
    }

    /** 复刻包内测试的四组断言，但调用的是毒株实现。 */
    static void runSuite(int strain) {
        // 1) 已知用例 + 对称性（包内 assertDistance）
        Object[][] known = {
            {"", "", 0}, {"", "abc", 3}, {"abc", "", 3}, {"kitten", "sitting", 3},
            {"flaw", "lawn", 2}, {"same", "same", 0}, {"prefix-X-suffix", "prefix-Y-suffix", 1},
            {"a", "ba", 1}, {"ba", "a", 1}, {"\uD83D\uDE42", "\uD83D\uDE43", 1},
        };
        for (Object[] k : known) {
            String a = (String) k[0];
            String b = (String) k[1];
            int expected = (Integer) k[2];
            int actual = impl(strain, a, b);
            if (actual != expected) throw new AssertionError("known '" + a + "'/'" + b + "' expected " + expected + " got " + actual);
            if (actual != impl(strain, b, a)) throw new AssertionError("symmetry failed");
        }

        // 2) 穷举短串（字母表 {a,b,c}，长度 ≤5）
        List<String> strings = new ArrayList<>();
        strings.add("");
        build(strings, "", 5, new char[] {'a', 'b', 'c'});
        for (String a : strings) {
            for (String b : strings) {
                int expected = Naive.distance(a, b);
                int actual = impl(strain, a, b);
                if (expected != actual) {
                    throw new AssertionError("exhaustive '" + a + "'/'" + b + "' expected " + expected + " got " + actual);
                }
            }
        }

        // 3) 5000 确定性随机
        Random r = new Random(20261017L);
        for (int i = 0; i < 5000; i++) {
            String a = rand(r, r.nextInt(65));
            String b = rand(r, r.nextInt(65));
            int expected = Naive.distance(a, b);
            int actual = impl(strain, a, b);
            if (expected != actual) throw new AssertionError("random sample " + i + " expected " + expected + " got " + actual);
        }

        // 4) 长非对称
        String longS = rep('a', 80_000);
        String shortS = "bbbbbbbbbbbb";
        int d = impl(strain, longS, shortS);
        if (d != 80_000) throw new AssertionError("long asymmetric expected 80000 got " + d);
        if (impl(strain, shortS, longS) != d) throw new AssertionError("reversed long asymmetric mismatch");
    }

    static void build(List<String> out, String prefix, int remaining, char[] alphabet) {
        if (!prefix.isEmpty()) out.add(prefix);
        if (remaining == 0) return;
        for (char c : alphabet) build(out, prefix + c, remaining - 1, alphabet);
    }

    static String rand(Random r, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append((char) ('a' + r.nextInt(5)));
        return sb.toString();
    }

    static String rep(char c, int n) {
        char[] b = new char[n];
        java.util.Arrays.fill(b, c);
        return new String(b);
    }

    // ---------------- 毒株实现 ----------------

    static int impl(int strain, String s1, String s2) {
        if (strain == 1) return 0;
        if (strain == 13) return Math.abs(s1.length() - s2.length());

        int n1 = s1.length();
        int n2 = s2.length();
        int start1 = 0;
        int start2 = 0;

        if (strain == 9) {
            if (n1 > 0 && n2 > 0 && s1.charAt(0) == s2.charAt(0)) { start1 = 1; start2 = 1; }
        } else if (strain == 16) {
            while (start1 < n1 && start2 < n2 && s1.charAt(start1) == s2.charAt(start2)) {
                start1++;
            }
        } else if (strain != 19) {
            while (start1 < n1 && start2 < n2 && s1.charAt(start1) == s2.charAt(start2)) {
                start1++;
                start2++;
            }
        }

        int end1 = n1;
        int end2 = n2;
        boolean noSuffix = strain == 3 || strain == 19;
        if (!noSuffix) {
            while (true) {
                boolean inRange;
                if (strain == 2) {
                    inRange = end1 > 0 && end2 > 0;
                } else if (strain == 7) {
                    inRange = end1 > 0 && end2 > 0 && (end1 > start1 || end2 > start2);
                } else {
                    inRange = end1 > start1 && end2 > start2;
                }
                if (!inRange) break;
                if (s1.charAt(end1 - 1) != s2.charAt(end2 - 1)) break;
                end1--;
                if (strain != 14) end2--;
            }
        }

        String m1 = start1 == 0 && end1 == n1 ? s1 : s1.substring(start1, end1);
        String m2 = start2 == 0 && end2 == n2 ? s2 : s2.substring(start2, end2);
        int l1 = m1.length();
        int l2 = m2.length();

        if (strain != 6) {
            if (strain == 15) {
                if (l1 == 0 || l2 == 0) return 0;
            } else if (strain == 17) {
                if (l1 == 0) return l2;
            } else {
                if (l1 == 0) return l2;
                if (l2 == 0) return l1;
            }
        }

        boolean swap;
        if (strain == 4) swap = l1 < l2;
        else if (strain == 12) swap = l1 > l2;
        else if (strain == 20) swap = true;
        else swap = l1 <= l2;

        if (strain == 10) return roll(strain, m2, m1);
        return swap ? roll(strain, m1, m2) : roll(strain, m2, m1);
    }

    static int roll(int strain, String shorter, String longer) {
        int sn = shorter.length();
        int[] row = new int[sn + 1];
        int initEnd = strain == 11 ? sn : sn + 1;
        for (int i = 0; i < initEnd && i <= sn; i++) row[i] = i;

        for (int j = 1; j <= longer.length(); j++) {
            int diag = row[0];
            if (strain == 18) row[0]++;
            else row[0] = j;
            char lc = longer.charAt(j - 1);
            for (int i = 1; i <= sn; i++) {
                int del = row[i] + 1;
                int ins = row[i - 1] + (strain == 8 ? 0 : 1);
                int sub;
                if (strain == 5) sub = diag;
                else sub = shorter.charAt(i - 1) == lc ? diag : diag + 1;
                diag = row[i];
                row[i] = Math.min(del, Math.min(ins, sub));
            }
        }
        return row[sn];
    }
}
