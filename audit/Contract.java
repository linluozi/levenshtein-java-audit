package audit;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** 接口契约比对：null、代理对、前后缀重叠、全等串、单字符。 */
public final class Contract {
    private Contract() { }

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.out.printf("%-36s %-26s %-26s %s%n", "input", "upstream", "package", "agree?");
        row("null, \"abc\"", null, "abc");
        row("\"abc\", null", "abc", null);
        row("null, null", null, null);
        row("\"\\uD83D\\uDE42\", \"\\uD83D\\uDE43\"", "\uD83D\uDE42", "\uD83D\uDE43");
        row("surrogate pair, low half differs", "a\uD83D\uDE42", "a\uD83D\uDE43");
        row("same high surrogate, low differs", "\uD83D\uDE00x", "\uD83D\uDE01x");
        row("lone high surrogate", "\uD83D", "\uD83D\uDE00");
        row("identical, 1000 chars", rep('z', 1000), rep('z', 1000));
        row("overlapping edges: aaa / aa", "aaa", "aa");
        row("overlapping edges: abab / ab", "abab", "ab");
        row("overlapping edges: abcabc / abc", "abcabc", "abc");
        row("shared suffix only", "Xabc", "Yabc");
        row("shared prefix only", "abcX", "abcY");
        row("empty middle", "abc", "abc");
        row("single char, equal", "q", "q");
        row("single char, differs", "q", "r");
        row("NUL character", "\u0000a", "\u0000b");
        row("identical, 80 000 chars", rep('a', 80_000), rep('a', 80_000));
        row("80 000 chars, one differs", rep('a', 80_000), rep('a', 79_999) + "b");
        row("80 000 chars, both ends differ", "X" + rep('a', 79_998) + "Y", "P" + rep('a', 79_998) + "Q");
    }

    private static void row(String name, String a, String b) {
        String up = safe(() -> Arms.aUpstream(a, b));
        String nw = safe(() -> Arms.dFull(a, b));
        String naive = safe(() -> Naive.distance(a, b));
        boolean same = up.equals(nw) && up.equals(naive);
        System.out.printf("%-36s %-26s %-26s %s%n", name, up, nw, same ? "OK" : "DIFFERS (reference=" + naive + ")");
    }

    private interface Call {
        int run();
    }

    private static String safe(Call c) {
        try {
            return String.valueOf(c.run());
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName();
        }
    }

    private static String rep(char ch, int n) {
        char[] b = new char[n];
        java.util.Arrays.fill(b, ch);
        return new String(b);
    }
}
