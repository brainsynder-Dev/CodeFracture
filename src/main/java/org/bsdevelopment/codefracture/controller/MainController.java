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
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ChoiceDialog;
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
import javafx.scene.control.Tooltip;
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
import org.bsdevelopment.codefracture.CodeFractureApp;
import org.bsdevelopment.codefracture.decompiler.ObfuscationDetector;
import org.bsdevelopment.codefracture.decompiler.VineflowerDecompiler;
import org.bsdevelopment.codefracture.model.JarNode;
import org.bsdevelopment.codefracture.ui.CodeTab;
import org.bsdevelopment.codefracture.ui.DiffFilterDialog;
import org.bsdevelopment.codefracture.ui.DiffTab;
import org.bsdevelopment.codefracture.ui.Differ;
import org.bsdevelopment.codefracture.utils.PasteClient;
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
    private final HBox obfBanner;

    private final Map<String, VineflowerDecompiler> decompilers = new LinkedHashMap<>();
    private final Map<String, VineflowerDecompiler> compareDecompilers = new ConcurrentHashMap<>();
    private final Map<String, ExecutorService> compareJobs = new ConcurrentHashMap<>();
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

        obfBanner = buildObfBanner();
        obfBanner.setVisible(false);
        obfBanner.setManaged(false);

        VBox bottomArea = new VBox(obfBanner, statusBar);
        mainPane.setBottom(bottomArea);

        dragOverlay = buildDragOverlay();
        dragOverlay.setVisible(false);
        dragOverlay.setMouseTransparent(true);

        rootContainer = new StackPane(mainPane, dragOverlay);
        setupDragAndDrop();
    }

    private static void cleanOldJars(Path dir, Path keep, String platform) {
        java.util.regex.Pattern pat = java.util.regex.Pattern.compile(
                "CodeFracture-(.+)-all-" + java.util.regex.Pattern.quote(platform) + "\\.jar");
        try (var files = Files.list(dir)) {
            files.filter(p -> pat.matcher(p.getFileName().toString()).matches())
                    .filter(p -> !p.equals(keep))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static String resolveJavaExe() {
        String javaHome = System.getProperty("java.home");
        boolean win = detectPlatform().equals("windows");
        if (javaHome != null) {
            Path candidate = Path.of(javaHome, "bin", win ? "javaw.exe" : "java");
            if (Files.exists(candidate)) return candidate.toString();
        }
        return win ? "javaw" : "java";
    }

    private static Path findLauncherJar() {
        try {
            URI loc = MainController.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path jarDir = Path.of(loc).getParent();
            if (jarDir != null) {
                Path candidate = jarDir.resolve("Launcher.jar");
                if (Files.exists(candidate)) return candidate;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "mac";
        return "linux";
    }

    private static int compareVersions(String a, String b) {
        String[] aParts = a.split("[.\\-]");
        String[] bParts = b.split("[.\\-]");
        int len = Math.min(aParts.length, bParts.length);
        for (int i = 0; i < len; i++) {
            int cmp;
            try {
                cmp = Integer.compare(Integer.parseInt(aParts[i]), Integer.parseInt(bParts[i]));
            } catch (NumberFormatException e) {
                cmp = aParts[i].compareTo(bParts[i]);
            }
            if (cmp != 0) return cmp;
        }
        return Integer.compare(aParts.length, bParts.length);
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

    private HBox buildObfBanner() {
        Label msg = new Label();
        msg.setStyle("-fx-text-fill: -color-warning-fg;");
        HBox.setHgrow(msg, Priority.ALWAYS);

        Button dismiss = new Button("Dismiss");
        dismiss.getStyleClass().add("small");
        dismiss.setOnAction(e -> {
            obfBanner.setVisible(false);
            obfBanner.setManaged(false);
        });

        HBox banner = new HBox(8, msg, dismiss);
        banner.setAlignment(Pos.CENTER);
        banner.setPadding(new Insets(4, 10, 4, 10));
        banner.setStyle("-fx-background-color: -color-warning-subtle; -fx-border-color: -color-warning-emphasis; -fx-border-width: 0 0 1 0;");
        banner.setUserData(msg);
        return banner;
    }

    private void showObfBanner(String jarName) {
        Label msg = (Label) obfBanner.getUserData();
        msg.setText("\"" + jarName + "\" appears to be obfuscated — class names may not be meaningful.");
        obfBanner.setVisible(true);
        obfBanner.setManaged(true);
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
                applySyntaxColors(theme.getName());
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
        MenuItem checkUpdates = new MenuItem("Check for Updates…");
        checkUpdates.setGraphic(new FontIcon(MaterialDesignU.UPDATE));
        checkUpdates.setOnAction(e -> checkForUpdates());
        MenuItem about = new MenuItem("About CodeFracture");
        about.setOnAction(e -> showAbout());
        helpMenu.getItems().addAll(checkUpdates, new SeparatorMenuItem(), about);

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

            Thread obfCheck = new Thread(() -> {
                if (ObfuscationDetector.isObfuscated(file)) {
                    Platform.runLater(() -> showObfBanner(file.getName()));
                }
            }, "obf-detector");
            obfCheck.setDaemon(true);
            obfCheck.start();
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
            ExecutorService job = compareJobs.remove(jarPath);
            if (job != null) {
                job.shutdownNow();
                progressBar.setVisible(false);
                progressBar.setManaged(false);
                setStatus("Ready");
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

        if (selected instanceof DiffTab dt) {
            String content = dt.getDiffText();
            if (content.isBlank()) {
                showError("Export", "Tab has no content yet.");
                return;
            }
            String defaultName = dt.getText().replace(".java", ".diff");
            ButtonType saveBtn = new ButtonType("Save as File");
            ButtonType pasteBtn = new ButtonType("Upload to Paste");
            Alert choice = new Alert(Alert.AlertType.NONE, "Choose how to export \"" + dt.getText() + "\":");
            choice.initOwner(primaryStage);
            choice.setTitle("Export");
            choice.setHeaderText(null);
            choice.getButtonTypes().setAll(saveBtn, pasteBtn, ButtonType.CANCEL);
            choice.showAndWait().ifPresent(bt -> {
                if (bt == saveBtn) saveTextFile(content, defaultName, "Diff Files", "*.diff");
                else if (bt == pasteBtn) uploadToPaste(content, dt.getText(), "diff");
            });
            return;
        }

        // No tab selected or not a code tab — offer whole-JAR export if JARs are loaded
        CodeTab ct = (selected instanceof CodeTab c) ? c : null;

        if (ct == null && decompilers.isEmpty()) {
            showError("Export", "No JAR is loaded.");
            return;
        }

        ButtonType classBtn = new ButtonType("This Class (.java)");
        ButtonType jarBtn = new ButtonType("Whole JAR (.zip)");
        ButtonType pasteBtn = new ButtonType("Upload to Paste");

        List<ButtonType> buttons = new ArrayList<>();
        if (ct != null && !ct.isResource()) buttons.add(classBtn);
        if (!decompilers.isEmpty()) buttons.add(jarBtn);
        if (ct != null) buttons.add(pasteBtn);
        buttons.add(ButtonType.CANCEL);

        Alert choice = new Alert(Alert.AlertType.NONE,
                ct != null ? "Choose how to export \"" + ct.getText() + "\":" : "Export a JAR as source ZIP:");
        choice.initOwner(primaryStage);
        choice.setTitle("Export Source");
        choice.setHeaderText(null);
        choice.getButtonTypes().setAll(buttons);

        choice.showAndWait().ifPresent(bt -> {
            if (bt == classBtn) {
                String content = ct.getCodeArea().getText();
                if (content.isBlank()) {
                    showError("Export", "Tab has no content yet.");
                    return;
                }
                String name = ct.getText().endsWith(".java") ? ct.getText() : ct.getText() + ".java";
                saveTextFile(content, name, "Java Files", "*.java");
            } else if (bt == jarBtn) {
                exportWholeJar(pickJarForExport(ct));
            } else if (bt == pasteBtn) {
                String content = ct.getCodeArea().getText();
                if (content.isBlank()) {
                    showError("Export", "Tab has no content yet.");
                    return;
                }
                uploadToPaste(content, ct.getText(), "java");
            }
        });
    }

    private VineflowerDecompiler pickJarForExport(CodeTab ct) {
        if (decompilers.size() == 1) return decompilers.values().iterator().next();

        // Try to infer jar from the active tab key
        if (ct != null) {
            Optional<String> jarKey = openTabs.entrySet().stream()
                    .filter(e -> e.getValue() == ct && e.getKey().contains("!"))
                    .map(e -> e.getKey().substring(0, e.getKey().lastIndexOf('!')))
                    .findFirst();
            if (jarKey.isPresent()) {
                VineflowerDecompiler d = decompilers.get(jarKey.get());
                if (d != null) return d;
            }
        }

        // Multiple JARs and can't infer — ask user
        List<String> names = decompilers.values().stream()
                .map(d -> d.getJarFile().getName())
                .toList();
        ChoiceDialog<String> picker = new ChoiceDialog<>(names.get(0), names);
        picker.initOwner(primaryStage);
        picker.setTitle("Select JAR");
        picker.setHeaderText("Which JAR do you want to export?");
        picker.setContentText("JAR:");
        return picker.showAndWait()
                .flatMap(name -> decompilers.values().stream()
                        .filter(d -> d.getJarFile().getName().equals(name))
                        .findFirst())
                .orElse(null);
    }

    private void saveTextFile(String content, String defaultName, String filterDesc, String filterExt) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export");
        chooser.setInitialFileName(defaultName);
        chooser.setInitialDirectory(AppConfig.getLastOpenedDir());
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(filterDesc, filterExt),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File dest = chooser.showSaveDialog(primaryStage);
        if (dest == null) return;
        try {
            Files.writeString(dest.toPath(), content);
            AppConfig.setLastOpenedDir(dest);
            setStatus("Exported: " + dest.getName());
        } catch (Exception e) {
            showError("Export Failed", e.getMessage());
        }
    }

    private void exportWholeJar(VineflowerDecompiler decompiler) {
        if (decompiler == null) return;
        String zipName = decompiler.getJarFile().getName().replaceAll("\\.jar$", "") + "-sources.zip";
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export JAR as ZIP");
        chooser.setInitialFileName(zipName);
        chooser.setInitialDirectory(AppConfig.getLastOpenedDir());
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("ZIP Files", "*.zip"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File dest = chooser.showSaveDialog(primaryStage);
        if (dest == null) return;

        AppConfig.setLastOpenedDir(dest);
        Set<String> classNames = decompiler.getClassNames();

        ProgressBar bar = new ProgressBar(0);
        bar.setPrefWidth(300);
        Label lbl = new Label("Preparing…");
        VBox content = new VBox(8, lbl, bar);
        content.setPadding(new Insets(16));
        content.setAlignment(Pos.CENTER_LEFT);

        Dialog<Void> progress = new Dialog<>();
        progress.initOwner(primaryStage);
        progress.setTitle("Exporting " + decompiler.getJarFile().getName());
        progress.setHeaderText(null);
        progress.getDialogPane().setContent(content);

        Thread worker = new Thread(() -> {
            int total = classNames.size();
            int done = 0;
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(dest.toPath()))) {
                for (String cls : classNames) {
                    final String current = cls;
                    final int pct = done;
                    Platform.runLater(() -> {
                        lbl.setText("Decompiling: " + current.replace('/', '.'));
                        bar.setProgress((double) pct / total);
                    });
                    String src;
                    try {
                        src = decompiler.decompile(cls);
                    } catch (Exception ex) {
                        src = "// Decompilation failed for: " + cls + "\n// " + ex.getMessage();
                    }
                    String entryName = cls + ".java";
                    zos.putNextEntry(new ZipEntry(entryName));
                    zos.write(src.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                    done++;
                }
            } catch (Exception ex) {
                Platform.runLater(() -> showError("Export Failed", ex.getMessage()));
            }
            final int finalDone = done;
            Platform.runLater(() -> {
                progress.close();
                setStatus("Exported " + finalDone + " classes to " + dest.getName());
            });
        }, "jar-exporter");
        worker.setDaemon(true);
        worker.start();

        progress.showAndWait();
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

    private void applySyntaxColors(String themeName) {
        var sheets = primaryStage.getScene().getStylesheets();
        sheets.removeIf(s -> s.contains("syntax-colors-"));
        sheets.add(CodeFractureApp.syntaxColorSheet(themeName));
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
                        "  \u2022 JavaFX 21\n" +
                        "  \u2022 AtlantaFX 2.1.0\n" +
                        "  \u2022 Vineflower (Fernflower fork)\n" +
                        "  \u2022 RichTextFX"
        );
        Label hint = new Label(
                "Ctrl+Click on any class name or import to navigate.\n" +
                        "Drag & drop JAR files directly onto the window."
        );

        VBox content = new VBox(12, builtWith, new Separator(), hint);
        content.setPrefWidth(400);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void checkForUpdates() {
        statusLabel.setText("Checking for updates…");
        Thread thread = new Thread(() -> {
            String[] info = null;
            String fetchError = null;
            try {
                info = fetchLatestRelease();
            } catch (Exception e) {
                fetchError = e.getMessage();
            }
            final String[] finalInfo = info;
            final String finalError = fetchError;
            Platform.runLater(() -> {
                statusLabel.setText("Ready — open or drop a JAR to begin");
                if (finalError != null) {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    applyAppIcons(alert);
                    alert.setTitle("Check for Updates");
                    alert.setHeaderText("Could not check for updates");
                    alert.setContentText(finalError);
                    alert.showAndWait();
                    return;
                }
                String latestVersion = finalInfo[0];
                String jarUrl = finalInfo[1]; // platform JAR download URL, may be null
                String htmlUrl = finalInfo[2]; // release page URL
                if (compareVersions(latestVersion, BuildInfo.VERSION) > 0) {
                    if (jarUrl != null) {
                        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                        applyAppIcons(confirm);
                        confirm.setTitle("Update Available");
                        confirm.setHeaderText("CodeFracture " + latestVersion + " is available");
                        confirm.setContentText("You are running v" + BuildInfo.VERSION
                                + ".\nDownload and restart to apply the update?");
                        confirm.showAndWait().ifPresent(btn -> {
                            if (btn == ButtonType.OK) downloadAndRestart(latestVersion, jarUrl);
                        });
                    } else {
                        // No platform JAR in this release — fall back to browser
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        applyAppIcons(alert);
                        alert.setTitle("Update Available");
                        alert.setHeaderText("CodeFracture " + latestVersion + " is available");
                        alert.setContentText("Click OK to open the download page.");
                        alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
                        alert.showAndWait().ifPresent(btn -> {
                            if (btn == ButtonType.OK) {
                                try {
                                    Desktop.getDesktop().browse(URI.create(htmlUrl));
                                } catch (Exception ignored) {
                                }
                            }
                        });
                    }
                } else {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    applyAppIcons(alert);
                    alert.setTitle("Check for Updates");
                    alert.setHeaderText("You're up to date");
                    alert.setContentText("CodeFracture v" + BuildInfo.VERSION + " is the latest version.");
                    alert.showAndWait();
                }
            });
        }, "update-check");
        thread.setDaemon(true);
        thread.start();
    }

    private void downloadAndRestart(String version, String jarUrl) {
        String platform = detectPlatform();
        Path destDir = AppConfig.getConfigDir().resolve("app");
        Path destFile = destDir.resolve("CodeFracture-" + version + "-all-" + platform + ".jar");
        Path tmpFile = destDir.resolve(".download-" + version + ".tmp");

        try {
            Files.createDirectories(destDir);
        } catch (IOException e) {
            showError("Download Failed", "Cannot create app directory: " + e.getMessage());
            return;
        }

        Dialog<ButtonType> dlDialog = new Dialog<>();
        applyAppIcons(dlDialog);
        dlDialog.setTitle("Downloading Update");
        dlDialog.setHeaderText("Downloading CodeFracture " + version + "…");
        ProgressBar dlProgress = new ProgressBar(-1);
        dlProgress.setPrefWidth(360);
        Label dlStatus = new Label("Connecting…");
        VBox dlContent = new VBox(10, dlProgress, dlStatus);
        dlContent.setPrefWidth(380);
        dlDialog.getDialogPane().setContent(dlContent);
        dlDialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        HttpRequest dlRequest = HttpRequest.newBuilder()
                .uri(URI.create(jarUrl))
                .header("User-Agent", "CodeFracture/" + BuildInfo.VERSION)
                .GET()
                .build();

        dlStatus.setText("Downloading…");
        CompletableFuture<HttpResponse<Path>> future =
                client.sendAsync(dlRequest, HttpResponse.BodyHandlers.ofFile(tmpFile));

        future.thenAccept(resp -> {
            try {
                Files.move(tmpFile, destFile, StandardCopyOption.REPLACE_EXISTING);
                cleanOldJars(destDir, destFile, platform);
            } catch (IOException e) {
                Platform.runLater(() -> {
                    dlDialog.close();
                    showError("Download Failed", e.getMessage());
                });
                return;
            }
            Platform.runLater(() -> {
                dlDialog.close();
                restartApp(destFile);
            });
        }).exceptionally(ex -> {
            try {
                Files.deleteIfExists(tmpFile);
            } catch (IOException ignored) {
            }
            boolean cancelled = ex instanceof java.util.concurrent.CancellationException
                    || ex.getCause() instanceof java.util.concurrent.CancellationException;
            if (!cancelled) {
                String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                Platform.runLater(() -> {
                    dlDialog.close();
                    showError("Download Failed", msg);
                });
            }
            return null;
        });

        dlDialog.showAndWait();
        if (!future.isDone()) {
            future.cancel(true);
            try {
                Files.deleteIfExists(tmpFile);
            } catch (IOException ignored) {
            }
        }
    }

    private void restartApp(Path newJar) {
        try {
            String javaExe = resolveJavaExe();
            Path launcherJar = findLauncherJar();
            List<String> cmd = launcherJar != null
                    ? List.of(javaExe, "-jar", launcherJar.toString())
                    : List.of(javaExe, "-jar", newJar.toAbsolutePath().toString());
            new ProcessBuilder(cmd).inheritIO().start();
            Platform.exit();
            System.exit(0);
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            applyAppIcons(alert);
            alert.setTitle("Restart Required");
            alert.setHeaderText("Update downloaded successfully");
            alert.setContentText("Please restart CodeFracture to apply the update.");
            alert.showAndWait();
        }
    }

    private String[] fetchLatestRelease() throws Exception {
        GitHub github = GitHub.connectAnonymously();
        GHRepository repo = github.getRepository("brainsynder-Dev/CodeFracture");
        GHRelease latest = repo.getLatestRelease();
        if (latest == null)
            throw new Exception("No GitHub Releases found for this repository.");
        String version = latest.getTagName().replaceFirst("^v", "");
        String platform = detectPlatform();
        String expectedAsset = "CodeFracture-" + version + "-all-" + platform + ".jar";
        String jarUrl = null;
        for (GHAsset asset : latest.listAssets().toList()) {
            if (asset.getName().equals(expectedAsset)) {
                jarUrl = asset.getBrowserDownloadUrl();
                break;
            }
        }
        return new String[]{ version, jarUrl, latest.getHtmlUrl().toString() };
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
        dialog.getDialogPane().setPrefWidth(480);

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
            if (f != null) { jarA[0] = f; lblA.setText(f.getName()); }
        });
        btnB.setOnAction(e -> {
            File f = pickJarFile("Select JAR B (modified)");
            if (f != null) { jarB[0] = f; lblB.setText(f.getName()); }
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

        String rawPatterns = AppConfig.get(AppConfig.DIFF_FILTER_PATTERNS, "").trim();
        @SuppressWarnings("unchecked")
        List<String>[] filterRef = new List[]{ rawPatterns.isEmpty() ? new ArrayList<>() :
                Arrays.stream(rawPatterns.split("\\|"))
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .collect(Collectors.toCollection(ArrayList::new)) };

        CheckBox filterCheck = new CheckBox("Enable class/package filter");
        filterCheck.setSelected(AppConfig.get(AppConfig.DIFF_FILTER_ENABLED, "false").equals("true"));
        filterCheck.setTooltip(new Tooltip("Only compare classes matching the configured patterns"));
        filterCheck.selectedProperty().addListener((obs, wasOn, isOn) ->
                AppConfig.set(AppConfig.DIFF_FILTER_ENABLED, String.valueOf(isOn)));

        Label patternCountLabel = new Label(describeFilterPatterns(filterRef[0]));
        patternCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");

        Button configurePatternsBtn = new Button("Configure…");
        configurePatternsBtn.setTooltip(new Tooltip("Set which packages or classes to include in the comparison"));
        configurePatternsBtn.setOnAction(e -> {
            DiffFilterDialog filterDialog = new DiffFilterDialog(filterRef[0]);
            filterDialog.initOwner(primaryStage);
            filterDialog.showAndWait().ifPresent(patterns -> {
                filterRef[0] = patterns;
                AppConfig.set(AppConfig.DIFF_FILTER_PATTERNS, String.join("|", patterns));
                patternCountLabel.setText(describeFilterPatterns(patterns));
            });
        });

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);
        HBox filterRow = new HBox(8, filterCheck, filterSpacer, patternCountLabel, configurePatternsBtn);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, grid, new Separator(), filterRow);
        content.setPadding(new Insets(4, 4, 0, 4));
        dialog.getDialogPane().setContent(content);

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

    private static String describeFilterPatterns(List<String> patterns) {
        return patterns.isEmpty() ? "No patterns configured" : patterns.size() + " pattern(s) configured";
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

    private static List<String> loadDiffFilterPatterns() {
        String raw = AppConfig.get(AppConfig.DIFF_FILTER_PATTERNS, "").trim();
        if (raw.isEmpty()) return List.of();
        return Arrays.stream(raw.split("\\|"))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static boolean matchesDiffFilter(String internalName, List<String> patterns) {
        String slashName = internalName.replace('.', '/');
        String simpleName = slashName.contains("/")
                ? slashName.substring(slashName.lastIndexOf('/') + 1) : slashName;
        for (String raw : patterns) {
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

                if (AppConfig.get(AppConfig.DIFF_FILTER_ENABLED, "false").equals("true")) {
                    List<String> patterns = loadDiffFilterPatterns();
                    if (!patterns.isEmpty()) {
                        classesA.removeIf(cls -> !matchesDiffFilter(cls, patterns));
                        classesB.removeIf(cls -> !matchesDiffFilter(cls, patterns));
                    }
                }

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
                ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
                    Thread th = new Thread(r, "jar-compare-worker");
                    th.setDaemon(true);
                    return th;
                });
                compareJobs.put(rootPath, pool);

                for (String cls : onlyInA) {
                    pool.submit(() -> {
                        if (Thread.currentThread().isInterrupted()) return;
                        int d = done.incrementAndGet();
                        Platform.runLater(() -> {
                            if (!compareJobs.containsKey(rootPath)) return;
                            addCompareLeaf(cls, JarNode.DiffStatus.REMOVED,
                                    jarA, null, compRoot, pkgMap, rootPath);
                            progressBar.setProgress((double) d / total);
                            setStatus("Comparing: " + d + "/" + total);
                        });
                    });
                }
                for (String cls : onlyInB) {
                    pool.submit(() -> {
                        if (Thread.currentThread().isInterrupted()) return;
                        int d = done.incrementAndGet();
                        Platform.runLater(() -> {
                            if (!compareJobs.containsKey(rootPath)) return;
                            addCompareLeaf(cls, JarNode.DiffStatus.ADDED,
                                    null, jarB, compRoot, pkgMap, rootPath);
                            progressBar.setProgress((double) d / total);
                            setStatus("Comparing: " + d + "/" + total);
                        });
                    });
                }
                for (String cls : common) {
                    pool.submit(() -> {
                        if (Thread.currentThread().isInterrupted()) return;
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
                                    if (!compareJobs.containsKey(rootPath)) return;
                                    addCompareLeaf(cls, JarNode.DiffStatus.CHANGED,
                                            jarA, jarB, compRoot, pkgMap, rootPath);
                                    progressBar.setProgress((double) d / total);
                                    setStatus("Comparing: " + d + "/" + total);
                                });
                            } else {
                                Platform.runLater(() -> {
                                    if (!compareJobs.containsKey(rootPath)) return;
                                    progressBar.setProgress((double) d / total);
                                    setStatus("Comparing: " + d + "/" + total);
                                });
                            }
                        } catch (Exception ignored) {
                            int d = done.incrementAndGet();
                            Platform.runLater(() -> {
                                if (!compareJobs.containsKey(rootPath)) return;
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

                // compareJobs.remove returns non-null only if closeJar hasn't already removed it,
                // meaning the comparison completed naturally rather than being cancelled.
                if (compareJobs.remove(rootPath) != null) {
                    Platform.runLater(() -> {
                        progressBar.setVisible(false);
                        progressBar.setManaged(false);
                        sortTree(compRoot);
                        updatePackageColors(compRoot);
                        collectSearchableNodes(compRoot);
                        setStatus("Comparison complete: " + rootName);
                    });
                }

            } catch (Exception e) {
                compareJobs.remove(rootPath);
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
