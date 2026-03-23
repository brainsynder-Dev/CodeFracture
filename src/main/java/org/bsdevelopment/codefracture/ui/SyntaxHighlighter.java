package org.bsdevelopment.codefracture.ui;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxHighlighter {

    // ── Java ─────────────────────────────────────────────────────────────────

    private static final String[] KEYWORDS = {
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "record", "sealed",
            "permits", "yield", "var"
    };

    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final Pattern JAVA_PATTERN = Pattern.compile(
            "(?<TRIPLESTRING>" + TRIPLE_STRING_PATTERN + ")"
                    + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
                    + "|(?<KEYWORD>" + KEYWORD_PATTERN + ")"
                    + "|(?<ANNOTATION>" + ANNOTATION_PATTERN + ")"
                    + "|(?<STRING>" + STRING_PATTERN + ")"
                    + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
                    + "|(?<CLASSREF>" + CLASS_REF_PATTERN + ")"
    );
    private static final String ANNOTATION_PATTERN = "@[\\w]+";
    private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'";
    private static final String TRIPLE_STRING_PATTERN = "\"\"\"[\\s\\S]*?\"\"\"";
    private static final String COMMENT_PATTERN = "//[^\n]*|/\\*(.|\\R)*?\\*/";
    private static final String NUMBER_PATTERN = "\\b(0[xX][0-9a-fA-F]+[lL]?|\\d+\\.?\\d*([eE][+-]?\\d+)?[fFdDlL]?)\\b";
    private static final String CLASS_REF_PATTERN = "\\b[A-Z][a-zA-Z0-9_]*\\b";

    // ── Properties ───────────────────────────────────────────────────────────
    private static final Pattern PROPERTIES_PATTERN = Pattern.compile(
            "(?<COMMENT>^[ \\t]*[#!][^\\n]*)"
                    + "|(?<KEY>^[ \\t]*[^=:#!\\n][^=:\\n]*(?=[=:]))"
                    + "|(?<VALUE>(?<=[=:])[^\\n]*)",
            Pattern.MULTILINE
    );

    // ── YAML ─────────────────────────────────────────────────────────────────

    private static final Pattern YAML_PATTERN = Pattern.compile(
            "(?<COMMENT>#[^\\n]*)"
                    + "|(?<KEY>^[ \\t]*[\\w.\\-/]+(?=\\s*:))"
                    + "|(?<STRING>\"[^\"]*\"|'[^']*')"
                    + "|(?<NUMBER>\\b-?\\d+\\.?\\d*(?:[eE][+-]?\\d+)?\\b)"
                    + "|(?<BOOLEAN>\\b(?:true|false|yes|no|null|True|False|Yes|No|Null)\\b)",
            Pattern.MULTILINE
    );

    // ── JSON ─────────────────────────────────────────────────────────────────

    private static final Pattern JSON_PATTERN = Pattern.compile(
            "(?<KEY>\"(?:[^\"\\\\]|\\\\.)*\"(?=\\s*:))"
                    + "|(?<STRING>\"(?:[^\"\\\\]|\\\\.)*\")"
                    + "|(?<NUMBER>-?\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b)"
                    + "|(?<BOOLEAN>\\b(?:true|false|null)\\b)"
    );

    // ── XML / HTML ────────────────────────────────────────────────────────────

    private static final Pattern XML_PATTERN = Pattern.compile(
            "(?<COMMENT><!--[\\s\\S]*?-->)"
                    + "|(?<CDATA><!\\[CDATA\\[[\\s\\S]*?\\]\\]>)"
                    + "|(?<TAG><[/?!]?[\\w:.-]*)"
                    + "|(?<ATTRNAME>[\\w:.-]+(?=\\s*=))"
                    + "|(?<ATTRVALUE>\"[^\"]*\"|'[^']*')"
    );

    // ── Public API ───────────────────────────────────────────────────────────

    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        return applyPattern(JAVA_PATTERN, text, SyntaxHighlighter::javaStyleClass);
    }

    /**
     * Returns syntax-highlighted spans for the given resource file extension,
     * or {@code null} if no highlighting is defined for that extension.
     */
    public static StyleSpans<Collection<String>> computeHighlightingForExtension(
            String text, String extension) {
        return switch (extension) {
            case "java" -> computeHighlighting(text);
            case "xml", "html", "htm" -> applyPattern(XML_PATTERN, text, SyntaxHighlighter::xmlStyleClass);
            case "json" -> applyPattern(JSON_PATTERN, text, SyntaxHighlighter::jsonStyleClass);
            case "yaml", "yml" -> applyPattern(YAML_PATTERN, text, SyntaxHighlighter::yamlStyleClass);
            case "properties" -> applyPattern(PROPERTIES_PATTERN, text, SyntaxHighlighter::propsStyleClass);
            default -> null;
        };
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static StyleSpans<Collection<String>> applyPattern(
            Pattern pattern, String text, StyleMapper mapper) {
        Matcher matcher = pattern.matcher(text);
        StyleSpansBuilder<Collection<String>> sb = new StyleSpansBuilder<>();
        int last = 0;
        while (matcher.find()) {
            String cls = mapper.map(matcher);
            sb.add(Collections.emptyList(), matcher.start() - last);
            sb.add(cls != null ? Collections.singleton(cls) : Collections.emptyList(),
                    matcher.end() - matcher.start());
            last = matcher.end();
        }
        sb.add(Collections.emptyList(), text.length() - last);
        return sb.create();
    }

    private static String javaStyleClass(Matcher m) {
        if (m.group("TRIPLESTRING") != null) return "string";
        if (m.group("COMMENT") != null) return "comment";
        if (m.group("KEYWORD") != null) return "keyword";
        if (m.group("ANNOTATION") != null) return "annotation";
        if (m.group("STRING") != null) return "string";
        if (m.group("NUMBER") != null) return "number";
        if (m.group("CLASSREF") != null) return "classref";
        return null;
    }

    private static String xmlStyleClass(Matcher m) {
        if (m.group("COMMENT") != null) return "comment";
        if (m.group("CDATA") != null) return "string";
        if (m.group("TAG") != null) return "keyword";
        if (m.group("ATTRNAME") != null) return "annotation";
        if (m.group("ATTRVALUE") != null) return "string";
        return null;
    }

    private static String jsonStyleClass(Matcher m) {
        if (m.group("KEY") != null) return "keyword";
        if (m.group("STRING") != null) return "string";
        if (m.group("NUMBER") != null) return "number";
        if (m.group("BOOLEAN") != null) return "annotation";
        return null;
    }

    private static String yamlStyleClass(Matcher m) {
        if (m.group("COMMENT") != null) return "comment";
        if (m.group("KEY") != null) return "keyword";
        if (m.group("STRING") != null) return "string";
        if (m.group("NUMBER") != null) return "number";
        if (m.group("BOOLEAN") != null) return "annotation";
        return null;
    }

    private static String propsStyleClass(Matcher m) {
        if (m.group("COMMENT") != null) return "comment";
        if (m.group("KEY") != null) return "keyword";
        if (m.group("VALUE") != null) return "string";
        return null;
    }

    @FunctionalInterface
    private interface StyleMapper {
        String map(Matcher m);
    }
}
