package org.bsdevelopment.codefracture.launcher;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Bootstrap launcher for CodeFracture.
 *
 * <p>On each launch it:
 * <ol>
 *   <li>Finds the best local app JAR (user-data dir, then install dir).</li>
 *   <li>Queries the GitHub Releases API for the latest version.</li>
 *   <li>Downloads the platform-specific fat JAR if a newer version is available.</li>
 *   <li>Starts the app JAR in a child process and exits.</li>
 * </ol>
 *
 * <p>All network failures are silent — the locally installed version is used as fallback.
 */
public class Launcher {

    private static final String GITHUB_API_LATEST =
            "https://api.github.com/repos/brainsynder-Dev/CodeFracture/releases/latest";

    private static final String PLATFORM = detectPlatform();

    // Matches filenames like "CodeFracture-0.2.0-all-linux.jar"
    private static final Pattern APP_JAR_PATTERN =
            Pattern.compile("CodeFracture-(.+)-all-" + Pattern.quote(PLATFORM) + "\\.jar");

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            System.err.println("[CodeFracture Launcher] Fatal: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        Path installDir    = getLauncherDir();
        Path userDataAppDir = getUserDataDir().resolve("app");
        Files.createDirectories(userDataAppDir);

        Optional<AppJar> localJar = findBestLocalJar(installDir, userDataAppDir);

        ReleaseInfo latest = fetchLatestRelease();
        if (latest != null) {
            String current = localJar.map(j -> j.version).orElse(null);
            if (isNewerVersion(latest.version, current)) {
                System.out.println("[Launcher] Downloading update " + latest.version
                        + (current != null ? " (was " + current + ")" : ""));
                Path downloaded = downloadFile(latest.downloadUrl, userDataAppDir,
                        "CodeFracture-" + latest.version + "-all-" + PLATFORM + ".jar");
                if (downloaded != null) {
                    cleanOldJars(userDataAppDir, downloaded);
                    localJar = Optional.of(new AppJar(downloaded, latest.version));
                    System.out.println("[Launcher] Updated to " + latest.version);
                } else {
                    System.err.println("[Launcher] Download failed – using existing version.");
                }
            }
        }

        if (localJar.isEmpty()) {
            System.err.println("[Launcher] No app JAR found. Cannot start CodeFracture.");
            System.exit(1);
        }

        launchApp(localJar.get().path, args);
    }

    // ── JAR discovery ────────────────────────────────────────────────────────

    private static Optional<AppJar> findBestLocalJar(Path installDir, Path userDataAppDir)
            throws IOException {
        Optional<AppJar> fromUser = findNewestJar(userDataAppDir);
        return fromUser.isPresent() ? fromUser : findNewestJar(installDir);
    }

    private static Optional<AppJar> findNewestJar(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return Optional.empty();
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(p -> APP_JAR_PATTERN.matcher(p.getFileName().toString()).matches())
                    .map(p -> {
                        Matcher m = APP_JAR_PATTERN.matcher(p.getFileName().toString());
                        return m.matches() ? new AppJar(p, m.group(1)) : null;
                    })
                    .filter(j -> j != null)
                    .max(Comparator.comparing(j -> j.version, Launcher::compareVersions));
        }
    }

    private static void cleanOldJars(Path dir, Path keep) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> APP_JAR_PATTERN.matcher(p.getFileName().toString()).matches())
                 .filter(p -> !p.equals(keep))
                 .forEach(p -> {
                     try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                 });
        }
    }

    // ── GitHub API ───────────────────────────────────────────────────────────

    private static ReleaseInfo fetchLatestRelease() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_LATEST))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "CodeFracture-Launcher/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) return parseReleaseInfo(response.body());
        } catch (Exception ignored) {
            // Network unavailable — silent fallback
        }
        return null;
    }

    private static ReleaseInfo parseReleaseInfo(String json) {
        Matcher tagMatcher = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([^\"]+)\"").matcher(json);
        if (!tagMatcher.find()) return null;
        String version = tagMatcher.group(1);

        String expectedAsset = "CodeFracture-" + version + "-all-" + PLATFORM + ".jar";
        Pattern assetBlock = Pattern.compile(
                "\"name\"\\s*:\\s*\"" + Pattern.quote(expectedAsset) + "\"" +
                ".*?\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"",
                Pattern.DOTALL);
        Matcher assetMatcher = assetBlock.matcher(json);
        if (!assetMatcher.find()) return null;

        return new ReleaseInfo(version, assetMatcher.group(1));
    }

    // ── Download ─────────────────────────────────────────────────────────────

    private static Path downloadFile(String url, Path destDir, String fileName) {
        Path dest = destDir.resolve(fileName);
        Path tmp  = destDir.resolve(fileName + ".download");
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "CodeFracture-Launcher/1.0")
                    .GET()
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofFile(tmp));
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
            return dest;
        } catch (Exception e) {
            System.err.println("[Launcher] Download error: " + e.getMessage());
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            return null;
        }
    }

    // ── Launch ───────────────────────────────────────────────────────────────

    private static void launchApp(Path appJar, String[] args) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(resolveJavaExecutable());
        cmd.add("-jar");
        cmd.add(appJar.toAbsolutePath().toString());
        cmd.addAll(Arrays.asList(args));
        new ProcessBuilder(cmd).inheritIO().start();
    }

    private static String resolveJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            boolean win = PLATFORM.equals("windows");
            Path candidate = Paths.get(javaHome, "bin", win ? "javaw.exe" : "java");
            if (Files.exists(candidate)) return candidate.toString();
        }
        return PLATFORM.equals("windows") ? "javaw" : "java";
    }

    // ── Paths ────────────────────────────────────────────────────────────────

    private static Path getLauncherDir() {
        try {
            URI loc = Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path parent = Paths.get(loc).getParent();
            return parent != null ? parent : Paths.get(".");
        } catch (Exception e) {
            return Paths.get(".");
        }
    }

    private static Path getUserDataDir() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank())
            return Paths.get(appData, "CodeFracture");

        String home = System.getProperty("user.home", "");
        if (System.getProperty("os.name", "").toLowerCase().contains("mac"))
            return Paths.get(home, "Library", "Application Support", "CodeFracture");

        String xdg = System.getenv("XDG_CONFIG_HOME");
        if (xdg != null && !xdg.isBlank())
            return Paths.get(xdg, "CodeFracture");

        return Paths.get(home, ".codefracture");
    }

    // ── Version comparison ───────────────────────────────────────────────────

    static int compareVersions(String a, String b) {
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

    static boolean isNewerVersion(String candidate, String current) {
        if (current == null) return true;
        return compareVersions(candidate, current) > 0;
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "mac";
        return "linux";
    }

    // ── Data classes ─────────────────────────────────────────────────────────

    private static final class AppJar {
        final Path   path;
        final String version;
        AppJar(Path path, String version) { this.path = path; this.version = version; }
    }

    private static final class ReleaseInfo {
        final String version;
        final String downloadUrl;
        ReleaseInfo(String version, String downloadUrl) {
            this.version = version;
            this.downloadUrl = downloadUrl;
        }
    }
}
