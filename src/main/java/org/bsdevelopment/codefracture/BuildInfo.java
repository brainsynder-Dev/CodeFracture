package org.bsdevelopment.codefracture;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class BuildInfo {
    public static final String VERSION;

    static {
        String v = "unknown";
        try (InputStream is = BuildInfo.class.getResourceAsStream("build.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                v = props.getProperty("version", "unknown");
            }
        } catch (IOException ignored) {
        }
        VERSION = v;
    }

    private BuildInfo() {}
}
