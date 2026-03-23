package org.bsdevelopment.codefracture.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DiffTab extends Tab {

    private static final int CONTEXT_LINES = 3;

    private final CodeArea codeArea;
    private final VirtualizedScrollPane<CodeArea> scrollPane;
    private final StackPane contentPane;

    public DiffTab(String title) {
        setText(title);
        setTooltip(new Tooltip(title));

        codeArea = new CodeArea();
        codeArea.setEditable(false);
        codeArea.getStyleClass().add("code-area");
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));

        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (!newText.isEmpty()) {
                try {
                    var spans = DiffHighlighter.computeHighlighting(newText);
                    if (spans != null) codeArea.setStyleSpans(0, spans);
                } catch (Exception ignored) {
                }
            }
        });

        scrollPane = new VirtualizedScrollPane<>(codeArea);
        scrollPane.getStyleClass().add("code-scroll-pane");
        scrollPane.setMaxWidth(Double.MAX_VALUE);
        scrollPane.setMaxHeight(Double.MAX_VALUE);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(56, 56);
        Label spinnerLabel = new Label("Computing diff\u2026");
        VBox spinnerBox = new VBox(8, spinner, spinnerLabel);
        spinnerBox.setAlignment(Pos.CENTER);

        contentPane = new StackPane(spinnerBox);
        contentPane.getStyleClass().add("code-container");
        setContent(contentPane);
    }

    private static String buildUnifiedDiff(String textA, String textB, String labelA, String labelB) {
        String[] a = splitLines(textA);
        String[] b = splitLines(textB);

        // Each op: [type, aIdx, bIdx] — type 0=equal, 1=added, 2=removed
        List<int[]> ops = computeDiff(a, b);
        List<List<int[]>> hunks = groupHunks(ops, CONTEXT_LINES);

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

    /** LCS-based diff. Returns ops as [type, aIdx, bIdx] — type 0=equal, 1=added, 2=removed. */
    private static List<int[]> computeDiff(String[] a, String[] b) {
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

    /** Groups diff ops into hunks with {@code ctx} context lines on each side. */
    private static List<List<int[]>> groupHunks(List<int[]> ops, int ctx) {
        int n = ops.size();
        boolean[] inHunk = new boolean[n];

        for (int i = 0; i < n; i++) {
            if (ops.get(i)[0] != 0) {
                for (int j = Math.max(0, i - ctx); j < Math.min(n, i + ctx + 1); j++)
                    inHunk[j] = true;
            }
        }

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

    private static String[] splitLines(String text) {
        String[] lines = text.split("\n", -1);
        if (lines.length > 0 && lines[lines.length - 1].isEmpty())
            lines = Arrays.copyOf(lines, lines.length - 1);
        return lines;
    }

    public void setDiff(String sourceA, String sourceB, String labelA, String labelB) {
        Platform.runLater(() -> {
            String diff = buildUnifiedDiff(sourceA, sourceB, labelA, labelB);
            codeArea.replaceText(diff);
            codeArea.moveTo(0);
            codeArea.requestFollowCaret();
            contentPane.getChildren().setAll(scrollPane);
        });
    }

    /**
     * Shows a file that exists only in one JAR — all lines prefixed with + or -.
     *
     * @param isAdded true if the file is new (only in JAR B), false if removed (only in JAR A)
     */
    public void setOnlyInOne(String source, boolean isAdded, String label) {
        Platform.runLater(() -> {
            String[] lines = splitLines(source);
            StringBuilder sb = new StringBuilder();
            if (isAdded) {
                sb.append("--- /dev/null\n");
                sb.append("+++ ").append(label).append('\n');
                sb.append("@@ -0,0 +1,").append(lines.length).append(" @@\n");
                for (String line : lines) sb.append('+').append(line).append('\n');
            } else {
                sb.append("--- ").append(label).append('\n');
                sb.append("+++ /dev/null\n");
                sb.append("@@ -1,").append(lines.length).append(" +0,0 @@\n");
                for (String line : lines) sb.append('-').append(line).append('\n');
            }
            codeArea.replaceText(sb.toString());
            codeArea.moveTo(0);
            codeArea.requestFollowCaret();
            contentPane.getChildren().setAll(scrollPane);
        });
    }

    public void setError(String message) {
        Platform.runLater(() -> {
            codeArea.replaceText("// Error:\n// " + message);
            codeArea.moveTo(0);
            codeArea.requestFollowCaret();
            contentPane.getChildren().setAll(scrollPane);
        });
    }

    public CodeArea getCodeArea() {
        return codeArea;
    }

    public String getDiffText() {
        return codeArea.getText();
    }
}
