package audit;

import java.util.Arrays;
import java.util.Random;

/** case 定义，供 One 单进程调用。 */
public final class Cases {
    private Cases() { }

    public static String[] get(String id) {
        switch (id) {
            case "P1": return new String[] {"P1: 4097 chars, long shared edges", rep('p', 2048) + "X" + rep('s', 2048),
                rep('p', 2048) + "Y" + rep('s', 2048)};
            case "P2": return new String[] {"P2: 8000x24, long string first", rep('a', 8000), rep('b', 24)};
            case "P2r": return new String[] {"P2r: 24x8000, short string first", rep('b', 24), rep('a', 8000)};
            case "P3": return new String[] {"P3: 128x128, no shared edges", rep('a', 128), rep('b', 128)};

            case "R1": return new String[] {"file path", "/srv/app/releases/2026-10-17/api-gateway/config/production.yaml",
                "/srv/app/releases/2026-10-17/api-gateway/config/staging.yaml"};
            case "R2": return new String[] {"URL", "https://api.example.com/v2/users/10086/orders?page=3&size=20",
                "https://api.example.com/v2/users/10087/orders?page=3&size=20"};
            case "R3": return new String[] {"english sentence, one word differs",
                "The quick brown fox jumps over the lazy dog near the river bank",
                "The quick brown fox leaps over the lazy dog near the river bank"};
            case "R4": return new String[] {"kitten / sitting", "kitten", "sitting"};
            case "R5": return new String[] {"implementation / documentation", "implementation", "documentation"};
            case "R6": return new String[] {"chinese, 3 chars", "\u82f9\u679c\u624b\u673a", "\u82f9\u679c\u8033\u673a"};
            case "R7": return new String[] {"chinese, 4 chars, all differ", "\u4eca\u5929\u5929\u6c14", "\u660e\u5929\u65f6\u95f4"};
            case "R8": return new String[] {"version string", "v2.14.3-rc.1+build.20261017", "v2.14.4-rc.1+build.20261017"};
            case "R9": return new String[] {"6000-char document vs short query", doc(6000), "levenshtein distance"};
            case "R10": return new String[] {"name + city row", "Zhang Wei, Beijing, 100084", "Zhang Wei, Shanghai, 100084"};

            case "S24": return new String[] {"asymmetric 24x24", rep('a', 24), rep('b', 24)};
            case "S96": return new String[] {"asymmetric 96x24", rep('a', 96), rep('b', 24)};
            case "S384": return new String[] {"asymmetric 384x24", rep('a', 384), rep('b', 24)};
            case "S1536": return new String[] {"asymmetric 1536x24", rep('a', 1536), rep('b', 24)};

            case "L1": return new String[] {"20000 chars, no shared edges", doc(20000), doc2(20000)};
            case "L2": return new String[] {"20000 chars, 10000 shared prefix", pre(10000) + doc(10000), pre(10000) + doc2(10000)};

            case "T1": return new String[] {"log line, similar",
                "2026-10-17 23:07:03 ERROR com.example.api.Gateway - upstream timeout after 3000ms",
                "2026-10-17 23:07:04 ERROR com.example.api.Gateway - upstream timeout after 5000ms"};
            case "T2": return new String[] {"log line, dissimilar",
                "2026-10-17 23:07:03 ERROR com.example.api.Gateway - upstream timeout after 3000ms",
                "2026-10-17 23:08:11 INFO com.example.db.Pool - connection acquired id=42"};
            default: throw new IllegalArgumentException("unknown case " + id);
        }
    }

    static String rep(char c, int n) {
        char[] b = new char[n];
        Arrays.fill(b, c);
        return new String(b);
    }

    static String pre(int n) {
        return doc(n);
    }

    static String doc(int n) {
        String p = "Levenshtein distance is a string metric for measuring the difference "
            + "between two sequences. Informally it is the minimum number of single-character "
            + "edits required to change one word into the other. ";
        StringBuilder sb = new StringBuilder(n + p.length());
        while (sb.length() < n) sb.append(p);
        return sb.substring(0, n);
    }

    static String doc2(int n) {
        String p = "Edit distance quantifies how dissimilar two strings are by counting the "
            + "minimum number of operations needed to transform one into the other. ";
        StringBuilder sb = new StringBuilder(n + p.length());
        while (sb.length() < n) sb.append(p);
        return sb.substring(0, n);
    }

    static String rand(Random r, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append((char) ('a' + r.nextInt(26)));
        return sb.toString();
    }
}
