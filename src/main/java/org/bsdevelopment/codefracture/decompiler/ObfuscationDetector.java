package org.bsdevelopment.codefracture.decompiler;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class ObfuscationDetector {
    // Fraction of class names that are "short" (≤2 chars) required to flag as obfuscated
    private static final double SHORT_NAME_THRESHOLD = 0.40;
    // Minimum number of classes before we bother checking
    private static final int MIN_CLASS_COUNT = 10;

    /**
     * Returns {@code true} if the JAR appears to be obfuscated based on the ratio
     * of suspiciously short class simple-names (≤ 2 characters).
     */
    public static boolean isObfuscated(File jarFile) {
        int total = 0;
        int short_ = 0;
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class") || name.contains("$")) continue;
                total++;
                String simple = name.contains("/")
                        ? name.substring(name.lastIndexOf('/') + 1, name.length() - 6)
                        : name.substring(0, name.length() - 6);
                if (simple.length() <= 2) short_++;
            }
        } catch (IOException ignored) {
        }

        if (total < MIN_CLASS_COUNT) return false;
        return (double) short_ / total >= SHORT_NAME_THRESHOLD;
    }
}
