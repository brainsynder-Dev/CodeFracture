package org.bsdevelopment.codefracture.ui;

public class FoldRegion {
    public final Type type;
    public final int startLine;
    /** Exclusive end — lines [startLine, endLine) belong to this region. */
    public final int endLine;
    public boolean folded = false;
    public FoldRegion(Type type, int startLine, int endLine) {
        this.type = type;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    public String placeholder(String[] lines) {
        String header = (startLine < lines.length) ? lines[startLine].stripTrailing() : "";
        int hiddenCount = endLine - startLine - 1;
        return switch (type) {
            case IMPORTS -> header + "  // … +" + hiddenCount + " more";
            case BLOCK -> {
                // Strip trailing '{' so the collapsed form reads: method() { … }
                String h = header.endsWith("{")
                        ? header.substring(0, header.lastIndexOf('{')).stripTrailing()
                        : header;
                yield h + " { … }  // " + hiddenCount + " lines";
            }
        };
    }

    public enum Type {IMPORTS, BLOCK}
}
