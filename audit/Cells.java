package audit;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** 精确统计 DP 单元格访问次数与 row 长度（结构量，确定性，不受机器影响）。 */
public final class Cells {
    private Cells() { }

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.out.printf("%-30s %10s %10s %12s %10s %10s%n",
            "case", "upstream cells", "trimmed cells", "reduction", "up row", "new row");
        String[][] cases = {
            {"P1 4097 long edges", Cases.get("P1")[1], Cases.get("P1")[2]},
            {"P2 8000x24 long first", Cases.get("P2")[1], Cases.get("P2")[2]},
            {"P2r 24x8000 short first", Cases.get("P2r")[1], Cases.get("P2r")[2]},
            {"P3 128x128", Cases.get("P3")[1], Cases.get("P3")[2]},
            {"R1 file path", Cases.get("R1")[1], Cases.get("R1")[2]},
            {"R2 URL", Cases.get("R2")[1], Cases.get("R2")[2]},
            {"R3 english sentence", Cases.get("R3")[1], Cases.get("R3")[2]},
            {"R5 implementation/doc", Cases.get("R5")[1], Cases.get("R5")[2]},
            {"R6 chinese 3 chars", Cases.get("R6")[1], Cases.get("R6")[2]},
            {"R8 version string", Cases.get("R8")[1], Cases.get("R8")[2]},
            {"T1 log line similar", Cases.get("T1")[1], Cases.get("T1")[2]},
            {"T2 log line dissimilar", Cases.get("T2")[1], Cases.get("T2")[2]},
        };
        for (String[] c : cases) {
            long up = (long) c[1].length() * c[2].length();
            int[] m = Arms.middle(c[1], c[2]);
            long nw = (long) (m[1] - m[0]) * (m[3] - m[2]);
            int upRow = c[1].length() + 1;
            int nwRow = Math.min(m[1] - m[0], m[3] - m[2]) + 1;
            System.out.printf("%-30s %10d %10d %11.1fx %10d %10d%n",
                c[0], up, Math.max(nw, 1), up / (double) Math.max(nw, 1), upRow, nwRow);
        }
    }
}
