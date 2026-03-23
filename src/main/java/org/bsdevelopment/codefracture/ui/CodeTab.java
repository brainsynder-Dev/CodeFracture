package org.bsdevelopment.codefracture.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class CodeTab extends Tab {
    private final CodeArea codeArea;
    private final VirtualizedScrollPane<CodeArea> scrollPane;
    private final StackPane contentPane;
    private final VBox codeWrapper;
    private final HBox findBar;
    private final TextField findField;
    private final String entryPath;
    private final boolean isResource;

    private Consumer<String> hyperlinkHandler;
    private Consumer<String> statusCallback;

    private String originalSource = "";
    private List<FoldRegion> foldRegions = new ArrayList<>();
    private int[] dispToOrig = new int[0];

    private Function<String, Boolean> classChecker;

    public CodeTab(String entryPath, boolean isResource) {
        this.entryPath = entryPath;
        this.isResource = isResource;

        String displayName = entryPath.contains("/")
                ? entryPath.substring(entryPath.lastIndexOf('/') + 1)
                : entryPath;
        if (!isResource) {
            if (displayName.contains("$"))
                displayName = displayName.substring(0, displayName.indexOf('$'));
            displayName += ".java";
        }
        setText(displayName);
        setTooltip(new Tooltip(entryPath + (isResource ? "" : ".java")));

        codeArea = new CodeArea();
        codeArea.setEditable(false);
        codeArea.getStyleClass().add("code-area");

        if (!isResource) {
            codeArea.setParagraphGraphicFactory(this::buildGutterNode);
        }

        String extension = entryPath.contains(".")
                ? entryPath.substring(entryPath.lastIndexOf('.') + 1).toLowerCase()
                : "";

        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (!newText.isEmpty()) {
                try {
                    var spans = isResource
                            ? SyntaxHighlighter.computeHighlightingForExtension(newText, extension)
                            : SyntaxHighlighter.computeHighlighting(newText);
                    if (spans != null) codeArea.setStyleSpans(0, spans);
                    if (!isResource && classChecker != null) applyImportColoring(newText);
                } catch (Exception ignored) {
                }
            }
        });

        codeArea.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY
                    && event.isControlDown()
                    && hyperlinkHandler != null) {
                handleCtrlClick(event.getX(), event.getY());
            }
        });

        codeArea.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode() == KeyCode.F) {
                showFindBar();
                e.consume();
                return;
            }
            if (e.isControlDown() && e.isShiftDown()) {
                if (e.getCode() == KeyCode.MINUS) {
                    foldAll();
                    e.consume();
                    return;
                }
                if (e.getCode() == KeyCode.EQUALS) {
                    unfoldAll();
                    e.consume();
                    return;
                }
            }
            if (e.isControlDown() && statusCallback != null)
                statusCallback.accept("Ctrl+Click on an import or class name to navigate");
        });
        codeArea.setOnKeyReleased(e -> {
            if (!e.isControlDown() && statusCallback != null)
                statusCallback.accept("Ready");
        });

        if (!isResource) {
            MenuItem foldAllItem = new MenuItem("Fold All   (Ctrl+Shift+\u2212)");
            MenuItem unfoldAllItem = new MenuItem("Unfold All  (Ctrl+Shift+=)");
            foldAllItem.setOnAction(e -> foldAll());
            unfoldAllItem.setOnAction(e -> unfoldAll());
            codeArea.setContextMenu(new ContextMenu(foldAllItem, new SeparatorMenuItem(), unfoldAllItem));
        }

        scrollPane = new VirtualizedScrollPane<>(codeArea);
        scrollPane.getStyleClass().add("code-scroll-pane");
        scrollPane.setMaxWidth(Double.MAX_VALUE);
        scrollPane.setMaxHeight(Double.MAX_VALUE);

        findField = new TextField();
        findField.setPromptText("Find\u2026");
        findField.getStyleClass().add("find-field");
        HBox.setHgrow(findField, Priority.ALWAYS);

        Button prevBtn = new Button("\u25b2");
        Button nextBtn = new Button("\u25bc");
        Button closeBtn = new Button("\u2715");
        prevBtn.setTooltip(new Tooltip("Previous match  (Shift+Enter)"));
        nextBtn.setTooltip(new Tooltip("Next match  (Enter)"));
        closeBtn.setTooltip(new Tooltip("Close  (Esc)"));
        prevBtn.getStyleClass().add("find-nav-btn");
        nextBtn.getStyleClass().add("find-nav-btn");
        closeBtn.getStyleClass().add("find-close-btn");

        prevBtn.setOnAction(e -> findStep(-1));
        nextBtn.setOnAction(e -> findStep(1));
        closeBtn.setOnAction(e -> hideFindBar());

        findField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                findStep(e.isShiftDown() ? -1 : 1);
                e.consume();
            }
            if (e.getCode() == KeyCode.ESCAPE) {
                hideFindBar();
                e.consume();
            }
        });
        findField.textProperty().addListener((obs, o, n) -> {
            findField.getStyleClass().remove("find-no-match");
            if (!n.isEmpty()) findStep(1);
        });

        findBar = new HBox(4, closeBtn, findField, prevBtn, nextBtn);
        findBar.setAlignment(Pos.CENTER_LEFT);
        findBar.setPadding(new Insets(3, 8, 3, 8));
        findBar.getStyleClass().add("find-bar");
        findBar.setVisible(false);
        findBar.setManaged(false);

        codeArea.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE && findBar.isVisible()) {
                hideFindBar();
                e.consume();
            }
        });

        codeWrapper = new VBox(scrollPane, findBar);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(56, 56);
        Label spinnerLabel = new Label(isResource ? "Loading..." : "Decompiling...");
        VBox spinnerBox = new VBox(8, spinner, spinnerLabel);
        spinnerBox.setAlignment(Pos.CENTER);

        contentPane = new StackPane(spinnerBox);
        contentPane.getStyleClass().add("code-container");
        setContent(contentPane);
    }

    public CodeTab(String entryPath) {
        this(entryPath, false);
    }

    private static String resolveViaImports(String simpleName, String fullText) {
        for (String line : fullText.split("\n")) {
            String t = line.trim();
            if (!t.startsWith("import ") || !t.endsWith(";")) continue;
            String fqn = t.substring(7, t.length() - 1).trim();
            if (fqn.startsWith("static ")) fqn = fqn.substring(7).trim();
            if (fqn.endsWith(".*")) continue;
            int dot = fqn.lastIndexOf('.');
            String simple = dot >= 0 ? fqn.substring(dot + 1) : fqn;
            if (simple.equals(simpleName))
                return fqn.replace('.', '/');
        }
        return null;
    }

    private static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '$';
    }

    public void setHyperlinkHandler(Consumer<String> handler) {
        this.hyperlinkHandler = handler;
    }

    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    public void setCode(String source) {
        Platform.runLater(() -> {
            originalSource = source;
            foldRegions = source.isBlank() ? new ArrayList<>() : FoldDetector.detect(source);
            String[] lines = source.split("\n", -1);
            dispToOrig = buildDispToOrig(lines);
            codeArea.replaceText(buildDisplayText(lines));
            codeArea.moveTo(0);
            codeArea.requestFollowCaret();
            contentPane.getChildren().setAll(codeWrapper);
        });
    }

    public void setError(String message) {
        Platform.runLater(() -> {
            originalSource = "";
            foldRegions.clear();
            dispToOrig = new int[0];
            codeArea.replaceText("// Error:\n// " + message);
            codeArea.moveTo(0);
            codeArea.requestFollowCaret();
            contentPane.getChildren().setAll(codeWrapper);
        });
    }

    public String getEntryPath() {
        return entryPath;
    }

    public boolean isResource() {
        return isResource;
    }

    public CodeArea getCodeArea() {
        return codeArea;
    }

    private void showFindBar() {
        findBar.setVisible(true);
        findBar.setManaged(true);
        findField.requestFocus();
        findField.selectAll();
    }

    private void hideFindBar() {
        findBar.setVisible(false);
        findBar.setManaged(false);
        findField.getStyleClass().remove("find-no-match");
        codeArea.requestFocus();
    }

    /** @param direction +1 for next, -1 for previous */
    private void findStep(int direction) {
        String query = findField.getText();
        if (query.isEmpty()) return;

        String text = codeArea.getText();
        String tLow = text.toLowerCase();
        String qLow = query.toLowerCase();
        int qLen = query.length();

        int anchor = codeArea.getSelection().getStart();
        int focus = codeArea.getSelection().getEnd();

        int pos;
        if (direction > 0) {
            // Search forward from end of current selection to advance past it
            pos = tLow.indexOf(qLow, focus);
            if (pos < 0) pos = tLow.indexOf(qLow, 0); // wrap
        } else {
            // Search backward from start of current selection
            int from = Math.max(0, anchor - 1);
            pos = tLow.lastIndexOf(qLow, from);
            if (pos < 0) pos = tLow.lastIndexOf(qLow); // wrap
        }

        findField.getStyleClass().remove("find-no-match");
        if (pos >= 0) {
            codeArea.selectRange(pos, pos + qLen);
            codeArea.requestFollowCaret();
        } else {
            if (!findField.getStyleClass().contains("find-no-match"))
                findField.getStyleClass().add("find-no-match");
        }
    }

    private void handleCtrlClick(double x, double y) {
        int pos = codeArea.hit(x, y).getInsertionIndex();
        String text = codeArea.getText();

        int lineStart = text.lastIndexOf('\n', pos - 1) + 1;
        int lineEnd = text.indexOf('\n', pos);
        if (lineEnd < 0) lineEnd = text.length();
        String line = text.substring(lineStart, lineEnd).trim();

        if (line.startsWith("import ") && line.endsWith(";")) {
            String fqn = line.substring(7, line.length() - 1).trim();
            if (fqn.startsWith("static ")) fqn = fqn.substring(7).trim();
            if (fqn.endsWith(".*")) fqn = fqn.substring(0, fqn.length() - 2);
            hyperlinkHandler.accept(fqn.replace('.', '/'));
            return;
        }

        int start = pos, end = pos;
        while (start > 0 && isIdentChar(text.charAt(start - 1))) start--;
        while (end < text.length() && isIdentChar(text.charAt(end))) end++;
        if (start >= end) return;

        String word = text.substring(start, end);
        if (word.contains(".")) {
            hyperlinkHandler.accept(word.replace('.', '/'));
            return;
        }

        String resolved = resolveViaImports(word, text);
        if (resolved != null) hyperlinkHandler.accept(resolved);
    }

    public void setClassChecker(Function<String, Boolean> checker) {
        this.classChecker = checker;
        String t = codeArea.getText();
        if (!t.isBlank()) applyImportColoring(t);
    }

    public void foldAll() {
        foldRegions.forEach(r -> r.folded = true);
        applyFolds();
    }

    public void unfoldAll() {
        foldRegions.forEach(r -> r.folded = false);
        applyFolds();
    }

    private void toggleFold(FoldRegion region) {
        region.folded = !region.folded;
        applyFolds();
    }

    // Must run on the FX thread.
    private void applyFolds() {
        if (originalSource.isEmpty()) return;
        String[] lines = originalSource.split("\n", -1);
        dispToOrig = buildDispToOrig(lines);
        String displayText = buildDisplayText(lines);
        int caretPara = codeArea.getCurrentParagraph(); // preserve rough scroll position
        codeArea.replaceText(displayText);
        int targetPara = Math.min(caretPara, codeArea.getParagraphs().size() - 1);
        codeArea.moveTo(targetPara, 0);
        codeArea.requestFollowCaret();
    }

    private String buildDisplayText(String[] lines) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            FoldRegion r = foldedRegionAt(i);
            if (r != null) {
                sb.append(r.placeholder(lines)).append('\n');
                i = r.endLine;
            } else {
                sb.append(lines[i]).append('\n');
                i++;
            }
        }
        // Trim the extra trailing newline when source itself doesn't end with one
        if (!originalSource.endsWith("\n") && sb.length() > 0
                && sb.charAt(sb.length() - 1) == '\n') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    /** Builds the displayed-paragraph → original-line index mapping. */
    private int[] buildDispToOrig(String[] lines) {
        List<Integer> mapping = new ArrayList<>();
        int i = 0;
        while (i < lines.length) {
            FoldRegion r = foldedRegionAt(i);
            if (r != null) {
                mapping.add(i); // placeholder row represents the region's first line
                i = r.endLine;
            } else {
                mapping.add(i);
                i++;
            }
        }
        return mapping.stream().mapToInt(Integer::intValue).toArray();
    }

    private FoldRegion foldedRegionAt(int line) {
        for (FoldRegion r : foldRegions) {
            if (r.startLine == line && r.folded) return r;
        }
        return null;
    }

    private FoldRegion regionAt(int line) {
        for (FoldRegion r : foldRegions) {
            if (r.startLine == line) return r;
        }
        return null;
    }

    private javafx.scene.Node buildGutterNode(int para) {
        int origLine = (dispToOrig != null && para < dispToOrig.length) ? dispToOrig[para] : para;
        FoldRegion region = regionAt(origLine);

        Label arrow = new Label();
        arrow.getStyleClass().add("fold-arrow");
        if (region != null) {
            arrow.setText(region.folded ? "\u25b6" : "\u25bc");
            arrow.setCursor(Cursor.HAND);
            final FoldRegion fr = region;
            arrow.setOnMouseClicked(e -> {
                toggleFold(fr);
                e.consume();
            });
        }

        Label lineNum = new Label(String.format("%4d", origLine + 1));
        lineNum.getStyleClass().add("lineno-num");

        HBox gutter = new HBox(0, arrow, lineNum);
        gutter.getStyleClass().add("fold-gutter");
        gutter.setAlignment(Pos.CENTER_LEFT);
        return gutter;
    }

    private void applyImportColoring(String displayedText) {
        if (classChecker == null) return;
        String[] lines = displayedText.split("\n", -1);
        int pos = 0;
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("import ") && t.endsWith(";")) {
                String fqn = t.substring(7, t.length() - 1).trim();
                if (fqn.startsWith("static ")) fqn = fqn.substring(7).trim();
                if (fqn.endsWith(".*")) fqn = fqn.substring(0, fqn.length() - 2);
                String internalName = fqn.replace('.', '/');
                String styleClass = classChecker.apply(internalName)
                        ? "import-available" : "import-unavailable";
                try {
                    int end = Math.min(pos + line.length(), displayedText.length());
                    codeArea.setStyle(pos, end, Collections.singleton(styleClass));
                } catch (Exception ignored) {
                }
            }
            pos += line.length() + 1; // +1 for '\n'
        }
    }
}
