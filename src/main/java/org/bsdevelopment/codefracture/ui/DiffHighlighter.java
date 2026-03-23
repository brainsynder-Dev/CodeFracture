package org.bsdevelopment.codefracture.ui;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;

public class DiffHighlighter {

    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        if (text.isEmpty()) return null;
        StyleSpansBuilder<Collection<String>> sb = new StyleSpansBuilder<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String cls = classForLine(line);
            int len = line.length();
            if (cls != null) {
                sb.add(Collections.singleton(cls), len);
            } else {
                sb.add(Collections.emptyList(), len);
            }
            if (i < lines.length - 1) {
                sb.add(Collections.emptyList(), 1); // newline character
            }
        }
        return sb.create();
    }

    private static String classForLine(String line) {
        if (line.startsWith("---") || line.startsWith("+++")) return "diff-header";
        if (line.startsWith("@@")) return "diff-hunk";
        if (line.startsWith("+")) return "diff-added";
        if (line.startsWith("-")) return "diff-removed";
        return null;
    }
}
