package org.bsdevelopment.codefracture.decompiler;

import org.bsdevelopment.codefracture.AppConfig;
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class VineflowerDecompiler {

    private final File jarFile;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private Set<String> classIndex;
    private boolean showComments;

    public VineflowerDecompiler(File jarFile) {
        this.jarFile = jarFile;
        this.showComments = Boolean.parseBoolean(AppConfig.get(AppConfig.SHOW_COMMENTS, "false"));
    }

    public boolean isShowComments() {
        return showComments;
    }

    public void setShowComments(boolean showComments) {
        if (this.showComments != showComments) {
            this.showComments = showComments;
            cache.clear();
            locks.clear();
        }
    }

    public File getJarFile() {
        return jarFile;
    }

    /**
     * Returns {@code true} if this JAR contains the given class.
     *
     * @param internalName class internal name, e.g. {@code "org/bukkit/Bukkit"}
     */
    public boolean hasClass(String internalName) {
        return getClassIndex().contains(internalName);
    }

    /**
     * Decompiles the given class from the JAR and returns its Java source.
     * The result is cached after the first call.
     *
     * @param className internal class name, e.g. {@code "com/example/Foo"}
     */
    public String decompile(String className) throws Exception {
        if (className.endsWith(".class")) {
            className = className.substring(0, className.length() - 6);
        }

        // Fast path — already cached
        String cached = cache.get(className);
        if (cached != null) return cached;

        // Per-class lock: prevents redundant parallel decompilation of the same class
        Object lock = locks.computeIfAbsent(className, k -> new Object());
        synchronized (lock) {
            cached = cache.get(className);
            if (cached != null) return cached;

            String outerClass = className.contains("$")
                    ? className.substring(0, className.indexOf('$'))
                    : className;

            Path tempInput = Files.createTempDirectory("cf_in_");
            try {
                extractClasses(outerClass, tempInput);

                Map<String, String> results = new LinkedHashMap<>();
                IResultSaver saver = buildSaver(results);

                Map<String, Object> options = new HashMap<>();
                options.put(IFernflowerPreferences.INDENT_STRING, "    ");
                options.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
                options.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
                options.put(IFernflowerPreferences.REMOVE_BRIDGE, "1");
                options.put(IFernflowerPreferences.LITERALS_AS_IS, "0");
                options.put(IFernflowerPreferences.DECOMPILE_INNER, "1");
                options.put("dc", showComments ? "1" : "0");

                BaseDecompiler decompiler = new BaseDecompiler(saver, options, new QuietLogger());
                decompiler.addSource(tempInput.toFile());
                decompiler.decompileContext();

                String outerSimple = outerClass.contains("/")
                        ? outerClass.substring(outerClass.lastIndexOf('/') + 1)
                        : outerClass;

                String source = results.entrySet().stream()
                        .filter(e -> e.getKey() != null && e.getKey().contains(outerSimple))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElseGet(() -> results.isEmpty() ? null : results.values().iterator().next());

                if (source == null) {
                    source = "// Decompilation failed for: " + className;
                }

                cache.put(className, source);
                return source;
            } finally {
                deleteDirectory(tempInput);
            }
        }
    }

    public void clearCache() {
        cache.clear();
        locks.clear();
        classIndex = null;
    }

    private Set<String> getClassIndex() {
        if (classIndex != null) return classIndex;
        classIndex = new HashSet<>();
        try (JarFile jar = new JarFile(jarFile)) {
            jar.stream()
                    .map(JarEntry::getName)
                    .filter(n -> n.endsWith(".class"))
                    .map(n -> n.substring(0, n.length() - 6))
                    .forEach(classIndex::add);
        } catch (IOException ignored) {
        }
        return classIndex;
    }

    private void extractClasses(String outerClass, Path targetDir) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class")) continue;

                String internalName = name.substring(0, name.length() - 6);
                boolean isOuter = internalName.equals(outerClass);
                boolean isInner = internalName.startsWith(outerClass + "$");

                if (isOuter || isInner) {
                    Path target = targetDir.resolve(name);
                    Files.createDirectories(target.getParent());
                    try (InputStream is = jar.getInputStream(entry)) {
                        Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    private IResultSaver buildSaver(Map<String, String> results) {
        return new IResultSaver() {
            @Override
            public void saveFolder(String path) {
            }

            @Override
            public void copyFile(String source, String path, String entryName) {
            }

            @Override
            public void saveClassFile(String path, String qualifiedName, String entryName,
                                      String content, int[] mapping) {
                if (content != null && !content.isBlank()) results.put(qualifiedName, content);
            }

            @Override
            public void createArchive(String path, String archiveName, Manifest manifest) {
            }

            @Override
            public void saveDirEntry(String path, String archiveName, String entryName) {
            }

            @Override
            public void copyEntry(String source, String path, String archiveName, String entryName) {
            }

            @Override
            public void saveClassEntry(String path, String archiveName,
                                       String qualifiedName, String entryName, String content) {
                if (content != null && !content.isBlank()) results.put(qualifiedName, content);
            }

            @Override
            public void closeArchive(String path, String archiveName) {
            }
        };
    }

    private void deleteDirectory(Path dir) {
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static class QuietLogger extends IFernflowerLogger {
        @Override
        public void writeMessage(String message, Severity severity) {
        }

        @Override
        public void writeMessage(String message, Severity severity, Throwable t) {
        }
    }
}
