package org.bsdevelopment.codefracture.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Static utility that computes unified-diff strings from two text documents. */
public final class Differ {

    static final int CONTEXT = 3;

    private Differ() {
    }

    /** Unified diff — only sections that changed, with {@value #CONTEXT} context lines each side. */
    public static String unifiedDiff(String textA, String textB, String labelA, String labelB) {
        String[] a = splitLines(textA);
        String[] b = splitLines(textB);
        List<int[]> ops = lcs(a, b);
        List<List<int[]>> hunks = groupHunks(ops, CONTEXT);

        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(labelA).append('\n');
        sb.append("+++ ").append(labelB).append('\n');

        if (hunks.isEmpty()) {
            sb.append("// No differences found");
            return sb.toString();
        }
        for (List<int[]> hunk : hunks) {
            int aStart = -1, aCount = 0, bStart = -1, bCount = 0;
            for (int[] op : hunk) {
                if (op[0] != 1 && aStart < 0) aStart = op[1];
                if (op[0] != 2 && bStart < 0) bStart = op[2];
                if (op[0] != 1) aCount++;
                if (op[0] != 2) bCount++;
            }
            if (aStart < 0) aStart = 0;
            if (bStart < 0) bStart = 0;
            sb.append(String.format("@@ -%d,%d +%d,%d @@%n", aStart + 1, aCount, bStart + 1, bCount));
            for (int[] op : hunk) {
                if (op[0] == 0) sb.append(' ').append(a[op[1]]).append('\n');
                else if (op[0] == 1) sb.append('+').append(b[op[2]]).append('\n');
                else sb.append('-').append(a[op[1]]).append('\n');
            }
        }
        return sb.toString();
    }

    /** All lines shown as added (+) — file exists only in the new JAR. */
    public static String addedDiff(String source, String label) {
        String[] lines = splitLines(source);
        StringBuilder sb = new StringBuilder();
        sb.append("--- /dev/null\n+++ ").append(label).append('\n');
        sb.append("@@ -0,0 +1,").append(lines.length).append(" @@\n");
        for (String line : lines) sb.append('+').append(line).append('\n');
        return sb.toString();
    }

    /** All lines shown as removed (-) — file exists only in the old JAR. */
    public static String removedDiff(String source, String label) {
        String[] lines = splitLines(source);
        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(label).append('\n').append("+++ /dev/null\n");
        sb.append("@@ -1,").append(lines.length).append(" +0,0 @@\n");
        for (String line : lines) sb.append('-').append(line).append('\n');
        return sb.toString();
    }

    // ── Internals ────────────────────────────────────────────────────────────

    /** LCS-based diff. Returns list of [type, aIdx, bIdx]: 0=equal, 1=added, 2=removed. */
    static List<int[]> lcs(String[] a, String[] b) {
        int m = a.length, n = b.length;
        if (Arrays.equals(a, b)) {
            List<int[]> eq = new ArrayList<>(m);
            for (int i = 0; i < m; i++) eq.add(new int[]{ 0, i, i });
            return eq;
        }
        int[][] dp = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--)
            for (int j = n - 1; j >= 0; j--)
                dp[i][j] = a[i].equals(b[j])
                        ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);

        List<int[]> result = new ArrayList<>();
        int i = 0, j = 0;
        while (i < m || j < n) {
            if (i < m && j < n && a[i].equals(b[j])) {
                result.add(new int[]{ 0, i, j });
                i++;
                j++;
            } else if (j < n && (i >= m || dp[i][j + 1] >= dp[i + 1][j])) {
                result.add(new int[]{ 1, i, j });
                j++;
            } else {
                result.add(new int[]{ 2, i, j });
                i++;
            }
        }
        return result;
    }

    static List<List<int[]>> groupHunks(List<int[]> ops, int ctx) {
        int n = ops.size();
        boolean[] inHunk = new boolean[n];
        for (int i = 0; i < n; i++)
            if (ops.get(i)[0] != 0)
                for (int j = Math.max(0, i - ctx); j < Math.min(n, i + ctx + 1); j++)
                    inHunk[j] = true;

        List<List<int[]>> hunks = new ArrayList<>();
        int i = 0;
        while (i < n) {
            if (!inHunk[i]) {
                i++;
                continue;
            }
            int start = i;
            while (i < n && inHunk[i]) i++;
            hunks.add(new ArrayList<>(ops.subList(start, i)));
        }
        return hunks;
    }

    static String[] splitLines(String text) {
        String[] lines = text.split("\n", -1);
        if (lines.length > 0 && lines[lines.length - 1].isEmpty())
            lines = Arrays.copyOf(lines, lines.length - 1);
        return lines;
    }
}
