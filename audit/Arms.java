package audit;

import java.util.stream.IntStream;

/** 五个相邻臂，用于把「平凡清理 / 交换方向 / 前后缀裁剪」逐项隔离。 */
public final class Arms {
    private Arms() { }

    /** A：上游原文，逐字照抄（内联在 public 方法里，IntStream 初始化行）。 */
    public static int aUpstream(final String string1, final String string2) {
        if (string1.isEmpty()) {
            return string2.length();
        }
        int[] previousDistance = IntStream.rangeClosed(0, string1.length()).toArray();
        for (int j = 1; j <= string2.length(); j++) {
            int prevSubstitutionCost = previousDistance[0];
            previousDistance[0] = j;
            for (int i = 1; i <= string1.length(); i++) {
                final int deletionCost = previousDistance[i] + 1;
                final int insertionCost = previousDistance[i - 1] + 1;
                final int substitutionCost = (string1.charAt(i - 1) == string2.charAt(j - 1)) ? prevSubstitutionCost : prevSubstitutionCost + 1;
                prevSubstitutionCost = previousDistance[i];
                previousDistance[i] = Math.min(deletionCost, Math.min(insertionCost, substitutionCost));
            }
        }
        return previousDistance[string1.length()];
    }

    /** A2：只做「什么都不改只整理代码」——抽方法 + 提局部变量 + for 循环建行，方向不变、无裁剪。 */
    public static int a2Refactor(final String string1, final String string2) {
        if (string1.isEmpty()) return string2.length();
        return roll(string1, string2);
    }

    /** B：A2 + 短串优先（交换方向），无裁剪。 */
    public static int bSwapOnly(final String string1, final String string2) {
        if (string1.length() <= string2.length()) return roll(string1, string2);
        return roll(string2, string1);
    }

    /** C：A2 + 前后缀裁剪，不交换方向。 */
    public static int cTrimOnly(final String string1, final String string2) {
        int[] m = middle(string1, string2);
        String middle1 = string1.substring(m[0], m[1]);
        String middle2 = string2.substring(m[2], m[3]);
        if (middle1.length() == 0) return middle2.length();
        if (middle2.length() == 0) return middle1.length();
        return roll(middle1, middle2);
    }

    /** D：交付版（裁剪 + 交换）。 */
    public static int dFull(final String string1, final String string2) {
        int[] m = middle(string1, string2);
        String middle1 = string1.substring(m[0], m[1]);
        String middle2 = string2.substring(m[2], m[3]);
        int length1 = middle1.length();
        int length2 = middle2.length();
        if (length1 == 0) return length2;
        if (length2 == 0) return length1;
        return length1 <= length2 ? roll(middle1, middle2) : roll(middle2, middle1);
    }


    /** F：修复版（trim 抽成 unmatchedRanges + short-first 抽成 rowString，逻辑同 D）。 */
    public static int fFixed(final String string1, final String string2) {
        if (string1.isEmpty()) return string2.length();
        if (string2.isEmpty()) return string1.length();
        int[] r = middle(string1, string2);
        String m1 = string1.substring(r[0], r[1]);
        String m2 = string2.substring(r[2], r[3]);
        if (m1.isEmpty()) return m2.length();
        if (m2.isEmpty()) return m1.length();
        return m1.length() <= m2.length() ? roll(m1, m2) : roll(m2, m1);
    }

    /** 返回 {start1, end1, start2, end2}。 */
    static int[] middle(final String s1, final String s2) {
        int n1 = s1.length();
        int n2 = s2.length();
        int start1 = 0;
        int start2 = 0;
        while (start1 < n1 && start2 < n2 && s1.charAt(start1) == s2.charAt(start2)) {
            start1++;
            start2++;
        }
        int end1 = n1;
        int end2 = n2;
        while (end1 > start1 && end2 > start2 && s1.charAt(end1 - 1) == s2.charAt(end2 - 1)) {
            end1--;
            end2--;
        }
        return new int[] {start1, end1, start2, end2};
    }

    /** 行主序滚动数组，第一个参数必须不长于第二个。 */
    static int roll(final String shorter, final String longer) {
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
}
