package org.bsdevelopment.codefracture.model;

import java.io.File;

public class JarNode {

    private final String name;
    private final String fullPath;
    private final Type type;
    private final File jarFile;
    /** Null on normal (non-comparison) nodes. */
    private final DiffStatus diffStatus;
    /** For CHANGED nodes: the "B" (modified) JAR. Null for ADDED/REMOVED. */
    private final File jarFileB;
    /** The fullPath of the comparison tree root this node belongs to. */
    private final String comparisonRootPath;
    public JarNode(String name, String fullPath, Type type, File jarFile) {
        this(name, fullPath, type, jarFile, null, null, null);
    }
    public JarNode(String name, String fullPath, Type type) {
        this(name, fullPath, type, null, null, null, null);
    }

    public JarNode(String name, String fullPath, Type type, File jarFile,
                   DiffStatus diffStatus, File jarFileB, String comparisonRootPath) {
        this.name = name;
        this.fullPath = fullPath;
        this.type = type;
        this.jarFile = jarFile;
        this.diffStatus = diffStatus;
        this.jarFileB = jarFileB;
        this.comparisonRootPath = comparisonRootPath;
    }

    public String getName() {
        return name;
    }

    public String getFullPath() {
        return fullPath;
    }

    public Type getType() {
        return type;
    }

    public File getJarFile() {
        return jarFile;
    }

    public DiffStatus getDiffStatus() {
        return diffStatus;
    }

    public File getJarFileB() {
        return jarFileB;
    }

    public String getComparisonRootPath() {
        return comparisonRootPath;
    }

    @Override
    public String toString() {
        return name;
    }

    public enum Type {
        ROOT, PACKAGE, CLASS, INTERFACE, ENUM, ANNOTATION, RESOURCE
    }

    public enum DiffStatus {ADDED, REMOVED, CHANGED}
}
