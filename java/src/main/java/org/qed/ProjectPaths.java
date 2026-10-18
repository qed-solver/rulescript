package org.qed;

import java.nio.file.Path;

public final class ProjectPaths {
    private ProjectPaths() {}

    public static Path baseDir() {
        String override = System.getProperty("rulescript.basedir");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.dir"));
    }
}
