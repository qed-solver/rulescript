package org.qed;

import java.nio.file.Path;

/**
 * Resolves repo-relative paths for codegen and tests. Maven sets {@code -Drulescript.basedir};
 * otherwise {@code user.dir} is used (run from repository root).
 */
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
