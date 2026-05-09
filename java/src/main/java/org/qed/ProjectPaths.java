package org.qed;

import java.nio.file.Path;

/**
 * Repo-relative paths for codegen and tests. Defaults to {@code user.dir} (run from repository root).
 * Optional override: {@code -Drulescript.basedir=/path}.
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
