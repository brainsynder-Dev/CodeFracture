package org.bsdevelopment.codefracture.controller;

import atlantafx.base.controls.Spacer;
import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.bsdevelopment.codefracture.AppConfig;
import org.bsdevelopment.codefracture.BuildInfo;
import org.bsdevelopment.codefracture.decompiler.VineflowerDecompiler;
import org.bsdevelopment.codefracture.model.JarNode;
import org.bsdevelopment.codefracture.ui.CodeTab;
import org.bsdevelopment.codefracture.ui.DiffTab;
import org.bsdevelopment.codefracture.ui.Differ;
import org.bsdevelopment.codefracture.utils.PasteClient;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignE;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignI;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;
import org.kordamp.ikonli.materialdesign2.MaterialDesignU;
import org.kordamp.ikonli.materialdesign2.MaterialDesignZ;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class MainController {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "properties", "xml", "yaml", "yml", "json", "toml",
            "cfg", "ini", "html", "htm", "css", "js", "gradle", "kts", "groovy",
            "sh", "bat", "cmd", "sql", "conf", "config", "mf", "log", "gitignore",
            "license", "notice", "readme"
    );

    private final Stage primaryStage;
    private final BorderPane mainPane;
    private final StackPane rootContainer;
    private final StackPane dragOverlay;
    private final TreeView<JarNode> treeView;
    private final TreeItem<JarNode> virtualRoot;
    private final TabPane tabPane;
    private final Label statusLabel;
    private final Label coordsLabel;
    private final ProgressBar progressBar;

    private final Map<String, VineflowerDecompiler> decompilers = new LinkedHashMap<>();
    private final Map<String, VineflowerDecompiler> compareDecompilers = new ConcurrentHashMap<>();
    private final Map<String, Tab> openTabs = new HashMap<>();
    private final Map<String, TreeItem<JarNode>> jarRoots = new LinkedHashMap<>();
    private final List<JarNode> searchableNodes = new ArrayList<>();
    private ListView<JarNode> searchResultsView;
    private TextField treeSearchField;

    public MainController(Stage primaryStage) {
        this.primaryStage = primaryStage;

        mainPane = new BorderPane();
        mainPane.getStyleClass().add("main-root");

        mainPane.setTop(buildTopBar());

        virtualRoot = new TreeItem<>();
        treeView = buildTreeView();
        tabPane = buildTabPane();

        SplitPane splitPane = new SplitPane(buildLeftPane(), tabPane);
        splitPane.setDividerPositions(0.25);
        mainPane.setCenter(splitPane);

        statusLabel = new Label("Ready — open or drop a JAR to begin");
        coordsLabel = new Label("");
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(160);
        progressBar.setVisible(false);
        progressBar.setManaged(false);
        HBox statusBar = new HBox(
                new FontIcon(MaterialDesignI.INFORMATION_OUTLINE),
                statusLabel,
                new Spacer(),
                progressBar,
                coordsLabel
        );
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setSpacing(6);
        statusBar.setPadding(new Insets(4, 10, 4, 10));
        mainPane.setBottom(statusBar);

        dragOverlay = buildDragOverlay();
        dragOverlay.setVisible(false);
        dragOverlay.setMouseTransparent(true);

        rootContainer = new StackPane(mainPane, dragOverlay);
        setupDragAndDrop();
    }

    private HBox buildTopBar() {
        MenuBar menuBar = buildMenuBar();
        HBox.setHgrow(menuBar, Priority.NEVER);

        Button openBtn = new Button("Open JAR");
        openBtn.setGraphic(new FontIcon(MaterialDesignF.FOLDER_OPEN));
        openBtn.getStyleClass().add("accent");
        openBtn.setOnAction(e -> openJarDialog());

        Button closeBtn = new Button("Close JAR");
        closeBtn.setGraphic(new FontIcon(MaterialDesignC.CLOSE));
        closeBtn.setOnAction(e -> closeSelectedJar());

        Button exportBtn = new Button("Export Source");
        exportBtn.setGraphic(new FontIcon(MaterialDesignE.EXPORT));
        exportBtn.setOnAction(e -> exportCurrentTab());

        HBox buttons = new HBox(4, openBtn, closeBtn,
                new Separator(Orientation.VERTICAL), exportBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(2, 8, 2, 8));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(menuBar, spacer, buttons);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("menu-bar");
        return topBar;
    }

    private StackPane buildDragOverlay() {
        FontIcon icon = new FontIcon(MaterialDesignF.FILE_IMPORT);
        icon.setIconSize(64);
        icon.setStyle("-fx-icon-color: -color-accent-emphasis;");

        Label label = new Label("Drop JAR files to load");
        label.setStyle(
                "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: -color-accent-emphasis;"
        );

        VBox content = new VBox(14, icon, label);
        content.setAlignment(Pos.CENTER);

        StackPane overlay = new StackPane(content);
        overlay.setStyle(
                "-fx-background-color: -color-bg-overlay;" +
                        "-fx-border-color: -color-accent-emphasis;" +
                        "-fx-border-width: 3;" +
                        "-fx-border-style: segments(18, 9);"
        );
        return overlay;
    }

    private void setupDragAndDrop() {
        rootContainer.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles() && db.getFiles().stream().anyMatch(this::isAcceptableFile)) {
                event.acceptTransferModes(TransferMode.COPY);
                dragOverlay.setVisible(true);
            }
            event.consume();
        });

        rootContainer.setOnDragExited(event -> {
            dragOverlay.setVisible(false);
            event.consume();
        });

        rootContainer.setOnDragDropped(event -> {
            dragOverlay.setVisible(false);
            Dragboard db = event.getDragboard();
            boolean ok = false;
            if (db.hasFiles()) {
                db.getFiles().stream()
                        .filter(this::isAcceptableFile)
                        .forEach(this::loadJar);
                ok = true;
            }
            event.setDropCompleted(ok);
            event.consume();
        });
    }

    private boolean isAcceptableFile(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".jar") || n.endsWith(".class");
    }

    private MenuBar buildMenuBar() {
        MenuBar menuBar = new MenuBar();

        Menu fileMenu = new Menu("_File");

        MenuItem openJar = new MenuItem("Open JAR / Class…");
        openJar.setGraphic(new FontIcon(MaterialDesignF.FOLDER_OPEN));
        openJar.setOnAction(e -> openJarDialog());

        MenuItem closeSelected = new MenuItem("Close Selected JAR");
        closeSelected.setOnAction(e -> closeSelectedJar());

        MenuItem closeAll = new MenuItem("Close All JARs");
        closeAll.setOnAction(e -> closeAllJars());

        MenuItem compareJars = new MenuItem("Compare JARs…");
        compareJars.setGraphic(new FontIcon(MaterialDesignD.DELTA));
        compareJars.setOnAction(e -> showJarPickerAndCompare());

        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(e -> Platform.exit());

        fileMenu.getItems().addAll(openJar, closeSelected, closeAll,
                new SeparatorMenuItem(), compareJars,
                new SeparatorMenuItem(), exit);

        Menu viewMenu = new Menu("_View");
        ToggleGroup themeGroup = new ToggleGroup();
        String savedTheme = AppConfig.get(AppConfig.THEME, "Nord Dark");
        for (Theme theme : List.of(new Dracula(), new PrimerDark(), new PrimerLight(),
                new NordDark(), new NordLight(),
                new CupertinoDark(), new CupertinoLight())) {
            RadioMenuItem item = new RadioMenuItem(theme.getName());
            item.setToggleGroup(themeGroup);
            if (theme.getName().equals(savedTheme)) item.setSelected(true);
            item.setOnAction(e -> {
                Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
                AppConfig.set(AppConfig.THEME, theme.getName());
            });
            viewMenu.getItems().add(item);
        }

        Menu settingsMenu = new Menu("_Settings");

        CheckMenuItem skipSplashItem = new CheckMenuItem("Skip Splash Screen on Startup");
        skipSplashItem.setSelected(
                Boolean.parseBoolean(AppConfig.get(AppConfig.SKIP_SPLASH, "false")));
        skipSplashItem.setOnAction(
                e -> AppConfig.set(AppConfig.SKIP_SPLASH, String.valueOf(skipSplashItem.isSelected())));

        CheckMenuItem showCommentsItem = new CheckMenuItem("Show Decompiler Comments");
        showCommentsItem.setSelected(
                Boolean.parseBoolean(AppConfig.get(AppConfig.SHOW_COMMENTS, "false")));
        showCommentsItem.setOnAction(e -> {
            boolean enabled = showCommentsItem.isSelected();
            AppConfig.set(AppConfig.SHOW_COMMENTS, String.valueOf(enabled));
            decompilers.values().forEach(d -> d.setShowComments(enabled));
            new ArrayList<>(openTabs.entrySet()).forEach(entry -> {
                if (!(entry.getValue() instanceof CodeTab ct) || ct.isResource()) return;
                String key = entry.getKey();
                String jarPath = key.contains("!") ? key.substring(0, key.lastIndexOf('!')) : null;
                String cls = key.contains("!") ? key.substring(key.lastIndexOf('!') + 1) : null;
                if (jarPath == null || cls == null) return;
                VineflowerDecompiler d = decompilers.get(jarPath);
                if (d == null) return;
                setStatus("Re-decompiling " + ct.getText() + "…");
                Task<String> task = new Task<>() {
                    @Override
                    protected String call() throws Exception {
                        return d.decompile(cls);
                    }
                };
                task.setOnSucceeded(ev -> {
                    ct.setCode(task.getValue());
                    setStatus("Ready");
                });
                task.setOnFailed(ev -> setStatus("Ready"));
                Thread t = new Thread(task, "redecompile-" + ct.getText());
                t.setDaemon(true);
                t.start();
            });
        });

        settingsMenu.getItems().addAll(skipSplashItem, showCommentsItem);

        Menu helpMenu = new Menu("_Help");
        MenuItem about = new MenuItem("About CodeFracture");
        about.setOnAction(e -> showAbout());
        helpMenu.getItems().add(about);

        menuBar.getMenus().addAll(fileMenu, viewMenu, settingsMenu, helpMenu);
        return menuBar;
    }

    private TreeView<JarNode> buildTreeView() {
        TreeView<JarNode> tv = new TreeView<>(virtualRoot);
        tv.setShowRoot(false);
        tv.setPrefWidth(290);
        tv.getStyleClass().add("jar-tree");

        tv.setCellFactory(tree -> new TreeCell<>() {
            {
                setOnContextMenuRequested(event -> {
                    JarNode item = getItem();
                    if (item == null) return;
                    ContextMenu menu = new ContextMenu();

                    if (item.getType() == JarNode.Type.ROOT) {
                        MenuItem closeItem = new MenuItem("Close JAR");
                        closeItem.setGraphic(new FontIcon(MaterialDesignC.CLOSE));
                        closeItem.setOnAction(e -> closeJar(getTreeItem()));
                        menu.getItems().add(closeItem);
                    }

                    if (isOpenableType(item.getType()) && item.getJarFile() != null) {
                        MenuItem openItem = new MenuItem("Open");
                        openItem.setOnAction(e -> openNode(item));
                        menu.getItems().add(openItem);
                    }

                    if (item.getComparisonRootPath() != null && isOpenableType(item.getType())) {
                        MenuItem exportItem = new MenuItem("Export Diff to Paste");
                        exportItem.setGraphic(new FontIcon(MaterialDesignU.UPLOAD));
                        exportItem.setOnAction(e -> exportDiffToPaste(item));
                        menu.getItems().add(exportItem);
                    }

                    if (!menu.getItems().isEmpty())
                        menu.show(this, event.getScreenX(), event.getScreenY());
                });
            }

            @Override
            protected void updateItem(JarNode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle(null);
                    return;
                }
                setText(item.getName());
                setGraphic(iconFor(item));
                JarNode.DiffStatus ds = item.getDiffStatus();
                if (ds == JarNode.DiffStatus.ADDED) setStyle("-fx-text-fill: #56d364;");
                else if (ds == JarNode.DiffStatus.REMOVED) setStyle("-fx-text-fill: #f85149;");
                else setStyle(null);
            }
        });

        tv.getSelectionModel().selectedItemProperty().addListener((obs, old, newItem) -> {
            if (newItem == null || newItem.getValue() == null) return;
            openNode(newItem.getValue());
        });

        return tv;
    }

    private boolean isOpenableType(JarNode.Type t) {
        return t == JarNode.Type.CLASS || t == JarNode.Type.INTERFACE
                || t == JarNode.Type.ENUM || t == JarNode.Type.ANNOTATION
                || t == JarNode.Type.RESOURCE;
    }

    private FontIcon iconFor(JarNode node) {
        return switch (node.getType()) {
            case ROOT -> new FontIcon(MaterialDesignZ.ZIP_BOX);
            case PACKAGE -> new FontIcon(MaterialDesignF.FOLDER);
            case CLASS -> new FontIcon(MaterialDesignL.LANGUAGE_JAVA);
            case INTERFACE -> new FontIcon(MaterialDesignC.CODE_BRACES);
            case ENUM -> new FontIcon(MaterialDesignF.FORMAT_LIST_BULLETED);
            case ANNOTATION -> new FontIcon(MaterialDesignA.AT);
            case RESOURCE -> new FontIcon(MaterialDesignF.FILE_OUTLINE);
        };
    }

    private TabPane buildTabPane() {
        TabPane tp = new TabPane();
        tp.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tp.getStyleClass().add("code-tabs");
        return tp;
    }

    private void openJarDialog() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open JAR or Class File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("JAR Files", "*.jar"),
                new FileChooser.ExtensionFilter("Class Files", "*.class"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        chooser.setInitialDirectory(AppConfig.getLastOpenedDir());

        List<File> files = chooser.showOpenMultipleDialog(primaryStage);
        if (files == null || files.isEmpty()) return;

        AppConfig.setLastOpenedDir(files.get(0));
        files.forEach(this::loadJar);
    }

    private void loadJar(File file) {
        String key = file.getAbsolutePath();
        if (decompilers.containsKey(key)) {
            setStatus("Already loaded: " + file.getName());
            return;
        }

        VineflowerDecompiler d = new VineflowerDecompiler(file);
        decompilers.put(key, d);
        setStatus("Loading " + file.getName() + "…");

        Task<TreeItem<JarNode>> task = new Task<>() {
            @Override
            protected TreeItem<JarNode> call() throws Exception {
                return buildJarTree(file);
            }
        };
        task.setOnSucceeded(e -> {
            TreeItem<JarNode> jarRoot = task.getValue();
            jarRoot.setExpanded(true);
            virtualRoot.getChildren().add(jarRoot);
            jarRoots.put(key, jarRoot);
            collectSearchableNodes(jarRoot);
            updateTitle();
            setStatus("Loaded: " + file.getName());
        });
        task.setOnFailed(e -> {
            decompilers.remove(key);
            setStatus("Error: " + task.getException().getMessage());
            showError("Failed to load JAR", task.getException().getMessage());
        });

        Thread t = new Thread(task, "jar-loader");
        t.setDaemon(true);
        t.start();
    }

    private TreeItem<JarNode> buildJarTree(File jarFile) throws Exception {
        JarNode rootNode = new JarNode(jarFile.getName(), jarFile.getAbsolutePath(),
                JarNode.Type.ROOT, jarFile);
        TreeItem<JarNode> root = new TreeItem<>(rootNode);

        Map<String, TreeItem<JarNode>> packageMap = new TreeMap<>();

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()) continue;

                if (name.endsWith(".class")) {
                    String internal = name.substring(0, name.length() - 6);
                    if (internal.contains("$")) continue;

                    String pkgPath = internal.contains("/")
                            ? internal.substring(0, internal.lastIndexOf('/')) : "";
                    String simple = internal.contains("/")
                            ? internal.substring(internal.lastIndexOf('/') + 1) : internal;

                    TreeItem<JarNode> pkg = ensurePackage(root, packageMap, pkgPath, jarFile);
                    pkg.getChildren().add(new TreeItem<>(
                            new JarNode(simple, internal, guessClassType(simple), jarFile)));
                } else {
                    String pkgPath = name.contains("/")
                            ? name.substring(0, name.lastIndexOf('/')) : "";
                    String simple = name.contains("/")
                            ? name.substring(name.lastIndexOf('/') + 1) : name;
                    if (simple.isEmpty()) continue;

                    TreeItem<JarNode> pkg = ensurePackage(root, packageMap, pkgPath, jarFile);
                    pkg.getChildren().add(new TreeItem<>(
                            new JarNode(simple, name, JarNode.Type.RESOURCE, jarFile)));
                }
            }
        }

        sortTree(root);
        compactPackages(root);
        return root;
    }

    private TreeItem<JarNode> ensurePackage(TreeItem<JarNode> root,
                                            Map<String, TreeItem<JarNode>> map,
                                            String pkgPath, File jarFile) {
        if (pkgPath.isEmpty()) return root;
        if (map.containsKey(pkgPath)) return map.get(pkgPath);

        String parentPath = pkgPath.contains("/")
                ? pkgPath.substring(0, pkgPath.lastIndexOf('/')) : "";
        TreeItem<JarNode> parent = ensurePackage(root, map, parentPath, jarFile);

        String simple = pkgPath.contains("/")
                ? pkgPath.substring(pkgPath.lastIndexOf('/') + 1) : pkgPath;

        JarNode pkg = new JarNode(simple, pkgPath, JarNode.Type.PACKAGE, jarFile);
        TreeItem<JarNode> item = new TreeItem<>(pkg);
        parent.getChildren().add(item);
        map.put(pkgPath, item);
        return item;
    }

    private void compactPackages(TreeItem<JarNode> item) {
        new ArrayList<>(item.getChildren()).forEach(this::compactPackages);

        JarNode node = item.getValue();
        if (node == null || node.getType() != JarNode.Type.PACKAGE) return;

        List<TreeItem<JarNode>> children = item.getChildren();
        if (children.size() == 1) {
            TreeItem<JarNode> only = children.get(0);
            JarNode onlyNode = only.getValue();
            if (onlyNode.getType() == JarNode.Type.PACKAGE) {
                String merged = node.getName() + "." + onlyNode.getName();
                item.setValue(new JarNode(merged, onlyNode.getFullPath(),
                        JarNode.Type.PACKAGE, node.getJarFile()));
                item.getChildren().setAll(only.getChildren());
            }
        }
    }

    private JarNode.Type guessClassType(String name) {
        if (name.startsWith("I") && name.length() > 1 && Character.isUpperCase(name.charAt(1)))
            return JarNode.Type.INTERFACE;
        return JarNode.Type.CLASS;
    }

    private void sortTree(TreeItem<JarNode> item) {
        item.getChildren().sort((a, b) -> {
            boolean aPkg = a.getValue().getType() == JarNode.Type.PACKAGE;
            boolean bPkg = b.getValue().getType() == JarNode.Type.PACKAGE;
            if (aPkg && !bPkg) return -1;
            if (!aPkg && bPkg) return 1;
            return a.getValue().getName().compareToIgnoreCase(b.getValue().getName());
        });
        item.getChildren().forEach(this::sortTree);
    }

    private void openNode(JarNode node) {
        if (node.getComparisonRootPath() != null) {
            openComparisonDiffTab(node);
            return;
        }
        switch (node.getType()) {
            case CLASS, INTERFACE, ENUM, ANNOTATION -> openClassTab(node);
            case RESOURCE -> openResourceTab(node);
            default -> {
            }
        }
    }

    private void openClassTab(JarNode node) {
        if (node.getJarFile() == null) return;
        String tabKey = tabKey(node);

        if (openTabs.containsKey(tabKey)) {
            tabPane.getSelectionModel().select(openTabs.get(tabKey));
            return;
        }

        VineflowerDecompiler d = decompilers.get(node.getJarFile().getAbsolutePath());
        if (d == null) return;

        CodeTab tab = new CodeTab(node.getFullPath(), false);
        tab.setHyperlinkHandler(this::navigateToClass);
        tab.setStatusCallback(this::setStatus);
        tab.setClassChecker(internalName ->
                decompilers.values().stream().anyMatch(dec -> dec.hasClass(internalName)));
        tab.setOnClosed(e -> openTabs.remove(tabKey));
        openTabs.put(tabKey, tab);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

        setStatus("Decompiling " + node.getName() + "…");
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return d.decompile(node.getFullPath());
            }
        };
        task.setOnSucceeded(e -> {
            tab.setCode(task.getValue());
            setStatus("Decompiled: " + node.getName());
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            tab.setError(ex != null ? ex.getMessage() : "Unknown error");
            setStatus("Decompilation failed: " + node.getName());
        });
        Thread t = new Thread(task, "decompile-" + node.getName());
        t.setDaemon(true);
        t.start();
    }

    private void openResourceTab(JarNode node) {
        if (node.getJarFile() == null) return;
        String tabKey = tabKey(node);

        if (openTabs.containsKey(tabKey)) {
            tabPane.getSelectionModel().select(openTabs.get(tabKey));
            return;
        }

        CodeTab tab = new CodeTab(node.getFullPath(), true);
        tab.setStatusCallback(this::setStatus);
        tab.setOnClosed(e -> openTabs.remove(tabKey));
        openTabs.put(tabKey, tab);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

        setStatus("Loading " + node.getName() + "…");
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return readResourceEntry(node.getJarFile(), node.getFullPath());
            }
        };
        task.setOnSucceeded(e -> {
            tab.setCode(task.getValue());
            setStatus("Loaded: " + node.getName());
        });
        task.setOnFailed(e -> {
            tab.setError("Cannot display: " +
                    (task.getException() != null ? task.getException().getMessage() : "binary content"));
            setStatus("Failed: " + node.getName());
        });
        Thread t = new Thread(task, "resource-" + node.getName());
        t.setDaemon(true);
        t.start();
    }

    private String readResourceEntry(File jarFile, String entryPath) throws Exception {
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry(entryPath);
            if (entry == null) throw new IOException("Entry not found: " + entryPath);
            byte[] bytes;
            try (InputStream is = jar.getInputStream(entry)) {
                bytes = is.readAllBytes();
            }
            if (!isTextResource(entryPath))
                return "// Binary file — cannot display as text\n// Size: " + bytes.length + " bytes";
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private boolean isTextResource(String name) {
        String lower = name.toLowerCase();
        int dot = lower.lastIndexOf('.');
        if (dot < 0) {
            String base = lower.contains("/") ? lower.substring(lower.lastIndexOf('/') + 1) : lower;
            return TEXT_EXTENSIONS.contains(base);
        }
        return TEXT_EXTENSIONS.contains(lower.substring(dot + 1));
    }

    private void navigateToClass(String internalName) {
        for (VineflowerDecompiler d : decompilers.values()) {
            if (d.hasClass(internalName)) {
                File jar = d.getJarFile();
                String simple = internalName.contains("/")
                        ? internalName.substring(internalName.lastIndexOf('/') + 1) : internalName;
                JarNode node = new JarNode(simple, internalName, JarNode.Type.CLASS, jar);
                Platform.runLater(() -> openClassTab(node));
                return;
            }
        }
        setStatus("Class not found: " + internalName.replace('/', '.'));
    }

    private void closeSelectedJar() {
        TreeItem<JarNode> sel = treeView.getSelectionModel().getSelectedItem();
        if (sel == null) {
            setStatus("Select a JAR in the tree first.");
            return;
        }
        TreeItem<JarNode> jarRoot = findJarRoot(sel);
        if (jarRoot != null) closeJar(jarRoot);
    }

    private void closeAllJars() {
        new ArrayList<>(virtualRoot.getChildren()).forEach(this::closeJar);
    }

    private void closeJar(TreeItem<JarNode> jarItem) {
        JarNode node = jarItem.getValue();
        if (node == null || node.getType() != JarNode.Type.ROOT) return;

        String jarPath = node.getFullPath();
        if (jarPath.startsWith("compare:")) {
            String parts = jarPath.substring("compare:".length());
            int sep = parts.indexOf(":::");
            if (sep >= 0) {
                compareDecompilers.remove(parts.substring(0, sep));
                compareDecompilers.remove(parts.substring(sep + 3));
            }
        } else {
            VineflowerDecompiler d = decompilers.remove(jarPath);
            if (d != null) d.clearCache();
        }

        String prefix = jarPath + "!";
        openTabs.entrySet().removeIf(e -> {
            if (e.getKey().startsWith(prefix)) {
                tabPane.getTabs().remove(e.getValue());
                return true;
            }
            return false;
        });

        virtualRoot.getChildren().remove(jarItem);
        jarRoots.remove(jarPath);
        if (jarPath.startsWith("compare:")) {
            searchableNodes.removeIf(n -> jarPath.equals(n.getComparisonRootPath()));
        } else {
            searchableNodes.removeIf(n -> n.getJarFile() != null
                    && n.getJarFile().getAbsolutePath().equals(jarPath));
        }
        if (treeSearchField != null && !treeSearchField.getText().isBlank()) {
            String q = treeSearchField.getText();
            treeSearchField.setText("");
            treeSearchField.setText(q);
        }
        setStatus("Closed: " + node.getName());
        updateTitle();
    }

    private TreeItem<JarNode> findJarRoot(TreeItem<JarNode> item) {
        if (item == null) return null;
        JarNode v = item.getValue();
        if (v != null && v.getType() == JarNode.Type.ROOT) return item;
        return findJarRoot(item.getParent());
    }

    private void exportCurrentTab() {
        Tab selected = tabPane.getSelectionModel().getSelectedItem();

        String content;
        String defaultName;
        boolean isDiff;

        if (selected instanceof CodeTab ct) {
            content = ct.getCodeArea().getText();
            defaultName = ct.getText().endsWith(".java") ? ct.getText() : ct.getText() + ".java";
            isDiff = false;
        } else if (selected instanceof DiffTab dt) {
            content = dt.getDiffText();
            defaultName = dt.getText().replace(".java", ".diff");
            isDiff = true;
        } else {
            showError("Export", "No exportable tab is selected.");
            return;
        }

        if (content.isBlank()) {
            showError("Export", "Tab has no content yet.");
            return;
        }

        ButtonType saveBtn = new ButtonType("Save as File");
        ButtonType pasteBtn = new ButtonType("Upload to Paste");
        Alert choice = new Alert(Alert.AlertType.NONE,
                "Choose how to export \"" + selected.getText() + "\":");
        choice.initOwner(primaryStage);
        choice.setTitle("Export");
        choice.setHeaderText(null);
        choice.getButtonTypes().setAll(saveBtn, pasteBtn, ButtonType.CANCEL);
        choice.showAndWait().ifPresent(bt -> {
            if (bt == saveBtn) {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Export");
                chooser.setInitialFileName(defaultName);
                chooser.setInitialDirectory(AppConfig.getLastOpenedDir());
                if (isDiff) {
                    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Diff Files", "*.diff"));
                } else if (selected instanceof CodeTab ct && !ct.isResource()) {
                    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java Files", "*.java"));
                }
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*.*"));
                File dest = chooser.showSaveDialog(primaryStage);
                if (dest == null) return;
                try {
                    java.nio.file.Files.writeString(dest.toPath(), content);
                    AppConfig.setLastOpenedDir(dest);
                    setStatus("Exported: " + dest.getName());
                } catch (Exception e) {
                    showError("Export Failed", e.getMessage());
                }
            } else if (bt == pasteBtn) {
                String lang = isDiff ? "diff" : "java";
                uploadToPaste(content, selected.getText(), lang);
            }
        });
    }

    private String tabKey(JarNode node) {
        return node.getJarFile().getAbsolutePath() + "!" + node.getFullPath();
    }

    private void updateTitle() {
        int n = decompilers.size();
        if (n == 0) {
            primaryStage.setTitle("CodeFracture — Java Decompiler");
        } else if (n == 1) {
            primaryStage.setTitle("CodeFracture — " +
                    decompilers.values().iterator().next().getJarFile().getName());
        } else {
            primaryStage.setTitle("CodeFracture — " + n + " JARs loaded");
        }
    }

    private void setStatus(String msg) {
        Platform.runLater(() -> statusLabel.setText(msg));
    }

    private void applyAppIcons(Dialog<?> dialog) {
        dialog.initOwner(primaryStage);
    }

    private void showAbout() {
        Dialog<ButtonType> dialog = new Dialog<>();
        applyAppIcons(dialog);
        dialog.setTitle("About CodeFracture");
        dialog.setHeaderText("CodeFracture — Java Decompiler  v" + BuildInfo.VERSION);

        Label builtWith = new Label(
                "Built with:\n" +
                        "  \u2022 JavaFX 21 + AtlantaFX 2.1.0\n" +
                        "  \u2022 Vineflower (Fernflower fork)\n" +
                        "  \u2022 RichTextFX"
        );
        Label hint = new Label(
                "Ctrl+Click on any class name or import to navigate.\n" +
                        "Drag & drop JAR files directly onto the window."
        );

        Button configBtn = new Button("Open Config Folder");
        configBtn.setGraphic(new FontIcon(MaterialDesignF.FOLDER));
        configBtn.setOnAction(e -> {
            try {
                Desktop.getDesktop().open(AppConfig.getConfigDir().toFile());
            } catch (Exception ex) { /* ignore */ }
        });

        VBox content = new VBox(12, builtWith, new Separator(), hint, configBtn);
        content.setPrefWidth(400);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(primaryStage);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private VBox buildLeftPane() {
        treeSearchField = new TextField();
        treeSearchField.setPromptText("Search classes…");
        treeSearchField.getStyleClass().add("tree-search-field");
        treeSearchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) treeSearchField.clear();
        });
        FontIcon searchIcon = new FontIcon(MaterialDesignM.MAGNIFY);
        searchIcon.getStyleClass().add("tree-search-icon");
        HBox searchBar = new HBox(4, searchIcon, treeSearchField);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.setPadding(new Insets(4, 6, 4, 6));
        searchBar.getStyleClass().add("tree-search-bar");
        HBox.setHgrow(treeSearchField, Priority.ALWAYS);

        searchResultsView = new ListView<>();
        searchResultsView.getStyleClass().add("jar-tree");
        searchResultsView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(JarNode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                String fp = item.getFullPath();
                String pkg = fp.contains("/") ? fp.substring(0, fp.lastIndexOf('/')).replace('/', '.') : "";
                String jar = item.getJarFile() != null ? "  [" + item.getJarFile().getName() + "]" : "";
                setText(item.getName() + (pkg.isEmpty() ? "" : "  " + pkg) + jar);
                setGraphic(iconFor(item));
            }
        });
        searchResultsView.setOnMouseClicked(e -> {
            JarNode sel = searchResultsView.getSelectionModel().getSelectedItem();
            if (sel != null) openNode(sel);
        });

        StackPane treeArea = new StackPane(treeView);
        VBox.setVgrow(treeArea, Priority.ALWAYS);

        treeSearchField.textProperty().addListener((obs, old, query) -> {
            if (query.isBlank()) {
                treeArea.getChildren().setAll(treeView);
            } else {
                String lq = query.toLowerCase();
                List<JarNode> hits = searchableNodes.stream()
                        .filter(n -> n.getName().toLowerCase().contains(lq)
                                || n.getFullPath().toLowerCase().contains(lq))
                        .sorted(Comparator.comparing(JarNode::getName, String.CASE_INSENSITIVE_ORDER))
                        .toList();
                searchResultsView.getItems().setAll(hits);
                treeArea.getChildren().setAll(searchResultsView);
            }
        });

        VBox leftPane = new VBox(searchBar, treeArea);
        SplitPane.setResizableWithParent(leftPane, false);
        return leftPane;
    }

    private void collectSearchableNodes(TreeItem<JarNode> item) {
        JarNode node = item.getValue();
        if (node != null && isOpenableType(node.getType())) {
            searchableNodes.add(node);
        }
        item.getChildren().forEach(this::collectSearchableNodes);
    }

    private void showJarPickerAndCompare() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(primaryStage);
        dialog.setTitle("Compare JARs");
        dialog.setHeaderText("Select two JAR files to compare");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(460);

        File[] jarA = { null }, jarB = { null };
        Label lblA = new Label("(none selected)"), lblB = new Label("(none selected)");
        lblA.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px;");
        lblB.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px;");
        lblA.setWrapText(true);
        lblB.setWrapText(true);

        List<VineflowerDecompiler> loaded = new ArrayList<>(decompilers.values());
        if (loaded.size() >= 1) {
            jarA[0] = loaded.get(0).getJarFile();
            lblA.setText(jarA[0].getName());
        }
        if (loaded.size() >= 2) {
            jarB[0] = loaded.get(1).getJarFile();
            lblB.setText(jarB[0].getName());
        }

        Button btnA = new Button("JAR A (original)…");
        Button btnB = new Button("JAR B (modified)…");
        btnA.setGraphic(new FontIcon(MaterialDesignF.FOLDER_OPEN));
        btnB.setGraphic(new FontIcon(MaterialDesignF.FOLDER_OPEN));
        btnA.setMaxWidth(Double.MAX_VALUE);
        btnB.setMaxWidth(Double.MAX_VALUE);
        btnA.setOnAction(e -> {
            File f = pickJarFile("Select JAR A (original)");
            if (f != null) {
                jarA[0] = f;
                lblA.setText(f.getName());
            }
        });
        btnB.setOnAction(e -> {
            File f = pickJarFile("Select JAR B (modified)");
            if (f != null) {
                jarB[0] = f;
                lblB.setText(f.getName());
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.add(btnA, 0, 0);
        grid.add(lblA, 1, 0);
        grid.add(btnB, 0, 1);
        grid.add(lblB, 1, 1);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(new ColumnConstraints(), c1);
        grid.setPadding(new Insets(4));

        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            if (jarA[0] == null || jarB[0] == null) {
                showError("Compare JARs", "Please select both JAR files.");
                return;
            }
            if (jarA[0].getAbsolutePath().equals(jarB[0].getAbsolutePath())) {
                showError("Compare JARs", "Please select two different JAR files.");
                return;
            }
            startCompareJars(jarA[0], jarB[0]);
        });
    }

    private File pickJarFile(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JAR Files", "*.jar"));
        chooser.setInitialDirectory(AppConfig.getLastOpenedDir());
        File f = chooser.showOpenDialog(primaryStage);
        if (f != null) AppConfig.setLastOpenedDir(f);
        return f;
    }

    private void startCompareJars(File jarA, File jarB) {
        String rootPath = "compare:" + jarA.getAbsolutePath() + ":::" + jarB.getAbsolutePath();
        String rootName = jarA.getName() + "-compared-to-" + jarB.getName();

        JarNode rootNode = new JarNode(rootName, rootPath, JarNode.Type.ROOT);
        TreeItem<JarNode> compRoot = new TreeItem<>(rootNode);
        compRoot.setExpanded(true);
        virtualRoot.getChildren().add(compRoot);
        jarRoots.put(rootPath, compRoot);
        setStatus("Comparing " + jarA.getName() + " vs " + jarB.getName() + "…");

        VineflowerDecompiler decA = compareDecompilers.computeIfAbsent(
                jarA.getAbsolutePath(), k -> new VineflowerDecompiler(jarA));
        VineflowerDecompiler decB = compareDecompilers.computeIfAbsent(
                jarB.getAbsolutePath(), k -> new VineflowerDecompiler(jarB));

        Thread t = new Thread(() -> {
            try {
                Set<String> classesA = listClasses(jarA);
                Set<String> classesB = listClasses(jarB);

                Set<String> onlyInA = new HashSet<>(classesA);
                onlyInA.removeAll(classesB);
                Set<String> onlyInB = new HashSet<>(classesB);
                onlyInB.removeAll(classesA);
                Set<String> common = new HashSet<>(classesA);
                common.retainAll(classesB);

                int total = onlyInA.size() + onlyInB.size() + common.size();
                AtomicInteger done = new AtomicInteger(0);

                Platform.runLater(() -> {
                    progressBar.setProgress(0);
                    progressBar.setVisible(true);
                    progressBar.setManaged(true);
                });

                Map<String, TreeItem<JarNode>> pkgMap = new HashMap<>();

                int threads = Math.min(Runtime.getRuntime().availableProcessors(), 8);
                ExecutorService pool = Executors.newFixedThreadPool(threads);

                for (String cls : onlyInA) {
                    pool.submit(() -> {
                        int d = done.incrementAndGet();
                        Platform.runLater(() -> {
                            addCompareLeaf(cls, JarNode.DiffStatus.REMOVED,
                                    jarA, null, compRoot, pkgMap, rootPath);
                            progressBar.setProgress((double) d / total);
                            setStatus("Comparing: " + d + "/" + total);
                        });
                    });
                }
                for (String cls : onlyInB) {
                    pool.submit(() -> {
                        int d = done.incrementAndGet();
                        Platform.runLater(() -> {
                            addCompareLeaf(cls, JarNode.DiffStatus.ADDED,
                                    null, jarB, compRoot, pkgMap, rootPath);
                            progressBar.setProgress((double) d / total);
                            setStatus("Comparing: " + d + "/" + total);
                        });
                    });
                }
                for (String cls : common) {
                    pool.submit(() -> {
                        try {
                            CompletableFuture<String> fa = CompletableFuture.supplyAsync(() -> {
                                try {
                                    return decA.decompile(cls);
                                } catch (Exception e) {
                                    return null;
                                }
                            });
                            CompletableFuture<String> fb = CompletableFuture.supplyAsync(() -> {
                                try {
                                    return decB.decompile(cls);
                                } catch (Exception e) {
                                    return null;
                                }
                            });
                            String srcA = fa.get();
                            String srcB = fb.get();
                            boolean changed = srcA == null || !srcA.equals(srcB);
                            int d = done.incrementAndGet();
                            if (changed) {
                                Platform.runLater(() -> {
                                    addCompareLeaf(cls, JarNode.DiffStatus.CHANGED,
                                            jarA, jarB, compRoot, pkgMap, rootPath);
                                    progressBar.setProgress((double) d / total);
                                    setStatus("Comparing: " + d + "/" + total);
                                });
                            } else {
                                Platform.runLater(() -> {
                                    progressBar.setProgress((double) d / total);
                                    setStatus("Comparing: " + d + "/" + total);
                                });
                            }
                        } catch (Exception ignored) {
                            int d = done.incrementAndGet();
                            Platform.runLater(() -> {
                                addCompareLeaf(cls, JarNode.DiffStatus.CHANGED,
                                        jarA, jarB, compRoot, pkgMap, rootPath);
                                progressBar.setProgress((double) d / total);
                                setStatus("Comparing: " + d + "/" + total);
                            });
                        }
                    });
                }

                pool.shutdown();
                pool.awaitTermination(10, TimeUnit.MINUTES);

                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                    sortTree(compRoot);
                    updatePackageColors(compRoot);
                    collectSearchableNodes(compRoot);
                    setStatus("Comparison complete: " + rootName);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                    setStatus("Comparison failed: " + e.getMessage());
                });
            }
        }, "jar-compare");
        t.setDaemon(true);
        t.start();
    }

    private Set<String> listClasses(File jar) throws IOException {
        Set<String> classes = new HashSet<>();
        try (JarFile jf = new JarFile(jar)) {
            jf.stream()
                    .map(JarEntry::getName)
                    .filter(n -> n.endsWith(".class") && !n.contains("$"))
                    .map(n -> n.substring(0, n.length() - 6))
                    .forEach(classes::add);
        }
        return classes;
    }

    private void addCompareLeaf(String cls, JarNode.DiffStatus status,
                                File jarA, File jarB,
                                TreeItem<JarNode> compRoot,
                                Map<String, TreeItem<JarNode>> pkgMap,
                                String compRootPath) {
        String pkgPath = cls.contains("/") ? cls.substring(0, cls.lastIndexOf('/')) : "";
        String simple = cls.contains("/") ? cls.substring(cls.lastIndexOf('/') + 1) : cls;

        TreeItem<JarNode> pkg = ensureComparePackage(compRoot, pkgMap, pkgPath);

        File nodeJar = (status == JarNode.DiffStatus.ADDED) ? jarB : jarA;
        File nodeJarB = (status == JarNode.DiffStatus.CHANGED) ? jarB : null;

        JarNode leaf = new JarNode(simple, cls, JarNode.Type.CLASS,
                nodeJar, status, nodeJarB, compRootPath);
        pkg.getChildren().add(new TreeItem<>(leaf));
    }

    private TreeItem<JarNode> ensureComparePackage(TreeItem<JarNode> root,
                                                   Map<String, TreeItem<JarNode>> map,
                                                   String pkgPath) {
        if (pkgPath.isEmpty()) return root;
        TreeItem<JarNode> existing = map.get(pkgPath);
        if (existing != null) return existing;

        String parentPath = pkgPath.contains("/") ? pkgPath.substring(0, pkgPath.lastIndexOf('/')) : "";
        TreeItem<JarNode> parent = ensureComparePackage(root, map, parentPath);

        String simple = pkgPath.contains("/") ? pkgPath.substring(pkgPath.lastIndexOf('/') + 1) : pkgPath;
        JarNode pkg = new JarNode(simple, pkgPath, JarNode.Type.PACKAGE);
        TreeItem<JarNode> item = new TreeItem<>(pkg);
        parent.getChildren().add(item);
        map.put(pkgPath, item);
        return item;
    }

    /** Walks the comparison tree post-order and colors packages that are all-added or all-removed. */
    private void updatePackageColors(TreeItem<JarNode> item) {
        item.getChildren().forEach(this::updatePackageColors);

        JarNode node = item.getValue();
        if (node == null || node.getType() != JarNode.Type.PACKAGE) return;
        if (item.getChildren().isEmpty()) return;

        boolean allAdded = true, allRemoved = true;
        for (TreeItem<JarNode> child : item.getChildren()) {
            JarNode c = child.getValue();
            if (c == null) continue;
            if (c.getDiffStatus() != JarNode.DiffStatus.ADDED) allAdded = false;
            if (c.getDiffStatus() != JarNode.DiffStatus.REMOVED) allRemoved = false;
        }

        JarNode.DiffStatus pkgStatus = allAdded ? JarNode.DiffStatus.ADDED
                : allRemoved ? JarNode.DiffStatus.REMOVED : null;
        if (pkgStatus != null) {
            item.setValue(new JarNode(node.getName(), node.getFullPath(),
                    JarNode.Type.PACKAGE, null, pkgStatus, null, null));
        }
    }

    private void openComparisonDiffTab(JarNode node) {
        if (!isOpenableType(node.getType())) return;
        String tabKey = node.getComparisonRootPath() + "!" + node.getFullPath();

        if (openTabs.containsKey(tabKey)) {
            tabPane.getSelectionModel().select(openTabs.get(tabKey));
            return;
        }

        String simple = node.getName();
        DiffTab tab = new DiffTab(simple + ".java");
        tab.setOnClosed(e -> openTabs.remove(tabKey));
        openTabs.put(tabKey, tab);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

        JarNode.DiffStatus status = node.getDiffStatus();
        setStatus("Computing diff for " + simple + "…");

        Task<String[]> task = new Task<>() {
            @Override
            protected String[] call() throws Exception {
                String cls = node.getFullPath();
                if (status == JarNode.DiffStatus.ADDED) {
                    return new String[]{ null, getCompareDecompiler(node.getJarFile()).decompile(cls) };
                } else if (status == JarNode.DiffStatus.REMOVED) {
                    return new String[]{ getCompareDecompiler(node.getJarFile()).decompile(cls), null };
                } else {
                    VineflowerDecompiler dA = getCompareDecompiler(node.getJarFile());
                    VineflowerDecompiler dB = getCompareDecompiler(node.getJarFileB());
                    CompletableFuture<String> fa = CompletableFuture.supplyAsync(() -> {
                        try {
                            return dA.decompile(cls);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    });
                    CompletableFuture<String> fb = CompletableFuture.supplyAsync(() -> {
                        try {
                            return dB.decompile(cls);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    });
                    return new String[]{ fa.get(), fb.get() };
                }
            }
        };
        task.setOnSucceeded(e -> {
            String[] srcs = task.getValue();
            String label = simple + ".java";
            if (status == JarNode.DiffStatus.ADDED) tab.setOnlyInOne(srcs[1], true, label);
            else if (status == JarNode.DiffStatus.REMOVED) tab.setOnlyInOne(srcs[0], false, label);
            else {
                String labelA = label + " (" + node.getJarFile().getName() + ")";
                String labelB = label + " (" + node.getJarFileB().getName() + ")";
                tab.setDiff(srcs[0], srcs[1], labelA, labelB);
            }
            setStatus("Diff ready: " + simple);
        });
        task.setOnFailed(e -> {
            tab.setError(task.getException() != null ? task.getException().getMessage() : "Unknown");
            setStatus("Diff failed: " + simple);
        });
        Thread t = new Thread(task, "diff-" + simple);
        t.setDaemon(true);
        t.start();
    }

    private VineflowerDecompiler getCompareDecompiler(File jar) {
        String path = jar.getAbsolutePath();
        VineflowerDecompiler d = decompilers.get(path);
        if (d != null) return d;
        return compareDecompilers.computeIfAbsent(path, k -> new VineflowerDecompiler(jar));
    }

    private void exportDiffToPaste(JarNode node) {
        String tabKey = node.getComparisonRootPath() + "!" + node.getFullPath();
        Tab existing = openTabs.get(tabKey);
        if (existing instanceof DiffTab dt && !dt.getDiffText().isBlank()) {
            uploadToPaste(dt.getDiffText(), node.getName(), "diff");
            return;
        }
        setStatus("Computing diff for export…");
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return computeDiffText(node);
            }
        };
        task.setOnSucceeded(e -> uploadToPaste(task.getValue(), node.getName(), "diff"));
        task.setOnFailed(e -> setStatus("Export failed: " +
                (task.getException() != null ? task.getException().getMessage() : "error")));
        Thread t = new Thread(task, "export-diff");
        t.setDaemon(true);
        t.start();
    }

    private String computeDiffText(JarNode node) throws Exception {
        String cls = node.getFullPath();
        String label = node.getName() + ".java";
        JarNode.DiffStatus status = node.getDiffStatus();
        if (status == JarNode.DiffStatus.ADDED) {
            return Differ.addedDiff(getCompareDecompiler(node.getJarFile()).decompile(cls), label);
        } else if (status == JarNode.DiffStatus.REMOVED) {
            return Differ.removedDiff(getCompareDecompiler(node.getJarFile()).decompile(cls), label);
        } else {
            String a = getCompareDecompiler(node.getJarFile()).decompile(cls);
            String b = getCompareDecompiler(node.getJarFileB()).decompile(cls);
            String labelA = label + " (" + node.getJarFile().getName() + ")";
            String labelB = label + " (" + node.getJarFileB().getName() + ")";
            return Differ.unifiedDiff(a, b, labelA, labelB);
        }
    }

    private void uploadToPaste(String content, String label, String language) {
        setStatus("Uploading " + label + "…");
        Thread t = new Thread(() -> {
            try {
                String key = PasteClient.upload(content, language);
                String url = PasteClient.pasteUrl(key);
                Platform.runLater(() -> {
                    ClipboardContent cc = new ClipboardContent();
                    cc.putString(url);
                    Clipboard.getSystemClipboard().setContent(cc);
                    setStatus("Paste URL copied: " + url);
                });
            } catch (Exception e) {
                Platform.runLater(() -> setStatus("Upload failed: " + e.getMessage()));
            }
        }, "paste-upload");
        t.setDaemon(true);
        t.start();
    }

    public StackPane getRoot() {
        return rootContainer;
    }
}
