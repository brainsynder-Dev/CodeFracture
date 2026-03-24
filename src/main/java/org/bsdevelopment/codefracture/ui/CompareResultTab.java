package org.bsdevelopment.codefracture.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.bsdevelopment.codefracture.decompiler.VineflowerDecompiler;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * A tab that compares two JARs and shows only classes that differ.
 *
 * <p>Left side: list of added/removed/changed classes (populated progressively as
 * each class is decompiled and compared in a background thread).
 * Right side: inline unified diff of the selected class.
 */
public class CompareResultTab extends Tab {

    private final ObservableList<CompareEntry> entries = FXCollections.observableArrayList();
    private final CodeArea diffArea;
    private final StackPane rightPane;
    private final Label statusLabel;
    private final ProgressBar progressBar;
    private final VirtualizedScrollPane<CodeArea> scrollPane;
    private final File jarFileA, jarFileB;
    private volatile boolean cancelled = false;
    private VineflowerDecompiler dA;
    private VineflowerDecompiler dB;

    public CompareResultTab(File jarFileA, File jarFileB) {
        this.jarFileA = jarFileA;
        this.jarFileB = jarFileB;

        setText("Diff: " + jarFileA.getName() + " ↔ " + jarFileB.getName());
        setTooltip(new Tooltip(jarFileA.getAbsolutePath() + "  ↔  " + jarFileB.getAbsolutePath()));
        setOnClosed(e -> cancelled = true);

        // ── Left pane ─────────────────────────────────────────────────────────

        Label jarLine = new Label(jarFileA.getName() + "  ↔  " + jarFileB.getName());
        jarLine.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        jarLine.setWrapText(true);

        progressBar = new ProgressBar(-1);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        statusLabel = new Label("Starting comparison…");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");
        statusLabel.setWrapText(true);

        ListView<CompareEntry> listView = new ListView<>(entries);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CompareEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                String prefix = switch (item.type()) {
                    case ADDED -> "[+] ";
                    case REMOVED -> "[-] ";
                    case CHANGED -> "[~] ";
                };
                setText(prefix + item.displayText());
                setStyle(switch (item.type()) {
                    case ADDED -> "-fx-text-fill: #56d364;";
                    case REMOVED -> "-fx-text-fill: #f85149;";
                    case CHANGED -> "";
                });
            }
        });
        VBox.setVgrow(listView, Priority.ALWAYS);

        VBox leftPane = new VBox(4, jarLine, progressBar, statusLabel, listView);
        leftPane.setPadding(new Insets(6));

        // ── Right pane ────────────────────────────────────────────────────────

        diffArea = new CodeArea();
        diffArea.setEditable(false);
        diffArea.getStyleClass().add("code-area");
        diffArea.setParagraphGraphicFactory(LineNumberFactory.get(diffArea));
        diffArea.textProperty().addListener((obs, o, newText) -> {
            if (!newText.isEmpty()) {
                try {
                    var spans = DiffHighlighter.computeHighlighting(newText);
                    if (spans != null) diffArea.setStyleSpans(0, spans);
                } catch (Exception ignored) {
                }
            }
        });

        scrollPane = new VirtualizedScrollPane<>(diffArea);
        scrollPane.getStyleClass().add("code-scroll-pane");
        scrollPane.setMaxWidth(Double.MAX_VALUE);
        scrollPane.setMaxHeight(Double.MAX_VALUE);

        Label placeholder = new Label("Select a file on the left to view its diff");
        placeholder.setStyle("-fx-font-size: 13px; -fx-text-fill: -color-fg-muted;");
        StackPane.setAlignment(placeholder, Pos.CENTER);

        rightPane = new StackPane(placeholder);
        rightPane.getStyleClass().add("code-container");

        // ── Split ─────────────────────────────────────────────────────────────

        SplitPane split = new SplitPane(leftPane, rightPane);
        split.setDividerPositions(0.28);
        SplitPane.setResizableWithParent(leftPane, false);
        setContent(split);

        // ── Selection → load diff ─────────────────────────────────────────────

        listView.getSelectionModel().selectedItemProperty().addListener((obs, o, entry) -> {
            if (entry == null) return;
            ProgressIndicator spinner = new ProgressIndicator();
            spinner.setMaxSize(48, 48);
            rightPane.getChildren().setAll(spinner);
            loadDiff(entry);
        });

        // ── Start background comparison ───────────────────────────────────────

        Thread thread = new Thread(this::runComparison, "compare-" + jarFileA.getName());
        thread.setDaemon(true);
        thread.start();
    }

    private static Set<String> listClasses(File jar) throws Exception {
        Set<String> names = new LinkedHashSet<>();
        try (JarFile jf = new JarFile(jar)) {
            jf.stream()
                    .map(JarEntry::getName)
                    .filter(n -> n.endsWith(".class") && !n.contains("$"))
                    .map(n -> n.substring(0, n.length() - 6))
                    .forEach(names::add);
        }
        return names;
    }

    private void runComparison() {
        try {
            dA = new VineflowerDecompiler(jarFileA);
            dB = new VineflowerDecompiler(jarFileB);

            Set<String> setA = listClasses(jarFileA);
            Set<String> setB = listClasses(jarFileB);

            // Added / removed — no decompilation needed
            for (String cls : setA) {
                if (!setB.contains(cls)) scheduleAdd(cls, EntryType.REMOVED);
            }
            for (String cls : setB) {
                if (!setA.contains(cls)) scheduleAdd(cls, EntryType.ADDED);
            }

            // Common — decompile both and compare
            List<String> common = new ArrayList<>(setA);
            common.retainAll(setB);
            common.sort(Comparator.naturalOrder());

            int total = common.size();
            for (int i = 0; i < total; i++) {
                if (cancelled) break;
                final int done = i;
                Platform.runLater(() -> {
                    statusLabel.setText("Comparing " + done + " / " + total + "…");
                    progressBar.setProgress((double) done / total);
                });
                String cls = common.get(i);
                String srcA = dA.decompile(cls);
                String srcB = dB.decompile(cls);
                if (!srcA.equals(srcB)) scheduleAdd(cls, EntryType.CHANGED);
            }

            final int found = entries.size();
            Platform.runLater(() -> {
                statusLabel.setText(found == 0 ? "No differences found." : found + " difference(s) found.");
                progressBar.setVisible(false);
                progressBar.setManaged(false);
            });

        } catch (Exception ex) {
            Platform.runLater(() -> {
                statusLabel.setText("Error: " + ex.getMessage());
                progressBar.setVisible(false);
                progressBar.setManaged(false);
            });
        }
    }

    // ── Background comparison ─────────────────────────────────────────────────

    private void scheduleAdd(String internalName, EntryType type) {
        String simple = internalName.contains("/")
                ? internalName.substring(internalName.lastIndexOf('/') + 1) : internalName;
        String pkg = internalName.contains("/")
                ? internalName.substring(0, internalName.lastIndexOf('/')) : "";
        CompareEntry entry = new CompareEntry(simple, pkg, internalName, type);
        Platform.runLater(() -> entries.add(entry));
    }

    private void loadDiff(CompareEntry entry) {
        String cls = entry.internalName();
        String labelA = jarFileA.getName() + "/" + cls.replace('/', '.') + ".java";
        String labelB = jarFileB.getName() + "/" + cls.replace('/', '.') + ".java";

        Thread t = new Thread(() -> {
            try {
                String diffText = switch (entry.type()) {
                    case ADDED -> Differ.addedDiff(dB.decompile(cls), labelB);
                    case REMOVED -> Differ.removedDiff(dA.decompile(cls), labelA);
                    case CHANGED -> Differ.unifiedDiff(dA.decompile(cls), dB.decompile(cls), labelA, labelB);
                };
                Platform.runLater(() -> {
                    diffArea.replaceText(diffText);
                    diffArea.moveTo(0);
                    diffArea.requestFollowCaret();
                    rightPane.getChildren().setAll(scrollPane);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    diffArea.replaceText("// Error: " + ex.getMessage());
                    rightPane.getChildren().setAll(scrollPane);
                });
            }
        }, "load-diff-" + entry.simpleName());
        t.setDaemon(true);
        t.start();
    }

    // ── Diff loading ──────────────────────────────────────────────────────────

    enum EntryType {ADDED, REMOVED, CHANGED}

    // ── Helpers ───────────────────────────────────────────────────────────────

    record CompareEntry(String simpleName, String pkg, String internalName, EntryType type) {
        String displayText() {
            String pkgDot = pkg.replace('/', '.');
            return simpleName + ".java" + (pkgDot.isEmpty() ? "" : "  (" + pkgDot + ")");
        }
    }
}
