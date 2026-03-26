package org.bsdevelopment.codefracture;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Persistent application configuration.
 *
 * <p>The config file is stored in a platform-appropriate directory:
 * <ul>
 *   <li>Windows - {@code %APPDATA%\CodeFracture\config.properties}</li>
 *   <li>macOS   - {@code ~/Library/Application Support/CodeFracture/config.properties}</li>
 *   <li>Linux   - {@code $XDG_CONFIG_HOME/CodeFracture/config.properties} or {@code ~/.codefracture/}</li>
 * </ul>
 *
 * <p>All writes are flushed to disk immediately so settings survive crashes.
 */
public final class AppConfig {

    public static final String LAST_OPENED_DIR      = "last.opened.dir";
    public static final String THEME                = "app.theme";
    public static final String SKIP_SPLASH          = "skip.splash";
    public static final String DIFF_FILTER_ENABLED  = "diff.filter.enabled";
    /** Pipe-separated list of class/package patterns for the diff filter. */
    public static final String DIFF_FILTER_PATTERNS = "diff.filter.patterns";

    private static final Path CONFIG_DIR;
    private static final Path CONFIG_FILE;
    private static final Properties PROPS = new Properties();

    static {
        CONFIG_DIR = resolveConfigDir();
        CONFIG_FILE = CONFIG_DIR.resolve("config.properties");
        load();
    }

    public static String get(String key, String defaultValue) {
        return PROPS.getProperty(key, defaultValue);
    }

    public static void set(String key, String value) {
        if (value == null) {
            PROPS.remove(key);
        } else {
            PROPS.setProperty(key, value);
        }
        save();
    }

    /**
     * Returns the last directory opened via the file chooser, falling back to the user home
     * directory if no value has been saved or the saved path no longer exists.
     */
    public static File getLastOpenedDir() {
        String raw = get(LAST_OPENED_DIR, System.getProperty("user.home"));
        File f = new File(raw);
        return (f.exists() && f.isDirectory()) ? f : new File(System.getProperty("user.home"));
    }

    /**
     * Saves the directory of the given file (or the file itself if it is a directory)
     * as the last opened location.
     */
    public static void setLastOpenedDir(File fileOrDir) {
        File dir = fileOrDir.isDirectory() ? fileOrDir : fileOrDir.getParentFile();
        if (dir != null && dir.exists()) set(LAST_OPENED_DIR, dir.getAbsolutePath());
    }

    public static Path getConfigDir() {
        return CONFIG_DIR;
    }

    private static void load() {
        try {
            Files.createDirectories(CONFIG_DIR);
            if (Files.exists(CONFIG_FILE)) {
                try (BufferedReader r = Files.newBufferedReader(CONFIG_FILE)) {
                    PROPS.load(r);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            try (BufferedWriter w = Files.newBufferedWriter(CONFIG_FILE)) {
                PROPS.store(w, "CodeFracture configuration - do not edit while the app is running");
            }
        } catch (IOException ignored) {
        }
    }

    private static Path resolveConfigDir() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank())
            return Path.of(appData, "CodeFracture");

        String home = System.getProperty("user.home", "");

        if (System.getProperty("os.name", "").toLowerCase().contains("mac"))
            return Path.of(home, "Library", "Application Support", "CodeFracture");

        String xdg = System.getenv("XDG_CONFIG_HOME");
        if (xdg != null && !xdg.isBlank())
            return Path.of(xdg, "CodeFracture");

        return Path.of(home, ".codefracture");
    }
}
