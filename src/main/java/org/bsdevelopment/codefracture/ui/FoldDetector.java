package org.bsdevelopment.codefracture.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/** Scans Vineflower-decompiled Java source and returns foldable regions. */
public final class FoldDetector {
    public static List<FoldRegion> detect(String source) {
        String[] lines = source.split("\n", -1);
        List<FoldRegion> regions = new ArrayList<>();

        int importFirst = -1, importLast = -1;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.startsWith("import ")) {
                if (importFirst < 0) importFirst = i;
                importLast = i;
            } else if (importFirst >= 0 && !t.isEmpty()) {
                break;
            }
        }
        if (importFirst >= 0 && importLast > importFirst) {
            regions.add(new FoldRegion(FoldRegion.Type.IMPORTS, importFirst, importLast + 1));
        }

        detectBlocks(lines, regions);
        regions.sort(Comparator.comparingInt(r -> r.startLine));
        return regions;
    }

    private static void detectBlocks(String[] lines, List<FoldRegion> regions) {
        // Stack tracks opening lines of foldable blocks
        Deque<Integer> stack = new ArrayDeque<>();
        boolean[] inMultilineComment = { false };

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;

            int net = countNetBraces(raw, inMultilineComment);

            if (net > 0 && trimmed.endsWith("{")) {
                stack.push(i);
            } else if (net < 0) {
                if (!stack.isEmpty()) {
                    int openLine = stack.pop();
                    if (i - openLine > 1 && isBlockHeader(lines[openLine])) {
                        regions.add(new FoldRegion(FoldRegion.Type.BLOCK, openLine, i + 1));
                    }
                }
            }
        }
    }

    /** Counts net open vs close braces on one line, respecting strings and comments. */
    private static int countNetBraces(String line, boolean[] inML) {
        int count = 0;
        boolean inString = false, inChar = false;
        boolean ml = inML[0];

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            char next = (i + 1 < line.length()) ? line.charAt(i + 1) : 0;

            if (ml) {
                if (c == '*' && next == '/') {
                    ml = false;
                    i++;
                }
                continue;
            }
            if (c == '/' && next == '*') {
                ml = true;
                i++;
                continue;
            }
            if (c == '/' && next == '/') break;
            if (c == '\\' && (inString || inChar)) {
                i++;
                continue;
            }
            if (c == '"' && !inChar) {
                inString = !inString;
                continue;
            }
            if (c == '\'' && !inString) {
                inChar = !inChar;
                continue;
            }
            if (!inString && !inChar) {
                if (c == '{') count++;
                else if (c == '}') count--;
            }
        }

        inML[0] = ml;
        return count;
    }

    /** Returns true if the line is a method, constructor, or type declaration header. */
    private static boolean isBlockHeader(String line) {
        String t = line.trim();
        if (!t.endsWith("{")) return false;
        String lower = t.toLowerCase();
        // Exclude control-flow openers
        for (String kw : new String[]{
                "if ", "if(", "for ", "for(", "while ", "while(",
                "do ", "try", "switch ", "switch(", "else",
                "catch ", "catch(", "finally", "synchronized ", "synchronized(" }) {
            if (lower.startsWith(kw)) return false;
        }
        // Must look like a declaration
        return t.contains("class ") || t.contains("interface ") ||
                t.contains("enum ") || t.contains("record ") ||
                t.contains("public ") || t.contains("private ") ||
                t.contains("protected ") || t.contains("static {") ||
                (t.contains("(") && t.contains(")") &&
                        !lower.startsWith("return") && !lower.startsWith("throw") &&
                        !lower.startsWith("new "));
    }
}
