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
import org.bsdevelopment.codefracture.AppConfig;
import org.bsdevelopment.codefracture.decompiler.VineflowerDecompiler;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * A tab that compares two JARs and shows only classes that differ.
 * Left side: list of added/removed/changed classes populated progressively on a background thread.
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
    private final AtomicInteger generation = new AtomicInteger(0);

    private final List<String> filterPatterns;
    private final boolean filterEnabled;

    private VineflowerDecompiler dA;
    private VineflowerDecompiler dB;

    public CompareResultTab(File jarFileA, File jarFileB) {
        this.jarFileA = jarFileA;
        this.jarFileB = jarFileB;

        filterPatterns = loadPatternsFromConfig();
        filterEnabled  = AppConfig.get(AppConfig.DIFF_FILTER_ENABLED, "false").equals("true");

        setText("Diff: " + jarFileA.getName() + " ↔ " + jarFileB.getName());
        setTooltip(new Tooltip(jarFileA.getAbsolutePath() + "  ↔  " + jarFileB.getAbsolutePath()));
        setOnClosed(e -> generation.incrementAndGet());

        Label jarLine = new Label(jarFileA.getName() + "  ↔  " + jarFileB.getName());
        jarLine.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        jarLine.setWrapText(true);

        progressBar = new ProgressBar(-1);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        statusLabel = new Label("Starting comparison…");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");
        statusLabel.setWrapText(true);

        ListView<CompareEntry> listView = buildListView();
        VBox.setVgrow(listView, Priority.ALWAYS);

        VBox leftPane = new VBox(4, jarLine, progressBar, statusLabel);
        if (filterEnabled && !filterPatterns.isEmpty()) {
            Label filterLabel = new Label("Filter active: " + filterPatterns.size() + " pattern(s)");
            filterLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #e5c890;");
            filterLabel.setTooltip(new Tooltip(String.join("\n", filterPatterns)));
            filterLabel.setWrapText(true);
            leftPane.getChildren().add(filterLabel);
        }
        leftPane.getChildren().add(listView);
        leftPane.setPadding(new Insets(6));

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

        SplitPane split = new SplitPane(leftPane, rightPane);
        split.setDividerPositions(0.28);
        SplitPane.setResizableWithParent(leftPane, false);
        setContent(split);

        startComparison();
    }

    private ListView<CompareEntry> buildListView() {
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
                    case ADDED   -> "[+] ";
                    case REMOVED -> "[-] ";
                    case CHANGED -> "[~] ";
                };
                setText(prefix + item.displayText());
                setStyle(switch (item.type()) {
                    case ADDED   -> "-fx-text-fill: #56d364;";
                    case REMOVED -> "-fx-text-fill: #f85149;";
                    case CHANGED -> "";
                });
            }
        });
        listView.getSelectionModel().selectedItemProperty().addListener((obs, o, entry) -> {
            if (entry == null) return;
            ProgressIndicator spinner = new ProgressIndicator();
            spinner.setMaxSize(48, 48);
            rightPane.getChildren().setAll(spinner);
            loadDiff(entry);
        });
        return listView;
    }

    private static List<String> loadPatternsFromConfig() {
        String raw = AppConfig.get(AppConfig.DIFF_FILTER_PATTERNS, "").trim();
        if (raw.isEmpty()) return new ArrayList<>();
        return Arrays.stream(raw.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // Patterns with a slash are prefix-matched against the full internal name (package filter);
    // patterns without a slash are prefix-matched against just the simple class name.
    private boolean matchesFilter(String internalName) {
        String slashName = internalName.replace('.', '/');
        String simpleName = slashName.contains("/")
                ? slashName.substring(slashName.lastIndexOf('/') + 1) : slashName;
        for (String raw : filterPatterns) {
            String pattern = raw.trim().replace('.', '/');
            if (pattern.isEmpty()) continue;
            if (pattern.contains("/")) {
                if (slashName.startsWith(pattern)) return true;
            } else {
                if (simpleName.startsWith(pattern)) return true;
            }
        }
        return false;
    }

    private void startComparison() {
        int gen = generation.get();
        Thread thread = new Thread(() -> runComparison(gen), "compare-" + jarFileA.getName());
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

    private void runComparison(int gen) {
        try {
            if (dA == null) dA = new VineflowerDecompiler(jarFileA);
            if (dB == null) dB = new VineflowerDecompiler(jarFileB);

            Set<String> setA = listClasses(jarFileA);
            Set<String> setB = listClasses(jarFileB);

            if (filterEnabled && !filterPatterns.isEmpty()) {
                setA.removeIf(cls -> !matchesFilter(cls));
                setB.removeIf(cls -> !matchesFilter(cls));
            }

            for (String cls : setA) {
                if (generation.get() != gen) return;
                if (!setB.contains(cls)) scheduleAdd(cls, EntryType.REMOVED, gen);
            }
            for (String cls : setB) {
                if (generation.get() != gen) return;
                if (!setA.contains(cls)) scheduleAdd(cls, EntryType.ADDED, gen);
            }

            List<String> common = new ArrayList<>(setA);
            common.retainAll(setB);
            common.sort(Comparator.naturalOrder());

            int total = common.size();
            for (int i = 0; i < total; i++) {
                if (generation.get() != gen) return;
                final int done = i;
                Platform.runLater(() -> {
                    statusLabel.setText("Comparing " + done + " / " + total + "…");
                    progressBar.setProgress((double) done / total);
                });
                String cls = common.get(i);
                String srcA = dA.decompile(cls);
                String srcB = dB.decompile(cls);
                if (!srcA.equals(srcB)) scheduleAdd(cls, EntryType.CHANGED, gen);
            }

            if (generation.get() != gen) return;
            final int found = entries.size();
            Platform.runLater(() -> {
                statusLabel.setText(found == 0 ? "No differences found." : found + " difference(s) found.");
                progressBar.setVisible(false);
                progressBar.setManaged(false);
            });

        } catch (Exception ex) {
            if (generation.get() != gen) return;
            Platform.runLater(() -> {
                statusLabel.setText("Error: " + ex.getMessage());
                progressBar.setVisible(false);
                progressBar.setManaged(false);
            });
        }
    }

    private void scheduleAdd(String internalName, EntryType type, int gen) {
        String simple = internalName.contains("/")
                ? internalName.substring(internalName.lastIndexOf('/') + 1) : internalName;
        String pkg = internalName.contains("/")
                ? internalName.substring(0, internalName.lastIndexOf('/')) : "";
        CompareEntry entry = new CompareEntry(simple, pkg, internalName, type);
        Platform.runLater(() -> {
            if (generation.get() == gen) entries.add(entry);
        });
    }

    private void loadDiff(CompareEntry entry) {
        String cls = entry.internalName();
        String labelA = jarFileA.getName() + "/" + cls.replace('/', '.') + ".java";
        String labelB = jarFileB.getName() + "/" + cls.replace('/', '.') + ".java";

        Thread t = new Thread(() -> {
            try {
                String diffText = switch (entry.type()) {
                    case ADDED   -> Differ.addedDiff(dB.decompile(cls), labelB);
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

    enum EntryType {ADDED, REMOVED, CHANGED}

    record CompareEntry(String simpleName, String pkg, String internalName, EntryType type) {
        String displayText() {
            String pkgDot = pkg.replace('/', '.');
            return simpleName + ".java" + (pkgDot.isEmpty() ? "" : "  (" + pkgDot + ")");
        }
    }
}
