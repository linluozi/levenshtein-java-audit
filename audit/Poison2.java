package audit;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

/**
 * 对「修复版」的测试套件喂毒：验证新增的结构断言能否抓住「优化被删掉但结果仍对」。
 * 这里复刻修复版的测试逻辑（含结构断言），实现换成毒株。
 */
public final class Poison2 {
    private Poison2() { }

    private static final String[][] STRAINS = {
        {"0", "control: fixed version as-is (must pass)"},
        {"1", "* trim removed, short-first kept"},
        {"2", "* short-first removed, row follows arg1"},
        {"3", "* both removed (= upstream original)"},
        {"4", "start1++ but start2 frozen"},
        {"5", "suffix trims end1 only"},
        {"6", "drop the end>start guard (crashes)"},
        {"7", "return 0"},
        {"8", "insertion cost +1 becomes +0"},
        {"9", "substitutionCost misses +1"},
        {"10", "row[0] = j written as row[0]++"},
        {"11", "only string1.isEmpty is checked"},
    };

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        for (String[] s : STRAINS) {
            String result;
            try {
                suite(Integer.parseInt(s[0]));
                result = "MISSED (suite green)";
            } catch (AssertionError e) {
                result = "caught: " + e.getMessage();
            } catch (RuntimeException e) {
                result = "caught (crash): " + e.getClass().getSimpleName();
            }
            System.out.printf("[%2s] %-44s -> %s%n", s[0], s[1], result);
        }
    }

    static void suite(int strain) {
        // 行为断言
        Object[][] known = {
            {"", "", 0}, {"", "abc", 3}, {"abc", "", 3}, {"kitten", "sitting", 3},
            {"flaw", "lawn", 2}, {"same", "same", 0}, {"prefix-X-suffix", "prefix-Y-suffix", 1},
            {"a", "ba", 1}, {"ba", "a", 1}, {"\uD83D\uDE42", "\uD83D\uDE43", 1},
            {"aaa", "aa", 1}, {"abab", "ab", 2}, {"abcabc", "abc", 3},
        };
        for (Object[] k : known) {
            String a = (String) k[0];
            String b = (String) k[1];
            int exp = (Integer) k[2];
            int act = dist(strain, a, b);
            if (act != exp) throw new AssertionError("known " + exp + " got " + act);
            if (act != dist(strain, b, a)) throw new AssertionError("symmetry");
        }

        java.util.List<String> strings = new java.util.ArrayList<>();
        strings.add("");
        build(strings, "", 5, new char[] {'a', 'b', 'c'});
        for (String a : strings) {
            for (String b : strings) {
                int exp = Naive.distance(a, b);
                if (dist(strain, a, b) != exp) throw new AssertionError("exhaustive " + exp);
                if (dist(strain, b, a) != exp) throw new AssertionError("exhaustive rev " + exp);
            }
        }

        Random r = new Random(20261017L);
        for (int i = 0; i < 5000; i++) {
            String a = rand(r, r.nextInt(65));
            String b = rand(r, r.nextInt(65));
            int exp = Naive.distance(a, b);
            if (dist(strain, a, b) != exp) throw new AssertionError("random " + i);
        }

        String longS = rep('a', 80_000);
        String shortS = "bbbbbbbbbbbb";
        if (dist(strain, longS, shortS) != 80_000) throw new AssertionError("long asymmetric");
        if (dist(strain, shortS, longS) != 80_000) throw new AssertionError("long asymmetric rev");

        // ★ 结构断言（修复版新增）
        String prefix = rep('p', 2048);
        String suffix = rep('s', 2048);
        String f = prefix + "X" + suffix;
        String s = prefix + "Y" + suffix;
        if (dist(strain, f, s) != 1) throw new AssertionError("structural distance");
        int[] ranges = ranges(strain, f, s);
        int[] want = {2048, 2049, 2048, 2049};
        if (!Arrays.equals(ranges, want)) throw new AssertionError("structural ranges " + Arrays.toString(ranges) + " != " + Arrays.toString(want));

        if (rowLen(strain, rep('a', 8000), rep('b', 24)) != 25) throw new AssertionError("row length long-first");
        if (rowLen(strain, rep('b', 24), rep('a', 8000)) != 25) throw new AssertionError("row length short-first");
    }

    static void build(java.util.List<String> out, String prefix, int remaining, char[] alphabet) {
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
        Arrays.fill(b, c);
        return new String(b);
    }

    /** 复刻修复版的 unmatchedRanges（带毒）。 */
    static int[] ranges(int strain, String s1, String s2) {
        int n1 = s1.length();
        int n2 = s2.length();
        int start1 = 0;
        int start2 = 0;
        if (strain != 1 && strain != 3) {
            if (strain == 4) {
                while (start1 < n1 && start2 < n2 && s1.charAt(start1) == s2.charAt(start2)) {
                    start1++;
                }
            } else {
                while (start1 < n1 && start2 < n2 && s1.charAt(start1) == s2.charAt(start2)) {
                    start1++;
                    start2++;
                }
            }
        }
        int end1 = n1;
        int end2 = n2;
        if (strain != 1 && strain != 3) {
            while (true) {
                boolean inRange = strain == 6 ? (end1 > 0 && end2 > 0) : (end1 > start1 && end2 > start2);
                if (!inRange) break;
                if (s1.charAt(end1 - 1) != s2.charAt(end2 - 1)) break;
                end1--;
                if (strain != 5) end2--;
            }
        }
        return new int[] {start1, end1, start2, end2};
    }

    static int rowLen(int strain, String s1, String s2) {
        boolean shortFirst = strain != 2 && strain != 3;
        String row = shortFirst ? (s1.length() <= s2.length() ? s1 : s2) : s1;
        return row.length() + 1;
    }

    static int dist(int strain, String s1, String s2) {
        if (strain == 7) return 0;
        int n1 = s1.length();
        int n2 = s2.length();
        if (strain == 11) {
            if (n1 == 0) return n2;
        } else {
            if (n1 == 0) return n2;
            if (n2 == 0) return n1;
        }
        int[] rg = ranges(strain, s1, s2);
        String m1 = s1.substring(rg[0], rg[1]);
        String m2 = s2.substring(rg[2], rg[3]);
        if (m1.isEmpty()) return m2.length();
        if (m2.isEmpty()) return m1.length();

        boolean shortFirst = strain != 2 && strain != 3;
        String shorter = shortFirst ? (m1.length() <= m2.length() ? m1 : m2) : m1;
        String longer = shorter == m1 ? m2 : m1;
        return roll(strain, shorter, longer);
    }

    static int roll(int strain, String shorter, String longer) {
        int sn = shorter.length();
        int[] row = new int[sn + 1];
        for (int i = 0; i <= sn; i++) row[i] = i;
        for (int j = 1; j <= longer.length(); j++) {
            int diag = row[0];
            if (strain == 10) row[0]++;
            else row[0] = j;
            char lc = longer.charAt(j - 1);
            for (int i = 1; i <= sn; i++) {
                int del = row[i] + 1;
                int ins = row[i - 1] + (strain == 8 ? 0 : 1);
                int sub = strain == 9 ? diag : (shorter.charAt(i - 1) == lc ? diag : diag + 1);
                diag = row[i];
                row[i] = Math.min(del, Math.min(ins, sub));
            }
        }
        return row[sn];
    }
}
